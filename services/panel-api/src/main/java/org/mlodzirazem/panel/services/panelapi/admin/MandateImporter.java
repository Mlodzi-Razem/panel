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
import org.mlodzirazem.panel.services.panelapi.bodies.AssignMembership;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignmentCheck;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipChanged;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipService;
import org.mlodzirazem.panel.services.panelapi.bodies.FunctionConflict;
import org.mlodzirazem.panel.services.panelapi.bodies.FunctionViolation;
import org.mlodzirazem.panel.services.panelapi.bodies.FunctionWarning;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberTypeRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Imports the owner's list of current functions (D6): member number, body code, function code,
 * valid from, decision reference (uchwała — mandatory). Every statutory rule is evaluated by
 * {@link BodyMembershipService#check(AssignMembership)} — this importer never re-implements them,
 * so art. 18 incompatibilities, eligibility and the A4 rules to come are enforced by the same code
 * the interactive assignment uses. Nothing is ever auto-closed: a conflicting mandate is a row
 * error, never a silent update of history (statute §0.6/AGENTS).
 *
 * <p>Idempotent: a run is a match on member + body + function + valid from + decision reference —
 * a rerun of the same file changes nothing (Unchanged). The one pass apply cannot predict is a
 * conflict between two rows of the same file: those are caught by re-checking each row right
 * before its write (apply is therefore always at least as strict as its dry run). By default an
 * apply with errors changes nothing (all-or-nothing in one transaction);
 * {@code skip-invalid-rows} writes the clean rows and skips the rest.
 */
@RequiredArgsConstructor
class MandateImporter {
    static final String AUDIT_OPERATION = "bodies :: mandate-import";
    private static final String FILE_LABEL = "file";
    private static final String CONFLICT_WARNING = "FUNCTION_CONFLICT";

    private final BodyMembershipService bodyMemberships;
    private final TMemberRepository members;
    private final TBodyRepository bodies;
    private final TBodyTemplateMemberTypeRepository memberTypes;
    private final TBodyMembershipRepository memberships;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;
    private final PlatformTransactionManager transactionManager;

    MandateImportReport run(Path file, boolean apply, boolean skipInvalidRows) {
        // unreadable file is fatal before any transaction: nothing to roll back
        MandateCsv table = MandateCsv.read(file);
        String fileName = String.valueOf(file.getFileName());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(!apply);

        try {
            return Objects.requireNonNull(transaction.execute(status -> process(table, apply, skipInvalidRows, fileName)));
        } catch (AbortedImport abort) {
            // thrown from inside the transaction: every membership written so far rolled back with it
            return abort.report();
        }
    }

    private MandateImportReport process(MandateCsv table, boolean apply, boolean skipInvalidRows, String fileName) {
        List<MandateImportProblem> problems = new ArrayList<>();
        table.unknownHeaders().forEach(h -> problems.add(fileProblem(h, "UNKNOWN_COLUMN", false)));
        table.duplicateHeaders().forEach(h -> problems.add(fileProblem(h, "DUPLICATE_COLUMN", false)));

        Seen seen = new Seen();
        List<Plan> plans = new ArrayList<>();

        for (MandateCsv.Row row : table.rows()) {
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
        if (apply && rowErrors > 0 && !skipInvalidRows) {
            return new MandateImportReport(fileName, true, true, plans.size(), candidates, matches, rowErrors, problems);
        }

        if (!apply) {
            return new MandateImportReport(fileName, false, false, plans.size(), candidates, matches, rowErrors,
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

            List<FunctionViolation> late = bodyMemberships.check(plan.request()).violations();

            if (!late.isEmpty()) {
                late.forEach(v -> problems.add(
                    new MandateImportProblem(plan.label, columnOf(v.code()), v.code().name(), true)));

                if (skipInvalidRows) {
                    skipped++;
                    continue;
                }

                // already written rows must not survive: undo the transaction and report
                throw new AbortedImport(new MandateImportReport(fileName, true, true, plans.size(), candidates - 1,
                    matches, rowErrors + 1, problems));
            }

            write(plan);
            created++;
        }

        auditSummary(plans.size(), created, unchanged, skipped);

        return new MandateImportReport(fileName, true, false, plans.size(), created, unchanged, skipped, problems);
    }

    private Plan plan(MandateCsv table, MandateCsv.Row row, Seen seen) {
        String number = table.get(row, MandateColumn.MEMBER);
        Plan plan = new Plan(number != null ? number : "row " + row.number());

        plan.member = resolveMember(plan, number);

        resolveBody(plan, table.get(row, MandateColumn.BODY));

        String function = table.get(row, MandateColumn.FUNCTION);

        if (function == null) {
            plan.add(MandateColumn.FUNCTION, "REQUIRED", true);
        } else if (plan.body != null) {
            resolveFunction(plan, function);
        }

        String fromText = table.get(row, MandateColumn.FROM);

        if (fromText == null) {
            plan.add(MandateColumn.FROM, "REQUIRED", true);
        } else {
            LocalDate from = MandateText.parseDate(fromText).orElse(null);

            if (from == null) {
                plan.add(MandateColumn.FROM, "UNPARSEABLE_DATE", true);
            } else {
                plan.validFrom = from;
            }
        }

        String resolution = table.get(row, MandateColumn.RESOLUTION);

        if (resolution == null) {
            plan.add(MandateColumn.RESOLUTION, "REQUIRED", true);
        } else {
            plan.decisionReference = resolution;
        }

        evaluate(plan, seen);

        return plan;
    }

    private void resolveBody(Plan plan, @Nullable String bodyCode) {
        if (bodyCode == null) {
            plan.add(MandateColumn.BODY, "REQUIRED", true);
            return;
        }

        plan.body = bodies.findByCode(bodyCode).orElse(null);

        if (plan.body == null) {
            plan.add(MandateColumn.BODY, "UNKNOWN_BODY", true);
        }
    }

    private void resolveFunction(Plan plan, String function) {
        TBody body = plan.body;

        if (body.getBodyTemplate() == null) {
            plan.add(MandateColumn.FUNCTION, "UNKNOWN_FUNCTION", true);
            return;
        }

        plan.memberType = memberTypes.findByBodyTemplateIdAndCode(body.getBodyTemplate().getId(), function)
                                     .orElse(null);

        if (plan.memberType == null) {
            plan.add(MandateColumn.FUNCTION, "UNKNOWN_FUNCTION", true);
        }
    }

    private void evaluate(Plan plan, Seen seen) {
        if (plan.member == null || plan.body == null || plan.memberType == null || plan.validFrom == null
            || plan.decisionReference == null || plan.hasErrors()) {
            return;
        }

        String key = plan.member.getId() + "|" + plan.body.getId() + "|" + plan.memberType.getId() + "|"
            + plan.validFrom;

        if (!seen.mandates.add(key)) {
            plan.add(MandateColumn.RESOLUTION, "DUPLICATE_IN_FILE", true);
            return;
        }

        // idempotency: the same mandate (member + body + function + from) already in the register
        plan.existing = memberships.findAllByMemberIdAndBodyId(plan.member.getId(), plan.body.getId())
                                   .stream()
                                   .filter(m -> m.getMemberType().getId() == plan.memberType.getId())
                                   .filter(m -> m.getValidFrom().equals(plan.validFrom))
                                   .findFirst()
                                   .orElse(null);

        if (plan.existing != null) {
            // history is never rewritten: the stored uchwała stays as it is; a mismatch is a row error
            String stored = plan.existing.getDecisionReference();

            if (!Objects.equals(stored == null ? null : stored.strip(), plan.decisionReference.strip())) {
                plan.add(MandateColumn.RESOLUTION, "DECISION_REFERENCE_DIFFERS", true);
            }

            return;
        }

        AssignmentCheck check = bodyMemberships.check(plan.request());

        check.violations().forEach(v -> plan.add(columnOf(v.code()), v.code().name(), true));
        check.warnings().forEach(w -> plan.add(MandateColumn.FUNCTION, w.code().name(), false));
        check.conflicts().forEach(c -> plan.add(MandateColumn.FUNCTION, CONFLICT_WARNING, false));
    }

    private void write(Plan plan) {
        TBodyMembership membership = new TBodyMembership();
        membership.setMember(plan.member);
        membership.setBody(plan.body);
        membership.setMemberType(plan.memberType);
        membership.setValidFrom(plan.validFrom);
        membership.setValidTo(null);
        membership.setDecisionReference(plan.decisionReference.strip());
        memberships.saveAndFlush(membership);

        events.publishEvent(new BodyMembershipChanged(
            BodyMembershipChanged.Change.CREATED,
            membership.getId(),
            plan.member.getId(),
            plan.body.getId(),
            plan.memberType.getId(),
            plan.validFrom,
            null
        ));

        auditLog.record(AUDIT_OPERATION, AuditPayload.of("membershipId", membership.getId())
                                                     .withId("memberId", plan.member.getId())
                                                     .withId("bodyId", plan.body.getId())
                                                     .withId("memberTypeId", plan.memberType.getId())
                                                     .withFields(List.of("validFrom", "decisionReference")));
    }

    private void auditSummary(int rows, int created, int unchanged, int skipped) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("rows", (long) rows);
        counts.put("created", (long) created);
        counts.put("unchanged", (long) unchanged);
        counts.put("skipped", (long) skipped);
        auditLog.record(AUDIT_OPERATION, new AuditPayload(counts, List.of(), null));
    }

    private @Nullable TMember resolveMember(Plan plan, @Nullable String number) {
        if (number == null) {
            plan.add(MandateColumn.MEMBER, "REQUIRED", true);
            return null;
        }

        String key = MandateText.numberKey(number);
        List<TMember> found = key != null ? members.findByMemberNumber(key) : List.of();

        if (found.isEmpty() && key == null) {
            // not numeric: the register's member_id (e.g. an e-mail shaped id) is matched verbatim
            TMember byMemberId = members.findByMemberId(number).orElse(null);
            found = byMemberId == null ? List.of() : List.of(byMemberId);
        }

        if (found.size() > 1) {
            plan.add(MandateColumn.MEMBER, "MATCHES_MULTIPLE_MEMBERS", true);
            return null;
        }

        if (found.isEmpty()) {
            plan.add(MandateColumn.MEMBER, "UNKNOWN_MEMBER", true);
            return null;
        }

        return found.getFirst();
    }

    private static String columnOf(FunctionViolation.Code code) {
        return switch (code) {
            case MEMBER_NOT_ELIGIBLE, FUNCTION_REQUIRES_ORDINARY_MEMBERSHIP, FUNCTION_REQUIRES_ACTIVE_MEMBER,
                 MEMBER_SUSPENDED_IN_PERIOD, MEMBERSHIP_ENDED_BEFORE_MANDATE -> MandateColumn.MEMBER.label();
            default -> MandateColumn.FUNCTION.label();
        };
    }

    private static MandateImportProblem fileProblem(String header, String code, boolean error) {
        return new MandateImportProblem(FILE_LABEL, header, code, error);
    }

    private static final class Seen {
        final Set<String> mandates = new HashSet<>();
    }

    private static final class Plan {
        final String label;
        final List<MandateImportProblem> problems = new ArrayList<>();
        @Nullable TMember member;
        @Nullable TBody body;
        @Nullable TBodyTemplateMemberType memberType;
        @Nullable LocalDate validFrom;
        @Nullable String decisionReference;
        @Nullable TBodyMembership existing;

        Plan(String label) {
            this.label = label;
        }

        void add(MandateColumn column, String code, boolean error) {
            add(column.label(), code, error);
        }

        void add(String column, String code, boolean error) {
            problems.add(new MandateImportProblem(label, column, code, error));
        }

        boolean hasErrors() {
            return problems.stream().anyMatch(MandateImportProblem::error);
        }

        AssignMembership request() {
            return new AssignMembership(member.getId(), body.getId(), memberType.getId(), validFrom, null);
        }
    }

    /** Carries the report out of the transaction that is being rolled back with it. */
    private static final class AbortedImport extends RuntimeException {
        private final transient MandateImportReport importReport;

        AbortedImport(MandateImportReport report) {
            super("aborted", null, false, false);
            this.importReport = report;
        }

        MandateImportReport report() {
            return importReport;
        }
    }
}
