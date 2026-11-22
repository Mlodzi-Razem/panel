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

package org.mlodzirazem.panel.services.panelapi.bodies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.EventListener;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
@Import(BodyMembershipServiceTest.EventCollector.class)
class BodyMembershipServiceTest extends DomainTestSupport {
    @Autowired
    BodyMembershipService service;

    @Autowired
    BodyService bodyService;

    @Autowired
    EventCollector events;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void clearEvents() {
        events.events.clear();
    }

    @Test
    void zkMemberWithKskFunctionIsBlockedAsIncompatibleMembership() {
        TMember member = newMember("Zed", "Konflikt", "mazowieckie");
        TBodyMembership ksk = grant(member, body("peer-court"), "MEMBER", today.minusMonths(6), null);
        AssignMembership request = assign(member, body("national-board"), "SECRETARY", today, null);

        AssignmentCheck check = service.check(request);

        assertThat(check.violations()).singleElement().satisfies(v -> {
            assertThat(v.code()).isEqualTo(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
            assertThat(v.conflictingMembershipId()).isEqualTo(ksk.getId());
            assertThat(v.conflictingBodyName()).isEqualTo(body("peer-court").getName());
        });
        assertThat(check.conflicts()).isEmpty();

        assertThatThrownBy(() -> service.assign(request, false)).isInstanceOf(AssignmentBlockedException.class);
        assertThatThrownBy(() -> service.assign(request, true)).isInstanceOf(AssignmentBlockedException.class);
        assertThat(membershipCount(member)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE valid_to IS NOT NULL",
            Integer.class)).isZero();
        assertThat(auditCount("bodies :: membership-assign")).isZero();
        assertThat(auditCount("bodies :: membership-end")).isZero();
    }

