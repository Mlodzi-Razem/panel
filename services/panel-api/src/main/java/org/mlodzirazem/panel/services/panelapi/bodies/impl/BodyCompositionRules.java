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

package org.mlodzirazem.panel.services.panelapi.bodies.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignMembership;
import org.mlodzirazem.panel.services.panelapi.bodies.CompositionAdvisory;
import org.mlodzirazem.panel.services.panelapi.bodies.FunctionViolation;
import org.mlodzirazem.panel.services.panelapi.bodies.FunctionWarning;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.AffiliationKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliationRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Statutory limits on who can hold which function in an organ (statute of 3.10.2026), kept in one
 * place so that every way of assigning a function — REST and the mandate importer through
 * {@code BodyMembershipService#check} — inherits them without copying.
 *
 * <ul>
 *   <li><b>Hard</b> (violations, nothing is written): the fixed size of an organ and the fixed number
 *       of people per function (art. 22 ust. 1, 26 ust. 1, 32 ust. 1, 35 ust. 1, 44 ust. 2–4).</li>
 *   <li><b>Confirmable</b> (warnings the caller has to confirm explicitly): full legal capacity
 *       (art. 17 ust. 3, 29 ust. 3) and a district board larger than art. 43 ust. 1 allows.</li>
 *   <li><b>Report</b> (advisories, never blocking): vacancies, board sizes below art. 43, circles
 *       with fewer than 6 members (art. 45 ust. 3).</li>
 * </ul>
 *
 * <p>Limits are keyed by the body kind and the member type code. A code the table does not know has
 * no per-function limit, but still counts towards the organ's size. Mandates that were voided or
 * ended before the assigned period never count.
 */
@Component
@RequiredArgsConstructor
class BodyCompositionRules {
    /** Art. 44 ust. 3: a commissioner board exists for at most 186 days, once. */
    static final int COMMISSIONER_BOARD_MAX_DAYS = 186;
    /** Art. 45 ust. 3: a local circle has at least 6 members. */
    static final int LOCAL_CIRCLE_MIN_MEMBERS = 6;

    private static final Map<BodyKind, Integer> ORGAN_SIZE = new EnumMap<>(Map.of(
        BodyKind.NATIONAL_BOARD, 7,
        BodyKind.PEER_COURT, 9,
        BodyKind.AUDIT_COMMISSION, 5,
        BodyKind.ELECTORAL_COMMISSION, 5,
        BodyKind.DISTRICT_COMMISSIONER, 2
    ));

    private static final Map<String, Integer> PRESIDIUM_SLOTS = Map.of("CHAIR", 1, "VICE_CHAIR", 1, "SECRETARY", 1);

    private static final Map<BodyKind, Map<String, Integer>> FUNCTION_SLOTS = new EnumMap<>(Map.of(
        BodyKind.NATIONAL_BOARD, Map.of("CO_CHAIR", 2, "SECRETARY", 1, "TREASURER", 1, "MEMBER", 3),
        BodyKind.DISTRICT_BOARD, Map.of("COORDINATOR", 1, "TREASURER", 1),
        BodyKind.PEER_COURT, PRESIDIUM_SLOTS,
        BodyKind.AUDIT_COMMISSION, PRESIDIUM_SLOTS,
        BodyKind.ELECTORAL_COMMISSION, PRESIDIUM_SLOTS
    ));

    /** Art. 17 ust. 3 (ZK, KKR, KSK) and art. 29 ust. 3 (the whole KSK presidium). */
    private static final Map<BodyKind, Set<String>> FULL_LEGAL_CAPACITY = new EnumMap<>(Map.of(
        BodyKind.NATIONAL_BOARD, Set.of("CO_CHAIR", "SECRETARY", "TREASURER"),
        BodyKind.AUDIT_COMMISSION, Set.of("CHAIR"),
        BodyKind.PEER_COURT, Set.of("CHAIR", "VICE_CHAIR", "SECRETARY")
    ));

    private final TBodyMembershipRepository memberships;
    private final TMemberDistrictAffiliationRepository affiliations;
    private final Clock clock;

    /** Everything that is about the body, not the member: sizes, slots, commissioner boards, art. 43. */
    void evaluate(
        AssignMembership request,
        TBody body,
        BodyKind kind,
        TBodyTemplateMemberType memberType,
        List<TBodyMembership> memberExisting,
        FunctionRules.Span span,
        List<FunctionViolation> violations,
        List<FunctionWarning> warnings
    ) {
        // ended mandates count too: a backdated assignment is measured against the people of its own period,
        // and a commissioner board's 186 days run from its first mandate even after that one has ended
        List<TBodyMembership> history = memberships.findAllByBodyId(body.getId());
        List<TBodyMembership> others = overlapping(history, span);
        String typeCode = memberType.getCode();
        Peak peak = peak(others, span, request.memberId(), typeCode);

        Integer organSize = ORGAN_SIZE.get(kind);

        if (organSize != null && peak.persons() > organSize) {
            violations.add(new FunctionViolation(FunctionViolation.Code.BODY_COMPOSITION_FULL,
                "%s has %d people at most (statute); the organ would have %d".formatted(
                    body.getName(), organSize, peak.persons())));
        }

        Integer slots = typeCode == null ? null : FUNCTION_SLOTS.getOrDefault(kind, Map.of()).get(typeCode);

        if (slots != null && peak.sameType() > slots) {
            violations.add(new FunctionViolation(FunctionViolation.Code.FUNCTION_SLOTS_FULL,
                "The function %s in %s has %d %s at most (statute)".formatted(
                    memberType.getName(), body.getName(), slots, slots == 1 ? "holder" : "holders")));
        }

        if (kind == BodyKind.DISTRICT_COMMISSIONER) {
            commissionerBoard(request, body, history, memberExisting, span, violations);
        }

        if (typeCode != null && FULL_LEGAL_CAPACITY.getOrDefault(kind, Set.of()).contains(typeCode)) {
            warnings.add(FunctionWarning.of(FunctionWarning.Code.FULL_LEGAL_CAPACITY_REQUIRED,
                "The function %s in %s requires full legal capacity (art. 17 ust. 3, art. 29 ust. 3 of the statute)"
                    .formatted(memberType.getName(), body.getName())));
        }

        if (kind == BodyKind.DISTRICT_BOARD) {
            districtBoardSize(body, peak.persons(), warnings);
        }
    }

    List<CompositionAdvisory> advisories(TBody body, BodyKind kind) {
        List<TBodyMembership> active = memberships.findActiveByBodyId(body.getId(), LocalDate.now(clock));
        int persons = (int) active.stream().map(m -> m.getMember().getId()).distinct().count();
        List<CompositionAdvisory> result = new ArrayList<>();

        if (kind == BodyKind.DISTRICT_BOARD) {
            long members = body.getParentBody() == null ? 0 : districtMembers(body.getParentBody().getId());

            if (members > 0) {
                Set<Integer> allowed = allowedBoardSizes(members);

                if (!allowed.contains(persons)) {
                    result.add(new CompositionAdvisory(CompositionAdvisory.Code.DISTRICT_BOARD_SIZE_MISMATCH,
                        "The board has %d people; art. 43 ust. 1 allows %s for a district of %d members".formatted(
                            persons, describe(allowed), members), persons, describe(allowed)));
                }
            }
        } else if (kind == BodyKind.LOCAL_CIRCLE) {
            if (persons < LOCAL_CIRCLE_MIN_MEMBERS) {
                result.add(new CompositionAdvisory(CompositionAdvisory.Code.LOCAL_CIRCLE_BELOW_MINIMUM,
                    "A local circle has at least %d members (art. 45 ust. 3); it has %d".formatted(
                        LOCAL_CIRCLE_MIN_MEMBERS, persons), persons, String.valueOf(LOCAL_CIRCLE_MIN_MEMBERS)));
            }
        } else {
            Integer size = ORGAN_SIZE.get(kind);
            int minimum = kind == BodyKind.DISTRICT_COMMISSIONER ? 1 : size == null ? 0 : size;

            if (size != null && persons < minimum) {
                result.add(new CompositionAdvisory(CompositionAdvisory.Code.BODY_COMPOSITION_INCOMPLETE,
                    "The organ has %d people, the statute fixes %s (vacancies)".formatted(
                        persons, kind == BodyKind.DISTRICT_COMMISSIONER ? "1 to 2" : String.valueOf(size)),
                    persons, kind == BodyKind.DISTRICT_COMMISSIONER ? "1-2" : String.valueOf(size)));
            }
        }

        return result;
    }

    private void commissionerBoard(
        AssignMembership request,
        TBody body,
        List<TBodyMembership> history,
        List<TBodyMembership> memberExisting,
        FunctionRules.Span span,
        List<FunctionViolation> violations
    ) {
        LocalDate start = Stream.concat(history.stream().map(TBodyMembership::getValidFrom), Stream.of(span.from()))
                                .min(Comparator.naturalOrder())
                                .orElseThrow();

        if (span.to() == null) {
            violations.add(new FunctionViolation(FunctionViolation.Code.COMMISSIONER_VALID_TO_REQUIRED,
                "valid_to is required for this function"));
        } else {
            // a backdated mandate moves the board's start back, so the mandates already recorded have to fit too
            LocalDate end = latestEnd(history, span.to());

            if (end.isAfter(start.plusDays(COMMISSIONER_BOARD_MAX_DAYS))) {
                violations.add(new FunctionViolation(FunctionViolation.Code.COMMISSIONER_BODY_TERM_EXCEEDED,
                    "A commissioner board exists for %d days at most, once (art. 44 ust. 3): it started on %s, so the last day is %s"
                        .formatted(COMMISSIONER_BOARD_MAX_DAYS, start, start.plusDays(COMMISSIONER_BOARD_MAX_DAYS))));
            }

            otherCommissionerBoards(body, new FunctionRules.Span(start, end), violations);
        }

        memberExisting.stream()
                      .filter(m -> m.getBody().getBodyTemplate() != null
                          && m.getBody().getBodyTemplate().getKind() == BodyKind.DISTRICT_COMMISSIONER)
                      .filter(m -> !m.getBody().getId().equals(body.getId()))
                      .filter(m -> span.overlaps(new FunctionRules.Span(m.getValidFrom(), m.getValidTo())))
                      .findFirst()
                      .ifPresent(other -> violations.add(FunctionViolation.of(
                          FunctionViolation.Code.COMMISSIONER_IN_ANOTHER_BODY,
                          "The member already holds a post in another commissioner board (art. 44 ust. 4)",
                          other.getId(), other.getBody().getId(), other.getBody().getName())));
    }

    /**
     * The district's other commissioner boards, backdated entries included: their periods never overlap this
     * one, and between the two starts a ZO mandate starts, before the later board does. Without it the second
     * board would be the first one extended under a new name.
     */
    private void otherCommissionerBoards(TBody body, FunctionRules.Span period, List<FunctionViolation> violations) {
        if (body.getParentBody() == null) {
            return;
        }

        long districtId = body.getParentBody().getId();
        Map<Long, List<TBodyMembership>> boards = memberships
            .findAllByParentBodyAndKind(districtId, BodyKind.DISTRICT_COMMISSIONER)
            .stream()
            .filter(m -> !m.getBody().getId().equals(body.getId()))
            .collect(Collectors.groupingBy(m -> m.getBody().getId(), LinkedHashMap::new, Collectors.toList()));

        if (boards.isEmpty()) {
            return;
        }

        List<LocalDate> zoStarts = memberships.findAllByParentBodyAndKind(districtId, BodyKind.DISTRICT_BOARD)
                                              .stream()
                                              .map(TBodyMembership::getValidFrom)
                                              .toList();

        for (List<TBodyMembership> mandates : boards.values()) {
            TBodyMembership first = mandates.getFirst();
            LocalDate otherStart = first.getValidFrom();
            boolean openEnded = mandates.stream().anyMatch(m -> m.getValidTo() == null);
            FunctionRules.Span other = new FunctionRules.Span(otherStart,
                openEnded ? null : latestEnd(mandates, otherStart));

            if (period.overlaps(other)) {
                violations.add(FunctionViolation.of(FunctionViolation.Code.COMMISSIONER_BOARDS_OVERLAP,
                    "The district had another commissioner board in this period", first.getId(),
                    first.getBody().getId(), first.getBody().getName()));
                continue;
            }

            LocalDate earlier = otherStart.isBefore(period.from()) ? otherStart : period.from();
            LocalDate later = otherStart.isBefore(period.from()) ? period.from() : otherStart;

            if (zoStarts.stream().noneMatch(d -> d.isAfter(earlier) && d.isBefore(later))) {
                violations.add(FunctionViolation.of(FunctionViolation.Code.COMMISSIONER_ZO_REQUIRED_BETWEEN,
                    "A second commissioner board needs a district board (ZO) mandate that started after %s and before %s"
                        .formatted(earlier, later), first.getId(), first.getBody().getId(),
                    first.getBody().getName()));
            }
        }
    }

    /** The latest end date among the mandates that have one, and {@code floor}. */
    private static LocalDate latestEnd(List<TBodyMembership> mandates, LocalDate floor) {
        return Stream.concat(mandates.stream().map(TBodyMembership::getValidTo).filter(Objects::nonNull),
                         Stream.of(floor))
                     .max(Comparator.naturalOrder())
                     .orElseThrow();
    }

    private void districtBoardSize(TBody body, int persons, List<FunctionWarning> warnings) {
        if (body.getParentBody() == null) {
            return;
        }

        long members = districtMembers(body.getParentBody().getId());

        if (members == 0) {
            return;
        }

        Set<Integer> allowed = allowedBoardSizes(members);
        int maximum = new TreeSet<>(allowed).last();

        if (persons > maximum) {
            warnings.add(FunctionWarning.of(FunctionWarning.Code.DISTRICT_BOARD_SIZE_EXCEEDS_STATUTE,
                "The board would have %d people; art. 43 ust. 1 allows %s for a district of %d members"
                    .formatted(persons, describe(allowed), members)));
        }
    }

    private long districtMembers(long districtId) {
        return affiliations.countMembersOfDistrict(districtId, LocalDate.now(clock), AffiliationKind.PRIMARY,
            Set.of(MemberStatus.REMOVED, MemberStatus.FORM_REQUEST));
    }

    /** Art. 43 ust. 1: 1–19 members → 1 person, 20–59 → 3, 60–89 → 3 or 5, above 89 → 5 or 7. */
    static Set<Integer> allowedBoardSizes(long districtMembers) {
        if (districtMembers < 20) {
            return Set.of(1);
        }

        if (districtMembers < 60) {
            return Set.of(3);
        }

        return districtMembers < 90 ? Set.of(3, 5) : Set.of(5, 7);
    }

    private static String describe(Set<Integer> sizes) {
        return sizes.stream().sorted().map(String::valueOf).collect(Collectors.joining(" or "));
    }

    private static List<TBodyMembership> overlapping(List<TBodyMembership> all, FunctionRules.Span span) {
        return all.stream()
                  .filter(m -> span.overlaps(new FunctionRules.Span(m.getValidFrom(), m.getValidTo())))
                  .toList();
    }

    private record Peak(int persons, int sameType) {
    }

    /**
     * The most people (and the most holders of the same function) the organ has on any day of the
     * period once the assignment is added: occupancy only changes when a mandate starts, so the
     * period's first day and every start inside it are the only days that have to be looked at.
     */
    private static Peak peak(List<TBodyMembership> others, FunctionRules.Span span, long memberId, String typeCode) {
        TreeSet<LocalDate> days = new TreeSet<>();
        days.add(span.from());

        for (TBodyMembership other : others) {
            if (other.getValidFrom().isAfter(span.from())) {
                days.add(other.getValidFrom());
            }
        }

        int persons = 1;
        int sameType = 1;

        for (LocalDate day : days) {
            List<TBodyMembership> active = others.stream()
                                                 .filter(m -> !m.getValidFrom().isAfter(day)
                                                     && (m.getValidTo() == null || m.getValidTo().isAfter(day)))
                                                 .toList();
            Set<Long> people = active.stream().map(m -> m.getMember().getId()).collect(Collectors.toSet());
            people.add(memberId);
            persons = Math.max(persons, people.size());

            long holders = active.stream()
                                 .filter(m -> typeCode != null && typeCode.equals(m.getMemberType().getCode()))
                                 .filter(m -> !m.getMember().getId().equals(memberId))
                                 .count();
            sameType = Math.max(sameType, (int) holders + 1);
        }

        return new Peak(persons, sameType);
    }
}
