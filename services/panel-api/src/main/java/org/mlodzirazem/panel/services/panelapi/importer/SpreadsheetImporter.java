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

package org.mlodzirazem.panel.services.panelapi.importer;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyService;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.ElectoralRightsService;
import org.mlodzirazem.panel.services.panelapi.members.MemberDistricts;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.MemberUpdate;
import org.mlodzirazem.panel.services.panelapi.members.NewMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
class SpreadsheetImporter {
    static final String AUDIT_OPERATION = "importer :: apply";
    private static final String FILE_LABEL = "file";
    private static final Set<String> YES = Set.of("tak", "t", "yes", "y", "true", "1", "prawda", "x");
    private static final Set<String> NO = Set.of("nie", "n", "no", "false", "0", "falsz");

    private final TMemberRepository members;
    private final MemberService memberService;
    private final AffiliationService affiliations;
    private final BodyService bodies;
    private final ElectoralRightsService electoralRights;
    private final AuditLog auditLog;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    ImportReport run(Path file, ImportOptions options) {
        CsvTable table = CsvTable.read(file);
        String fileName = String.valueOf(file.getFileName());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(!options.apply());

        return Objects.requireNonNull(transaction.execute(status -> process(table, options, fileName)));
    }

    private ImportReport process(CsvTable table, ImportOptions options, String fileName) {
        DistrictMatcher matcher = new DistrictMatcher(bodies.districts().stream()
            .map(d -> new DistrictMatcher.District(d.getId(), d.getName(), Objects.toString(d.getCode(), "")))
            .toList());

        List<RowProblem> problems = new ArrayList<>();
        table.unknownHeaders().forEach(h -> problems.add(new RowProblem(FILE_LABEL, h, ProblemCode.UNKNOWN_COLUMN)));
        table.duplicateHeaders().forEach(h -> problems.add(new RowProblem(FILE_LABEL, h,
            ProblemCode.DUPLICATE_COLUMN)));

        Seen seen = new Seen();
        List<Plan> plans = new ArrayList<>();

        for (CsvTable.Row row : table.rows()) {
            plans.add(plan(table, row, matcher, seen));
        }

        boolean hasErrors = plans.stream().anyMatch(Plan::hasErrors);
        boolean aborted = options.apply() && hasErrors && !options.skipInvalidRows();
        boolean write = options.apply() && !aborted;

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int skipped = 0;

        for (Plan plan : plans) {
            problems.addAll(plan.problems);

            if (plan.hasErrors()) {
                skipped++;
                continue;
            }

            Outcome outcome = write ? write(plan) : diff(plan);

            switch (outcome.kind) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }

            checkVotingRight(plan, outcome.memberDbId, problems);
        }

        if (write) {
            Map<String, Long> counts = new LinkedHashMap<>();
            counts.put("rows", (long) plans.size());
            counts.put("created", (long) created);
            counts.put("updated", (long) updated);
            counts.put("unchanged", (long) unchanged);
            counts.put("skipped", (long) skipped);
            auditLog.record(AUDIT_OPERATION, new AuditPayload(counts, List.of(), null));
        }