    @Test
    void zkIsIncompatibleWithAnyDistrictBoardEvenWhenSinglePerson() {
        TMember member = newMember("Zora", "Board", "mazowieckie");
        grant(member, board("mazowieckie"), "COORDINATOR", today.minusMonths(3), null);

        AssignmentCheck check = service.check(assign(member, body("national-board"), "CO_CHAIR", today, null));

        assertThat(check.violations()).singleElement().satisfies(v -> {
            assertThat(v.code()).isEqualTo(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
            assertThat(v.conflictingBodyName()).isEqualTo(board("mazowieckie").getName());
        });
    }

    @Test
    void kkwWithSinglePersonDistrictBoardIsAllowedByTheSinglePersonExemption() {
        TMember member = newMember("Kira", "Solo", "mazowieckie");
        grant(member, body("electoral-commission"), "MEMBER", today.minusMonths(3), null);

        AssignmentCheck check = service.check(assign(member, board("mazowieckie"), "COORDINATOR", today, null));

        assertThat(check.conflicts()).isEmpty();
        assertThat(check.violations()).isEmpty();
        assertThat(check.needsConfirmation()).isFalse();
        assertThat(service.assign(assign(member, board("mazowieckie"), "COORDINATOR", today, null), false)
                          .endedMemberships()).isEmpty();
    }

    @Test
    void kkwWithMultiPersonDistrictBoardIsIncompatible() {
        TMember other = newMember("Olga", "Other", "mazowieckie");
        TMember member = newMember("Kira", "Multi", "mazowieckie");
        grant(other, board("mazowieckie"), "COORDINATOR", today.minusMonths(3), null);
        grant(member, body("electoral-commission"), "CHAIR", today.minusMonths(3), null);

        AssignmentCheck check = service.check(assign(member, board("mazowieckie"), "VICE_COORDINATOR", today, null));

        assertThat(check.violations()).singleElement().satisfies(v -> {
            assertThat(v.code()).isEqualTo(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
            assertThat(v.conflictingBodyName()).isEqualTo(body("electoral-commission").getName());
        });
    }

    @Test
    void assigningKskToSolePersonZoMemberIsAllowedButNotWhenBoardHasOthers() {
        TMember solo = newMember("Sol", "Solo", "slaskie");
        grant(solo, board("slaskie"), "COORDINATOR", today.minusMonths(3), null);

        assertThat(service.check(assign(solo, body("peer-court"), "MEMBER", today, null)).violations()).isEmpty();

        TMember second = newMember("Sec", "Ond", "slaskie");
        grant(second, board("slaskie"), "VICE_COORDINATOR", today.minusMonths(1), null);

        assertThat(service.check(assign(solo, body("audit-commission"), "MEMBER", today, null)).violations())
            .singleElement().satisfies(v -> assertThat(v.conflictingBodyName())
                .isEqualTo(board("slaskie").getName()));
    }

    @Test
    void compatibleFunctionsDoNotConflict() {
        TMember member = newMember("Cia", "Compat", "mazowieckie");
        grant(member, body("peer-court"), "MEMBER", today.minusMonths(6), null);

        AssignmentCheck check = service.check(assign(member, body("press-spokesperson"), "SPOKESPERSON", today, null));

        assertThat(check.conflicts()).isEmpty();
        assertThat(check.warnings()).isEmpty();
        assertThat(check.violations()).isEmpty();
    }

    @Test
    void kskKkrAndKkwExcludeEachOther() {
        TMember member = newMember("Nora", "National", "mazowieckie");
        grant(member, body("peer-court"), "MEMBER", today.minusMonths(6), null);

        assertThat(service.check(assign(member, body("audit-commission"), "MEMBER", today, null)).violations())
            .singleElement().satisfies(v -> {
                assertThat(v.code()).isEqualTo(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
                assertThat(v.conflictingBodyName()).isEqualTo(body("peer-court").getName());
            });
        assertThat(service.check(assign(member, body("electoral-commission"), "MEMBER", today, null)).violations())
            .singleElement().satisfies(v -> {
                assertThat(v.code()).isEqualTo(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
                assertThat(v.conflictingBodyName()).isEqualTo(body("peer-court").getName());
            });

        TMember auditor = newMember("Ada", "Audit", "mazowieckie");
        grant(auditor, body("audit-commission"), "MEMBER", today.minusMonths(6), null);

        assertThat(service.check(assign(auditor, body("electoral-commission"), "MEMBER", today, null)).violations())
            .singleElement().satisfies(v -> {
                assertThat(v.code()).isEqualTo(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
                assertThat(v.conflictingBodyName()).isEqualTo(body("audit-commission").getName());
            });
    }

    @Test
    void joiningASinglePersonBoardWarnsAboutTheLostExemption() {
        TMember solo = newMember("Sola", "Kkr", "pomorskie");
        grant(solo, board("pomorskie"), "COORDINATOR", today.minusMonths(3), null);
        grant(solo, body("audit-commission"), "MEMBER", today.minusMonths(2), null);
        TMember joiner = newMember("Jan", "Joiner", "pomorskie");

        AssignmentCheck check = service.check(assign(joiner, board("pomorskie"), "VICE_COORDINATOR", today, null));

        assertThat(check.conflicts()).isEmpty();
        assertThat(check.needsConfirmation()).isTrue();
        assertThat(check.warnings()).singleElement().satisfies(w -> {
            assertThat(w.code()).isEqualTo(FunctionWarning.Code.SINGLE_PERSON_EXEMPTION_LOST);
            assertThat(w.affectedMemberId()).isEqualTo(solo.getId());
            assertThat(w.affectedBodyName()).isEqualTo(body("audit-commission").getName());
        });
    }

    @Test
    void joiningABoardWithoutExemptedFunctionsDoesNotWarn() {
        TMember coordinator = newMember("Ola", "Plain", "lubuskie");
        grant(coordinator, board("lubuskie"), "COORDINATOR", today.minusMonths(3), null);
        TMember joiner = newMember("Ewa", "Joiner", "lubuskie");

        assertThat(service.check(assign(joiner, board("lubuskie"), "VICE_COORDINATOR", today, null)).warnings())
            .isEmpty();
    }

    @Test
    void anIncompatibleMandateStartingLaterIsAlsoBlocked() {
        TMember member = newMember("Lara", "Later", "mazowieckie");
        grant(member, body("peer-court"), "MEMBER", today.plusMonths(2), null);

        AssignmentCheck check = service.check(assign(member, body("national-board"), "SECRETARY", today, null));

        assertThat(check.violations()).singleElement().satisfies(v -> {
            assertThat(v.code()).isEqualTo(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
            assertThat(v.conflictingBodyName()).isEqualTo(body("peer-court").getName());
        });
        assertThatThrownBy(() -> service.assign(assign(member, body("national-board"), "SECRETARY", today, null), true))
            .isInstanceOf(AssignmentBlockedException.class);
    }

    @Test
    void thirdConsecutiveYearInTheSameBodyWarns() {
        TMember member = newMember("Tara", "Terms", "mazowieckie");
        LocalDate start = today.minusYears(2);
        grant(member, body("national-board"), "SECRETARY", start, start.plusYears(1));
        grant(member, body("national-board"), "SECRETARY", start.plusYears(1), today);

        AssignMembership third = assign(member, body("national-board"), "SECRETARY", today, today.plusYears(1));
        AssignmentCheck check = service.check(third);

        assertThat(check.warnings()).singleElement().satisfies(w -> {
            assertThat(w.code()).isEqualTo(FunctionWarning.Code.TERM_LIMIT_EXCEEDED);
            assertThat(w.consecutiveTerms()).isEqualTo(3);
            assertThat(w.maxConsecutiveTerms()).isEqualTo(2);
        });
        assertThat(check.violations()).isEmpty();
        assertThatThrownBy(() -> service.assign(third, false)).isInstanceOf(ConfirmationRequiredException.class);
        assertThat(service.assign(third, true).membership().getId()).isNotNull();
    }

    @Test
    void secondConsecutiveYearAndNonConsecutiveTermsDoNotWarn() {
        TMember member = newMember("Nora", "Normal", "mazowieckie");
        LocalDate start = today.minusYears(4);
        grant(member, body("national-board"), "SECRETARY", start, start.plusYears(1));
        grant(member, body("national-board"), "SECRETARY", today.minusYears(1), today);

        AssignmentCheck check = service.check(assign(member, body("national-board"), "SECRETARY", today, today.plusYears(1)));

        assertThat(check.warnings()).isEmpty();
    }

    @Test
    void commissionerNeedsValidToAndIsLimitedToThreeMonths() {
        TMember member = newMember("Cora", "Komisarz", "mazowieckie");
        TBody commissioners = commissionerBody("mazowieckie");

        assertThat(service.check(assign(member, commissioners, "COMMISSIONER", today, null)).violations())
            .extracting(FunctionViolation::code).containsExactly(FunctionViolation.Code.COMMISSIONER_VALID_TO_REQUIRED);
        assertThat(service.check(assign(member, commissioners, "COMMISSIONER", today, today.plusMonths(4))).violations())
            .extracting(FunctionViolation::code).containsExactly(FunctionViolation.Code.COMMISSIONER_TERM_TOO_LONG);
        assertThat(service.check(assign(member, commissioners, "COMMISSIONER", today, today.plusMonths(3))).violations())
            .isEmpty();
    }

    @Test
    void commissionerTermCanBeExtendedAtMostTwice() {
        TMember member = newMember("Eli", "Extend", "mazowieckie");
        TBody commissioners = commissionerBody("mazowieckie");
        LocalDate start = today.minusMonths(7);

        service.assign(assign(member, commissioners, "COMMISSIONER", start, start.plusMonths(3)), true);
        service.assign(assign(member, commissioners, "COMMISSIONER", start.plusMonths(3), start.plusMonths(6)), true);
        service.assign(assign(member, commissioners, "COMMISSIONER", start.plusMonths(6), start.plusMonths(9)), true);

        AssignMembership fourth = assign(member, commissioners, "COMMISSIONER", start.plusMonths(9), start.plusMonths(12));

        assertThat(service.check(fourth).violations()).extracting(FunctionViolation::code)
                                                      .contains(FunctionViolation.Code.COMMISSIONER_EXTENSIONS_EXCEEDED);
        assertThatThrownBy(() -> service.assign(fourth, true)).isInstanceOf(AssignmentBlockedException.class);
    }

    @Test
    void rejectsMismatchedMemberTypeOverlapAndInvalidPeriod() {
        TMember member = newMember("Mia", "Mismatch", "mazowieckie");
        grant(member, board("mazowieckie"), "COORDINATOR", today.minusMonths(1), null);

        assertThat(service.check(new AssignMembership(member.getId(), body("peer-court").getId(),
            memberType(BodyKind.NATIONAL_BOARD, "SECRETARY").getId(), today, null)).violations())
            .extracting(FunctionViolation::code).contains(FunctionViolation.Code.MEMBER_TYPE_MISMATCH);
        assertThat(service.check(assign(member, board("mazowieckie"), "COORDINATOR", today, null)).violations())
            .extracting(FunctionViolation::code).contains(FunctionViolation.Code.DUPLICATE_MEMBERSHIP);
        assertThat(service.check(assign(member, body("peer-court"), "MEMBER", today, today)).violations())
            .extracting(FunctionViolation::code).contains(FunctionViolation.Code.INVALID_PERIOD);
    }

    @Test
    void endingAMembershipPublishesAnEvent() {
        TMember member = newMember("Eda", "Ender", "mazowieckie");
        TBodyMembership membership = grant(member, body("peer-court"), "MEMBER", today.minusMonths(1), null);

        TBodyMembership ended = service.end(membership.getId(), null, null);

        assertThat(ended.getValidTo()).isEqualTo(today);
        assertThat(events.events).singleElement().satisfies(e -> {
            assertThat(e.change()).isEqualTo(BodyMembershipChanged.Change.ENDED);
            assertThat(e.bodyMembershipId()).isEqualTo(membership.getId());
        });
        assertThat(service.activeOf(member.getId())).isEmpty();
        assertThat(service.historyOf(member.getId())).hasSize(1);
        assertThatThrownBy(() -> service.end(membership.getId(), null, null)).hasMessageContaining("already closed");
    }

    @Test
    void bodiesCanBeCreatedUnderTheRightParentTemplate() {
        TBody created = bodyService.create(new NewBody(template(BodyKind.LOCAL_CIRCLE).getId(), "Test Koło Gliwice", null,
            district("slaskie").getId(), "test-circle-gliwice"));

        assertThat(bodyService.get(created.getId()).getName()).isEqualTo("Test Koło Gliwice");
        assertThat(auditCount("bodies :: create")).isEqualTo(1);

        assertThatThrownBy(() -> bodyService.create(new NewBody(template(BodyKind.LOCAL_CIRCLE).getId(), "x", null,
            body("national-board").getId(), null))).hasMessageContaining("wrong template");
        assertThatThrownBy(() -> bodyService.create(new NewBody(template(BodyKind.LOCAL_CIRCLE).getId(), "x", null,
            null, null))).hasMessageContaining("needs a parent");
        assertThatThrownBy(() -> bodyService.create(new NewBody(template(BodyKind.TEAM).getId(), "x", null,
            district("slaskie").getId(), null))).hasMessageContaining("cannot have a parent");
    }

    @Test
    void incompatibilityIsJudgedOnDateRangesNotOnlyOpenMandates() {
        TMember overlapping = newMember("Ola", "Overlap", "mazowieckie");
        grant(overlapping, body("national-board"), "SECRETARY", today.minusYears(2), today.plusMonths(6));

        assertThat(service.check(assign(overlapping, board("mazowieckie"), "COORDINATOR", today, today.plusYears(1)))
                       .violations())
            .singleElement().satisfies(v -> assertThat(v.code())
                .isEqualTo(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP));

        TMember boundary = newMember("Ada", "Boundary", "mazowieckie");
        grant(boundary, body("national-board"), "SECRETARY", today.minusYears(2), today);

        assertThat(service.check(assign(boundary, board("mazowieckie"), "COORDINATOR", today, null)).violations())
            .isEmpty();

        TMember disjoint = newMember("Iwa", "Disjoint", "mazowieckie");
        grant(disjoint, body("national-board"), "SECRETARY", today.minusYears(4), today.minusYears(3));

        assertThat(service.check(assign(disjoint, board("mazowieckie"), "COORDINATOR", today.minusYears(2), null))
                       .violations()).isEmpty();
    }

    @Test
    void closingAMembershipRetroactivelyRequiresADecisionReference() {
        TMember member = newMember("Rita", "Retro", "mazowieckie");
        TBodyMembership membership = grant(member, body("peer-court"), "MEMBER", today.minusMonths(6), null);

        assertThatThrownBy(() -> service.end(membership.getId(), today.minusDays(10), null))
            .isInstanceOf(RuleViolationException.class)
            .hasMessageContaining("decision reference");

        assertThat(jdbc.queryForObject("SELECT valid_to FROM body_memberships WHERE id = ?", LocalDate.class,
            membership.getId())).isNull();

        TBodyMembership ended = service.end(membership.getId(), today.minusDays(10), "ZO/MZ/01/2026");

        assertThat(ended.getValidTo()).isEqualTo(today.minusDays(10));
        assertThat(jdbc.queryForObject("SELECT decision_reference FROM body_memberships WHERE id = ?", String.class,
            membership.getId())).isEqualTo("ZO/MZ/01/2026");
        assertThat(auditPayloads("bodies :: membership-end")).contains("decisionReference");

        assertThatThrownBy(() -> service.end(membership.getId(), null, null))
            .hasMessageContaining("already closed");
    }

    @Test
    void endingTodayOrLaterNeedsNoReasonButAClosedMandateCannotBeExtended() {
        TMember member = newMember("Fran", "Future", "mazowieckie");
        TBodyMembership membership = grant(member, body("peer-court"), "MEMBER", today.minusMonths(1), null);

        TBodyMembership planned = service.end(membership.getId(), today.plusDays(5), null);

        assertThat(planned.getValidTo()).isEqualTo(today.plusDays(5));
        assertThat(jdbc.queryForObject("SELECT decision_reference FROM body_memberships WHERE id = ?", String.class,
            membership.getId())).isNull();

        assertThatThrownBy(() -> service.end(membership.getId(), today.plusDays(30), null))
            .hasMessageContaining("already closed");

        TBodyMembership shortened = service.end(membership.getId(), today, "KKR/02/2026");

        assertThat(shortened.getValidTo()).isEqualTo(today);
    }

    private TBody commissionerBody(String districtSlug) {
        return bodyService.create(new NewBody(template(BodyKind.DISTRICT_COMMISSIONER).getId(),
            "Test Komisarz " + districtSlug, null, district(districtSlug).getId(), "test-commissioner-" + districtSlug));
    }

    private AssignMembership assign(TMember member, TBody body, String typeCode, LocalDate from, LocalDate to) {
        BodyKind kind = BodyKind.valueOf(jdbc.queryForObject(
            "SELECT t.kind::text FROM bodies b JOIN body_templates t ON t.id = b.body_template_id WHERE b.id = ?",
            String.class, body.getId()));

        return new AssignMembership(member.getId(), body.getId(), memberType(kind, typeCode).getId(), from, to);
    }

    private int membershipCount(TMember member) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE member_id = ?", Integer.class,
            member.getId());
        return count == null ? 0 : count;
    }

    @TestComponent
    static class EventCollector {
        final List<BodyMembershipChanged> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(BodyMembershipChanged event) {
            events.add(event);
        }
    }
}
