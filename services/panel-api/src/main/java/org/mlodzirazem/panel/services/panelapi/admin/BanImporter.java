/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mlodzirazem.panel.services.panelapi.admin;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBan;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBanRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.AdmissionBanCheck;
import org.mlodzirazem.panel.services.panelapi.recruitment.AdmissionIdentifiers;
import org.mlodzirazem.panel.services.panelapi.recruitment.AdmissionBanRule;
import org.mlodzirazem.panel.services.panelapi.recruitment.AdmissionBanService;
import org.mlodzirazem.panel.services.panelapi.recruitment.NewAdmissionBan;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Imports the ZK uchwały o zakazie przyjmowania (statut jednolity 2026, art. 9 ust. 5) — the G1
 * audit §7 item 5 gap: the Panel enforced the ban but offered no path to record one. Every statutory
 * rule (the PESEL checksum of the e-declaration form, the mandatory reason and signature of the
 * uchwała, the 365-day bounds, the overlap with a recorded ban) is evaluated by
 * {@link AdmissionBanService} — this importer never re-implements them, so the CLI and the back
 * office surface to come enforce the same rules.
 *
 * <p>Nothing is ever rewritten: an identical re-run of a row changes nothing (Unchanged — the
 * idempotency key is the recorded identifier and the whole period), a differing signature or
 * reason of the same period is a row error, an overlapping ban is a row error — history is
 * untouchable (§0.6). An apply with row errors writes nothing (all-or-nothing in one transaction).
 *
 * <p>The report names line numbers and problem codes only — zero values of the file (§0.5:
 * the PESEL and the reason never reach logs, reports or the audit trail).
 */
@RequiredArgsConstructor
class BanImporter {
    static final String AUDIT_OPERATION = "recruitment :: ban-import";
    private static final String FILE_LABEL = "file";

    private final AdmissionBanService admissionBans;
    private final TAdmissionBanRepository bans;
    private final AuditLog auditLog;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    BanImportReport run(Path file, boolean apply) {
        // unreadable file is fatal before any transaction: nothing to roll back
        BanCsv table = BanCsv.read(file);
        String fileName = String.valueOf(file.getFileName());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(!apply);

        try {
            return Objects.requireNonNull(transaction.execute(status -> process(table, apply, fileName)));
        } catch (AbortedImport abort) {
            // thrown from inside the transaction: every ban written so far rolled back with it
            return abort.report();
        }
    }

    private BanImportReport process(BanCsv table, boolean apply, String fileName) {
        List<BanImportProblem> problems = new ArrayList<>();
        table.unknownHeaders().forEach(h -> problems.add(fileProblem(h, "UNKNOWN_COLUMN", false)));
        table.duplicateHeaders().forEach(h -> problems.add(fileProblem(h, "DUPLICATE_COLUMN", false)));

        Seen seen = new Seen();
        List<Plan> plans = new ArrayList<>();

        for (BanCsv.Row row : table.rows()) {
            plans.add(plan(table, row, seen));
        }

        for (Plan plan : plans) {
            problems.addAll(plan.problems);
        }

        int candidates = 0;
        int matches = 0;
        int rowErrors = 0;

        for (Plan plan : plans) {
            if (plan.hasErrors()) {
                rowErrors++;
            } else if (plan.existing != null) {
                matches++;
            } else {
                candidates++;
            }
        }

        // all-or-nothing: row errors found during planning abort the apply before anything is written
        if (apply && rowErrors > 0) {
            return new BanImportReport(fileName, true, true, plans.size(), candidates, matches, rowErrors,
                problems);
        }

        if (!apply) {
            return new BanImportReport(fileName, false, false, plans.size(), candidates, matches, rowErrors,
                problems);
        }

        int created = 0;
        int unchanged = 0;
        int skipped = 0;

        for (Plan plan : plans) {
            if (plan.hasErrors()) {
                skipped++;
                continue;
            }

            if (plan.existing != null) {
                unchanged++;
                continue;
            }

            AdmissionBanCheck late = admissionBans.check(plan.request());

            if (!late.allowed()) {
                late.violations().forEach(
                    rule -> problems.add(new BanImportProblem(plan.label, columnOf(rule), rule.name(), true)));

                // already written rows must not survive: undo the transaction and report
                throw new AbortedImport(new BanImportReport(fileName, true, true, plans.size(), candidates - 1,
                    matches, rowErrors + 1, problems));
            }

            admissionBans.create(plan.request(), null);
            created++;
        }

        auditSummary(plans.size(), created, unchanged, skipped);

        return new BanImportReport(fileName, true, false, plans.size(), created, unchanged, skipped, problems);
    }

    private Plan plan(BanCsv table, BanCsv.Row row, Seen seen) {
        // the line number is the only identifier a problem carries — the values stay out (§0.5)
        Plan plan = new Plan("row " + row.number());

        String pesel = table.get(row, BanColumn.PESEL);

        if (pesel == null) {
            plan.add(BanColumn.PESEL, "REQUIRED", true);
        }

        LocalDate effectiveFrom = null;
        String fromText = table.get(row, BanColumn.FROM);

        if (fromText != null) {
            effectiveFrom = BanText.parseDate(fromText).orElse(null);

            if (effectiveFrom == null) {
                plan.add(BanColumn.FROM, "UNPARSEABLE_DATE", true);
            }
        }

        LocalDate effectiveUntil = null;
        String untilText = table.get(row, BanColumn.UNTIL);

        if (untilText == null) {
            plan.add(BanColumn.UNTIL, "REQUIRED", true);
        } else {
            effectiveUntil = BanText.parseDate(untilText).orElse(null);

            if (effectiveUntil == null) {
                plan.add(BanColumn.UNTIL, "UNPARSEABLE_DATE", true);
            }
        }

        String resolution = table.get(row, BanColumn.RESOLUTION);

        if (resolution == null) {
            plan.add(BanColumn.RESOLUTION, "REQUIRED", true);
        }

        String reason = table.get(row, BanColumn.REASON);

        if (reason == null) {
            plan.add(BanColumn.REASON, "REQUIRED", true);
        }

        plan.pesel = pesel;
        plan.effectiveFrom = effectiveFrom;
        plan.effectiveUntil = effectiveUntil;
        plan.resolution = resolution;
        plan.reason = reason;

        evaluate(plan, pesel, effectiveFrom, effectiveUntil, resolution, reason, seen);

        return plan;
    }