        return new ImportReport(fileName, options.apply(), aborted, plans.size(), created, updated, unchanged,
            skipped, problems);
    }

    private Plan plan(CsvTable table, CsvTable.Row row, DistrictMatcher matcher, Seen seen) {
        String memberId = table.get(row, Column.MEMBER_ID);
        Plan plan = new Plan(memberId != null ? memberId : "row " + row.number());
        Map<MemberField, @Nullable Object> values = plan.values;

        if (memberId == null) {
            plan.add(Column.MEMBER_ID, ProblemCode.REQUIRED);
        } else {
            values.put(MemberField.MEMBER_ID, memberId);

            if (!seen.memberIds.add(memberId)) {
                plan.add(Column.MEMBER_ID, ProblemCode.DUPLICATE_IN_FILE);
            }
        }

        String formalFirst = table.get(row, Column.FORMAL_FIRST);
        String formalLast = table.get(row, Column.FORMAL_LAST);

        if (formalFirst == null) {
            plan.add(Column.FORMAL_FIRST, ProblemCode.REQUIRED);
        }

        if (formalLast == null) {
            plan.add(Column.FORMAL_LAST, ProblemCode.REQUIRED);
        }

        values.put(MemberField.FORMAL_NAME_FIRST, formalFirst);
        values.put(MemberField.FORMAL_NAME_LAST, formalLast);
        values.put(MemberField.PREFERRED_NAME_FIRST, orElse(table.get(row, Column.PREFERRED_FIRST), formalFirst));
        values.put(MemberField.PREFERRED_NAME_LAST, orElse(table.get(row, Column.PREFERRED_LAST), formalLast));

        optional(table, row, Column.PRONOUNS, MemberField.PRONOUNS, values);
        optional(table, row, Column.PROVINCE, MemberField.ADDRESS_PROVINCE, values);
        optional(table, row, Column.CITY, MemberField.ADDRESS_CITY, values);
        optional(table, row, Column.ADDRESS, MemberField.ADDRESS_LINE, values);
        optional(table, row, Column.INSTITUTION, MemberField.INSTITUTION, values);
        optional(table, row, Column.SOCIAL, MemberField.SOCIAL_PROFILE_URL, values);
        optional(table, row, Column.DECLARATION, MemberField.DECLARATION_REFERENCE, values);

        if (table.has(Column.PHONES)) {
            values.put(MemberField.PHONE_NUMBERS, Text.splitList(table.get(row, Column.PHONES)));
        }

        if (table.has(Column.PRIVATE_EMAILS)) {
            values.put(MemberField.PRIVATE_EMAILS, Text.splitList(table.get(row, Column.PRIVATE_EMAILS)));
        }

        String domainEmail = table.get(row, Column.DOMAIN_EMAIL);

        if (table.has(Column.DOMAIN_EMAIL)) {
            values.put(MemberField.DOMAIN_EMAIL, domainEmail);
        }

        if (domainEmail != null && !seen.domainEmails.add(domainEmail.toLowerCase(Locale.ROOT))) {
            plan.add(Column.DOMAIN_EMAIL, ProblemCode.DUPLICATE_IN_FILE);
        }

        LocalDate birthDate = null;
        String birthText = table.get(row, Column.BIRTH_DATE);

        if (birthText == null) {
            plan.add(Column.BIRTH_DATE, ProblemCode.REQUIRED);
        } else {
            birthDate = Text.parseDate(birthText).orElse(null);

            if (birthDate == null) {
                plan.add(Column.BIRTH_DATE, ProblemCode.UNPARSEABLE_DATE);
            } else {
                values.put(MemberField.BIRTH_DATE, birthDate);
                plan.birthDate = birthDate;
            }
        }

        String sinceText = table.get(row, Column.MEMBER_SINCE);

        if (table.has(Column.MEMBER_SINCE)) {
            LocalDate since = sinceText == null ? null : Text.parseDate(sinceText).orElse(null);

            if (sinceText != null && since == null) {
                plan.add(Column.MEMBER_SINCE, ProblemCode.UNPARSEABLE_DATE);
            } else {
                values.put(MemberField.MEMBER_SINCE, since);
            }
        }

        planPesel(table, row, plan, birthDate, seen);
        planDistricts(table, row, plan, matcher);
        planChecks(table, row, plan, birthDate);

        match(plan, memberId, domainEmail, (String) values.get(MemberField.FORMAL_IDENTIFIER));

        return plan;
    }

    private void planPesel(CsvTable table, CsvTable.Row row, Plan plan, @Nullable LocalDate birthDate, Seen seen) {
        if (!table.has(Column.PESEL)) {
            return;
        }

        String pesel = table.get(row, Column.PESEL);

        if (pesel == null) {
            plan.values.put(MemberField.FORMAL_IDENTIFIER, null);
            return;
        }

        if (!Pesel.hasFormat(pesel)) {
            plan.add(Column.PESEL, ProblemCode.FORMAT);
            return;
        }

        if (!Pesel.checksumValid(pesel)) {
            plan.add(Column.PESEL, ProblemCode.CHECKSUM);
            return;
        }

        plan.values.put(MemberField.FORMAL_IDENTIFIER, pesel);

        if (!seen.pesels.add(pesel)) {
            plan.add(Column.PESEL, ProblemCode.DUPLICATE_IN_FILE);
        }

        Optional<LocalDate> encoded = Pesel.birthDate(pesel);

        if (birthDate != null && encoded.isPresent() && !encoded.get().equals(birthDate)) {
            plan.add(Column.PESEL, ProblemCode.BIRTH_DATE_MISMATCH);
        }
    }

    private void planDistricts(CsvTable table, CsvTable.Row row, Plan plan, DistrictMatcher matcher) {
        String primary = table.get(row, Column.PRIMARY_DISTRICT);

        if (primary == null) {
            plan.add(Column.PRIMARY_DISTRICT, ProblemCode.REQUIRED);
        } else {
            plan.primaryId = matcher.match(primary).orElse(null);

            if (plan.primaryId == null) {
                plan.add(Column.PRIMARY_DISTRICT, ProblemCode.UNKNOWN_DISTRICT);
            }
        }

        String voting = table.get(row, Column.VOTING_DISTRICT);

        if (voting != null) {
            plan.votingId = matcher.match(voting).orElse(null);

            if (plan.votingId == null) {
                plan.add(Column.VOTING_DISTRICT, ProblemCode.UNKNOWN_DISTRICT);
            }
        }
    }

    private void planChecks(CsvTable table, CsvTable.Row row, Plan plan, @Nullable LocalDate birthDate) {
        LocalDate today = LocalDate.now(clock);

        if (birthDate != null && today.isAfter(MemberRules.membershipEndDate(birthDate))) {
            plan.add(Column.BIRTH_DATE, ProblemCode.MEMBERSHIP_AGE_LIMIT_EXCEEDED);
        }

        String age = table.get(row, Column.AGE);

        if (age != null && birthDate != null) {
            try {
                if (Integer.parseInt(age) != MemberRules.ageOn(birthDate, today)) {
                    plan.add(Column.AGE, ProblemCode.AGE_MISMATCH);
                }
            } catch (NumberFormatException ex) {
                plan.add(Column.AGE, ProblemCode.AGE_UNPARSEABLE);
            }
        }

        plan.canVoteText = table.get(row, Column.CAN_VOTE);

        if (table.get(row, Column.REMOVAL_VOTE) != null) {
            plan.add(Column.REMOVAL_VOTE, ProblemCode.REMOVAL_VOTE_FOLLOW_UP);
        }
    }

    private void match(Plan plan, @Nullable String memberId, @Nullable String domainEmail, @Nullable String pesel) {
        Map<Long, TMember> found = new LinkedHashMap<>();
        boolean multiple = false;

        try {
            if (memberId != null) {
                members.findByMemberId(memberId).ifPresent(m -> found.put(m.getId(), m));
            }
        } catch (IncorrectResultSizeDataAccessException ex) {
            multiple = true;
        }

        try {
            if (domainEmail != null) {
                members.findByDomainEmailIgnoreCase(domainEmail).ifPresent(m -> found.put(m.getId(), m));
            }
        } catch (IncorrectResultSizeDataAccessException ex) {
            multiple = true;
        }

        try {
            if (pesel != null) {
                members.findByFormalIdentifier(pesel).ifPresent(m -> found.put(m.getId(), m));
            }
        } catch (IncorrectResultSizeDataAccessException ex) {
            multiple = true;
        }

        if (multiple || found.size() > 1) {
            plan.problems.add(new RowProblem(plan.label, "—", ProblemCode.MATCHES_MULTIPLE_MEMBERS));
            return;
        }

        if (found.size() == 1) {
            TMember existing = found.values().iterator().next();

            if (memberId != null && existing.getMemberId() != null && !existing.getMemberId().equals(memberId)) {
                plan.add(Column.MEMBER_ID, ProblemCode.MEMBER_ID_CONFLICT);
                return;
            }

            plan.existing = existing;
        }
    }

    private Outcome diff(Plan plan) {
        TMember existing = plan.existing;

        if (existing == null) {
            return new Outcome(Kind.CREATED, null);
        }

        boolean changed = plan.values.entrySet().stream().anyMatch(e -> differs(e.getKey(), existing, e.getValue()))
            || affiliationsDiffer(existing.getId(), plan);

        return new Outcome(changed ? Kind.UPDATED : Kind.UNCHANGED, existing.getId());
    }

    private static boolean differs(MemberField field, TMember member, @Nullable Object value) {
        Object current = field.get(member);

        return !Objects.equals(current, value) && !(current instanceof List<?> list && list.isEmpty() && value == null);
    }

    private boolean affiliationsDiffer(long id, Plan plan) {
        MemberDistricts districts = affiliations.districtsOf(id);

        if (!Objects.equals(districts.primaryDistrictId(), plan.primaryId)) {
            return true;
        }

        if (plan.votingId == null) {
            return false;
        }

        Long votingNow = votingNow(id);

        if (plan.votingId.equals(plan.primaryId)) {
            return !plan.votingId.equals(votingNow);
        }

        return !districts.supportingDistrictIds().contains(plan.votingId) || !plan.votingId.equals(votingNow);
    }

    private Outcome write(Plan plan) {
        Objects.requireNonNull(plan.primaryId);
        TMember existing = plan.existing;

        if (existing == null) {
            TMember created = memberService.create(new NewMember(plan.values, plan.primaryId, false));
            applyVoting(created.getId(), plan);

            return new Outcome(Kind.CREATED, created.getId());
        }

        long id = existing.getId();
        MemberUpdate update = memberService.update(id, plan.values);
        boolean changed = !update.changedFields().isEmpty();

        if (!Objects.equals(affiliations.districtsOf(id).primaryDistrictId(), plan.primaryId)) {
            affiliations.setPrimaryDistrict(id, plan.primaryId, false);
            changed = true;
        }

        changed |= applyVoting(id, plan);

        return new Outcome(changed ? Kind.UPDATED : Kind.UNCHANGED, id);
    }

    private boolean applyVoting(long id, Plan plan) {
        Long target = plan.votingId;

        if (target == null) {
            return false;
        }

        MemberDistricts districts = affiliations.districtsOf(id);
        Long votingNow = votingNow(id);

        if (target.equals(districts.primaryDistrictId()) || districts.supportingDistrictIds().contains(target)) {
            if (target.equals(votingNow)) {
                return false;
            }

            affiliations.setVotingDistrict(id, target);
            return true;
        }

        affiliations.addSupportingDistrict(id, target, true);
        return true;
    }

    private @Nullable Long votingNow(long id) {
        return affiliations.votingDistrict(id).map(TBody::getId).orElse(null);
    }

    private void checkVotingRight(Plan plan, @Nullable Long memberDbId, List<RowProblem> problems) {
        if (plan.canVoteText == null) {
            return;
        }

        String text = Text.fold(plan.canVoteText);
        Boolean sheet = YES.contains(text) ? Boolean.TRUE : NO.contains(text) ? Boolean.FALSE : null;

        if (sheet == null) {
            problems.add(new RowProblem(plan.label, Column.CAN_VOTE.label(), ProblemCode.VOTING_RIGHT_UNPARSEABLE));
            return;
        }

        boolean computed = memberDbId == null || electoralRights.rightsOf(memberDbId).eligible();

        if (computed != sheet) {
            problems.add(new RowProblem(plan.label, Column.CAN_VOTE.label(), ProblemCode.VOTING_RIGHT_MISMATCH));
        }
    }

    private static void optional(
        CsvTable table,
        CsvTable.Row row,
        Column column,
        MemberField field,
        Map<MemberField, @Nullable Object> values
    ) {
        if (table.has(column)) {
            values.put(field, table.get(row, column));
        }
    }

    private static @Nullable String orElse(@Nullable String value, @Nullable String fallback) {
        return value != null ? value : fallback;
    }

    private enum Kind {
        CREATED, UPDATED, UNCHANGED
    }

    private record Outcome(Kind kind, @Nullable Long memberDbId) {
    }

    private static final class Seen {
        final Set<String> memberIds = new HashSet<>();
        final Set<String> domainEmails = new HashSet<>();
        final Set<String> pesels = new HashSet<>();
    }

    private static final class Plan {
        final String label;
        final Map<MemberField, @Nullable Object> values = new EnumMap<>(MemberField.class);
        final List<RowProblem> problems = new ArrayList<>();
        @Nullable Long primaryId;
        @Nullable Long votingId;
        @Nullable TMember existing;
        @Nullable LocalDate birthDate;
        @Nullable String canVoteText;

        Plan(String label) {
            this.label = label;
        }

        void add(Column column, ProblemCode code) {
            problems.add(new RowProblem(label, column.label(), code));
        }

        boolean hasErrors() {
            return problems.stream().anyMatch(p -> p.code().isError());
        }
    }
}