    private void evaluate(
        Plan plan,
        @Nullable String pesel,
        @Nullable LocalDate effectiveFrom,
        @Nullable LocalDate effectiveUntil,
        @Nullable String resolution,
        @Nullable String reason,
        Seen seen
    ) {
        if (plan.hasErrors() || pesel == null || effectiveUntil == null || resolution == null || reason == null) {
            return;
        }

        // the same stripping the e-declaration rules apply; the service normalises canonically
        String identifier = AdmissionIdentifiers.canonicalDigits(pesel);

        if (identifier.length() != 11 || !AdmissionIdentifiers.isAsciiDigits(identifier)) {
            // malformed values never reach the register lookups; the statutory check classifies them
            AdmissionBanCheck malformed = admissionBans.check(plan.request());

            malformed.violations().forEach(rule -> plan.add(columnOf(rule), rule.name(), true));
            return;
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate from = effectiveFrom == null ? today : effectiveFrom;

        if (!seen.bans.add(identifier + "|" + from + "|" + effectiveUntil)) {
            plan.add(BanColumn.PESEL, "DUPLICATE_IN_FILE", true);
            return;
        }

        // idempotency: the same uchwała (identifier + the whole period) already recorded
        TAdmissionBan existing = bans.findAllByFormalIdentifier(identifier)
            .stream()
            .filter(ban -> ban.getEffectiveFrom().isEqual(from) && ban.getEffectiveUntil().isEqual(effectiveUntil))
            .findFirst()
            .orElse(null);

        if (existing != null) {
            // history is never rewritten: the stored uchwała stays as it is; a mismatch is a row error
            if (!Objects.equals(stripped(existing.getDecisionSignature()), resolution.strip())) {
                plan.add(BanColumn.RESOLUTION, "DECISION_REFERENCE_DIFFERS", true);
            }

            if (!Objects.equals(stripped(existing.getReason()), reason.strip())) {
                plan.add(BanColumn.REASON, "BAN_REASON_DIFFERS", true);
            }

            plan.existing = existing;
            return;
        }

        AdmissionBanCheck check = admissionBans.check(plan.request());

        check.violations().forEach(rule -> plan.add(columnOf(rule), rule.name(), true));
    }

    private void auditSummary(int rows, int created, int unchanged, int skipped) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("rows", (long) rows);
        counts.put("created", (long) created);
        counts.put("unchanged", (long) unchanged);
        counts.put("skipped", (long) skipped);
        auditLog.record(AUDIT_OPERATION, new AuditPayload(counts, List.of(), null));
    }

    private static String columnOf(AdmissionBanRule rule) {
        return switch (rule) {
            case BAN_PERSONAL_ID_REQUIRED, PERSONAL_ID_INVALID, BAN_OVERLAPS_EXISTING -> BanColumn.PESEL.label();
            case BAN_REASON_REQUIRED -> BanColumn.REASON.label();
            case BAN_DECISION_SIGNATURE_REQUIRED -> BanColumn.RESOLUTION.label();
            case BAN_PERIOD_REQUIRED, BAN_PERIOD_INVALID, BAN_PERIOD_OVER_365_DAYS, BAN_EXPIRY_OVER_365_DAYS ->
                BanColumn.UNTIL.label();
        };
    }

    private static BanImportProblem fileProblem(String header, String code, boolean error) {
        return new BanImportProblem(FILE_LABEL, header, code, error);
    }

    private static @Nullable String stripped(@Nullable String value) {
        return value == null ? null : value.strip();
    }

    private static final class Seen {
        final Set<String> bans = new HashSet<>();
    }

    private static final class Plan {
        final String label;
        final List<BanImportProblem> problems = new ArrayList<>();
        @Nullable TAdmissionBan existing;

        Plan(String label) {
            this.label = label;
        }

        void add(BanColumn column, String code, boolean error) {
            add(column.label(), code, error);
        }

        void add(String column, String code, boolean error) {
            problems.add(new BanImportProblem(label, column, code, error));
        }

        boolean hasErrors() {
            return problems.stream().anyMatch(BanImportProblem::error);
        }

        NewAdmissionBan request() {
            return new NewAdmissionBan(pesel, effectiveFrom, effectiveUntil, resolution, reason, null);
        }

        private @Nullable String pesel;
        private @Nullable LocalDate effectiveFrom;
        private @Nullable LocalDate effectiveUntil;
        private @Nullable String resolution;
        private @Nullable String reason;
    }

    /** Carries the report out of the transaction that is being rolled back with it. */
    private static final class AbortedImport extends RuntimeException {
        private final transient BanImportReport importReport;

        AbortedImport(BanImportReport report) {
            super("aborted", null, false, false);
            this.importReport = report;
        }

        BanImportReport report() {
            return importReport;
        }
    }
}
