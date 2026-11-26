/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Statute 2026 rules on who can hold which function (A4b): fixed organ sizes and function slots are
 * hard violations, full legal capacity and an oversized district board are warnings the caller has
 * to confirm explicitly, and the decision reference is stored with the mandate.
 */
@SpringBootTest
@PostgresTest
class BodyCompositionRulesTest extends DomainTestSupport {
    @Autowired
    BodyMembershipService service;

    @Autowired
    BodyService bodyService;

    private final LocalDate today = LocalDate.now();

    @Test
    void zkHasAtMostSevenPeopleAndTheEighthIsBlocked() {
        fill("national-board", BodyKind.NATIONAL_BOARD, 7);
        TMember eighth = newMember("Osma", "Osoba", "mazowieckie");

        AssignmentCheck check = service.check(assign(eighth, body("national-board"), "SECRETARY", today, null));

        assertThat(check.violations()).extracting(FunctionViolation::code)
                                      .containsExactly(FunctionViolation.Code.BODY_COMPOSITION_FULL);
        assertThatThrownBy(() -> service.assign(assign(eighth, body("national-board"), "SECRETARY", today, null)
            .withConfirmedWarnings(Set.of(FunctionWarning.Code.values()))))
            .isInstanceOf(AssignmentBlockedException.class)
            .extracting(e -> ((AssignmentBlockedException) e).getCode())
            .isEqualTo("BODY_COMPOSITION_FULL");
        assertThat(membershipCount(eighth)).isZero();
        assertThat(auditCount("bodies :: membership-assign")).isZero();
    }

    @Test
    void aPersonAlreadyInTheOrganDoesNotTakeAnotherSeat() {
        fill("national-board", BodyKind.NATIONAL_BOARD, 6);
        TMember holder = newMember("Dwie", "Funkcje", "mazowieckie");
        grant(holder, body("national-board"), "CO_CHAIR", today.minusMonths(1), null);

        // seven people; the same person taking a second function in the ZK is still seven people
        AssignmentCheck check = service.check(assign(holder, body("national-board"), "SECRETARY", today, null));

        assertThat(check.violations()).extracting(FunctionViolation::code)
                                      .doesNotContain(FunctionViolation.Code.BODY_COMPOSITION_FULL);
    }

    @Test
    void zkFunctionsHaveStatutorySlots() {
        TBody zk = body("national-board");
        grant(newMember("Pierwsza", "Wspolprzewodniczaca", "mazowieckie"), zk, "CO_CHAIR", today.minusMonths(1), null);
        grant(newMember("Drugi", "Wspolprzewodniczacy", "mazowieckie"), zk, "CO_CHAIR", today.minusMonths(1), null);
        grant(newMember("Jedyna", "Sekretarz", "mazowieckie"), zk, "SECRETARY", today.minusMonths(1), null);
        TMember newcomer = newMember("Nowa", "Osoba", "mazowieckie");

        assertThat(violationsOf(newcomer, zk, "CO_CHAIR")).containsExactly(FunctionViolation.Code.FUNCTION_SLOTS_FULL);
        assertThat(violationsOf(newcomer, zk, "SECRETARY")).containsExactly(FunctionViolation.Code.FUNCTION_SLOTS_FULL);
        assertThat(violationsOf(newcomer, zk, "TREASURER")).isEmpty();
    }

    @Test
    void anEndedOrNotOverlappingMandateDoesNotTakeASlot() {
        TBody zk = body("national-board");
        TMember newcomer = newMember("Nowy", "Sekretarz", "mazowieckie");
        grant(newMember("Byly", "Sekretarz", "mazowieckie"), zk, "SECRETARY", today.minusYears(1), today.minusDays(1));

        assertThat(violationsOf(newcomer, zk, "SECRETARY")).isEmpty();

        // a successor starting later does not collide with a period that ends before it
        grant(newMember("Przyszly", "Sekretarz", "mazowieckie"), zk, "SECRETARY", today.plusMonths(2), null);

        assertThat(service.check(assign(newcomer, zk, "SECRETARY", today, today.plusMonths(1))).violations()).isEmpty();
        assertThat(violationsOf(newcomer, zk, "SECRETARY")).containsExactly(FunctionViolation.Code.FUNCTION_SLOTS_FULL);
    }

    @Test
    void kskKkrAndKkwHaveFixedSizes() {
        fill("peer-court", BodyKind.PEER_COURT, 9);
        fill("audit-commission", BodyKind.AUDIT_COMMISSION, 5);
        fill("electoral-commission", BodyKind.ELECTORAL_COMMISSION, 5);
        TMember extra = newMember("Dodatkowy", "Czlonek", "mazowieckie");

        assertThat(violationsOf(extra, body("peer-court"), "MEMBER"))
            .containsExactly(FunctionViolation.Code.BODY_COMPOSITION_FULL);
        assertThat(violationsOf(extra, body("audit-commission"), "MEMBER"))
            .containsExactly(FunctionViolation.Code.BODY_COMPOSITION_FULL);
        assertThat(violationsOf(extra, body("electoral-commission"), "MEMBER"))
            .containsExactly(FunctionViolation.Code.BODY_COMPOSITION_FULL);
    }

    @Test
    void presidiumFunctionsOfKskAreSingle() {
        TBody ksk = body("peer-court");
        grant(newMember("Pani", "Przewodniczaca", "mazowieckie"), ksk, "CHAIR", today.minusMonths(1), null);
        TMember other = newMember("Druga", "Kandydatka", "mazowieckie");

        assertThat(violationsOf(other, ksk, "CHAIR")).containsExactly(FunctionViolation.Code.FUNCTION_SLOTS_FULL);
        assertThat(violationsOf(other, ksk, "VICE_CHAIR")).isEmpty();
    }

    @Test
    void districtBoardHasOneCoordinator() {
        TBody zo = board("mazowieckie");
        grant(newMember("Pierwszy", "Koordynator", "mazowieckie"), zo, "COORDINATOR", today.minusMonths(1), null);

        assertThat(violationsOf(newMember("Drugi", "Koordynator", "mazowieckie"), zo, "COORDINATOR"))
            .containsExactly(FunctionViolation.Code.FUNCTION_SLOTS_FULL);
    }

    @Test
    void commissionerBoardNeedsAnEndDateAndLastsAtMost186DaysOnce() {
        TBody commissioners = commissionerBody("mazowieckie");
        TMember first = newMember("Pierwszy", "Komisarz", "mazowieckie");
        TMember second = newMember("Drugi", "Komisarz", "mazowieckie");

        assertThat(service.check(assign(first, commissioners, "COMMISSIONER", today, null)).violations())
            .extracting(FunctionViolation::code).containsExactly(FunctionViolation.Code.COMMISSIONER_VALID_TO_REQUIRED);
        assertThat(service.check(assign(first, commissioners, "COMMISSIONER", today, today.plusDays(187))).violations())
            .extracting(FunctionViolation::code).containsExactly(FunctionViolation.Code.COMMISSIONER_BODY_TERM_EXCEEDED);
        assertThat(service.check(assign(first, commissioners, "COMMISSIONER", today, today.plusDays(186))).violations())
            .isEmpty();

        service.assign(assign(first, commissioners, "COMMISSIONER", today, today.plusDays(100)));

        // the board's 186 days run from its first mandate: a later colleague cannot outlast them
        assertThat(service.check(assign(second, commissioners, "COMMISSIONER", today.plusDays(50), today.plusDays(190)))
            .violations()).extracting(FunctionViolation::code)
                          .containsExactly(FunctionViolation.Code.COMMISSIONER_BODY_TERM_EXCEEDED);
        assertThat(service.check(assign(second, commissioners, "COMMISSIONER", today.plusDays(50), today.plusDays(186)))
            .violations()).isEmpty();

        // no extension: the same person cannot get a second mandate that carries the board past 186 days
        assertThat(service.check(assign(first, commissioners, "COMMISSIONER", today.plusDays(100), today.plusDays(250)))
            .violations()).extracting(FunctionViolation::code)
                          .contains(FunctionViolation.Code.COMMISSIONER_BODY_TERM_EXCEEDED);
    }

    @Test
    void theCommissionerBoards186DaysRunFromItsFirstMandateEvenAfterThatOneEnded() {
        TBody commissioners = commissionerBody("mazowieckie");
        LocalDate start = today.minusDays(200);
        grant(newMember("Dawny", "Komisarz", "mazowieckie"), commissioners, "COMMISSIONER", start,
            start.plusDays(100));

        // no second round after the term, although nobody sits on the board today
        assertThat(violationsOf(newMember("Spozniony", "Komisarz", "mazowieckie"), commissioners, "COMMISSIONER", 30))
            .containsExactly(FunctionViolation.Code.COMMISSIONER_BODY_TERM_EXCEEDED);
    }

    @Test
    void aBackdatedCommissionerMandateCannotLeaveALaterOnePast186Days() {
        TBody commissioners = commissionerBody("mazowieckie");
        grant(newMember("Obecny", "Komisarz", "mazowieckie"), commissioners, "COMMISSIONER", today,
            today.plusDays(150));
        TMember earlier = newMember("Wsteczny", "Komisarz", "mazowieckie");

        assertThat(service.check(assign(earlier, commissioners, "COMMISSIONER", today.minusDays(60),
            today.minusDays(30))).violations()).extracting(FunctionViolation::code)
                                               .containsExactly(FunctionViolation.Code.COMMISSIONER_BODY_TERM_EXCEEDED);
        assertThat(service.check(assign(earlier, commissioners, "COMMISSIONER", today.minusDays(36),
            today.minusDays(30))).violations()).isEmpty();
    }

    @Test
    void aBackdatedAssignmentIsMeasuredAgainstTheHoldersOfItsOwnPeriod() {
        TBody zk = body("national-board");
        grant(newMember("Dawna", "Sekretarz", "mazowieckie"), zk, "SECRETARY", today.minusYears(2),
            today.minusYears(1));

        assertThat(service.check(assign(newMember("Wsteczna", "Sekretarz", "mazowieckie"), zk, "SECRETARY",
            today.minusMonths(18), today.minusMonths(15))).violations()).extracting(FunctionViolation::code)
                                                                       .containsExactly(
                                                                           FunctionViolation.Code.FUNCTION_SLOTS_FULL);
    }

    @Test
    void aNewCommissionerBoardCanBeCreatedOnlyWhenTheEarlierOneIsClosed() {
        TBody first = commissionerBody("mazowieckie");

        // still waiting for its mandates
        assertStillOpen();

        TBodyMembership mandate = grant(newMember("Pierwszy", "Komisarz", "mazowieckie"), first, "COMMISSIONER",
            today.minusDays(100), today.plusDays(1));
        assertStillOpen();

        jdbc.update("UPDATE body_memberships SET valid_to = ? WHERE id = ?", today, mandate.getId());

        assertThat(nextCommissionerBody().getId()).isNotEqualTo(first.getId());
    }

    @Test
    void aLaterCommissionerBoardNeedsAZoMandateStartedBetweenTheTwoStarts() {
        TBody first = commissionerBody("mazowieckie");
        TMember commissioner = newMember("Dawny", "Komisarz", "mazowieckie");
        LocalDate firstStart = today.minusDays(400);
        grant(commissioner, first, "COMMISSIONER", firstStart, firstStart.plusDays(186));
        TBody second = nextCommissionerBody();
        AssignMembership later = assign(commissioner, second, "COMMISSIONER", today, today.plusDays(100));

        // a ZO mandate that was already running when the first board started proves nothing
        grant(newMember("Stary", "Zarzad", "mazowieckie"), board("mazowieckie"), "MEMBER", firstStart,
            firstStart.plusDays(1));
        assertThat(service.check(later).violations()).singleElement().satisfies(v -> {
            assertThat(v.code()).isEqualTo(FunctionViolation.Code.COMMISSIONER_ZO_REQUIRED_BETWEEN);
            assertThat(v.conflictingBodyId()).isEqualTo(first.getId());
        });
        assertThatThrownBy(() -> service.assign(later.withConfirmedWarnings(Set.of(FunctionWarning.Code.values()))))
            .isInstanceOf(AssignmentBlockedException.class);

        // nor does one that starts together with the new board, or one in another district
        grant(newMember("Rowny", "Zarzad", "mazowieckie"), board("mazowieckie"), "MEMBER", today, null);
        grant(newMember("Obcy", "Zarzad", "slaskie"), board("slaskie"), "MEMBER", today.minusDays(100), null);
        assertThat(service.check(later).violations()).extracting(FunctionViolation::code)
            .containsExactly(FunctionViolation.Code.COMMISSIONER_ZO_REQUIRED_BETWEEN);

        grant(newMember("Nowy", "Zarzad", "mazowieckie"), board("mazowieckie"), "COORDINATOR",
            today.minusDays(100), today.minusDays(10));

        assertThat(service.check(later).violations()).isEmpty();
    }

    @Test
    void aVoidedZoMandateDoesNotOpenTheWayToALaterCommissionerBoard() {
        TBody first = commissionerBody("mazowieckie");
        grant(newMember("Dawny", "Komisarz", "mazowieckie"), first, "COMMISSIONER", today.minusDays(400),
            today.minusDays(300));
        TBody second = nextCommissionerBody();
        long mistaken = grant(newMember("Omylkowy", "Zarzad", "mazowieckie"), board("mazowieckie"), "MEMBER",
            today.minusDays(200), today.minusDays(100)).getId();
        service.voidMembership(mistaken, "wpis omyłkowy", null);

        assertThat(violationsOf(newMember("Nowy", "Komisarz", "mazowieckie"), second, "COMMISSIONER", 30))
            .containsExactly(FunctionViolation.Code.COMMISSIONER_ZO_REQUIRED_BETWEEN);
    }

    @Test
    void commissionerBoardsOfADistrictNeverOverlapAlsoThroughBackdatedMandates() {
        TBody first = commissionerBody("mazowieckie");
        grant(newMember("Dawny", "Komisarz", "mazowieckie"), first, "COMMISSIONER", today.minusDays(300),
            today.minusDays(200));
        TBody second = nextCommissionerBody();
        TMember other = newMember("Inny", "Komisarz", "mazowieckie");

        assertThat(service.check(assign(other, second, "COMMISSIONER", today.minusDays(250), today.minusDays(150)))
            .violations()).extracting(FunctionViolation::code)
                          .containsExactly(FunctionViolation.Code.COMMISSIONER_BOARDS_OVERLAP);
        // recorded later but lying earlier: the ZO mandate is required between the two starts just the same
        assertThat(service.check(assign(other, second, "COMMISSIONER", today.minusDays(500), today.minusDays(400)))
            .violations()).extracting(FunctionViolation::code)
                          .containsExactly(FunctionViolation.Code.COMMISSIONER_ZO_REQUIRED_BETWEEN);

        grant(newMember("Pomiedzy", "Zarzad", "mazowieckie"), board("mazowieckie"), "MEMBER", today.minusDays(350),
            today.minusDays(320));

        assertThat(service.check(assign(other, second, "COMMISSIONER", today.minusDays(500), today.minusDays(400)))
            .violations()).isEmpty();
    }

    private void assertStillOpen() {
        assertThatThrownBy(this::nextCommissionerBody).isInstanceOfSatisfying(ConflictException.class,
            e -> assertThat(e.getCode()).isEqualTo("COMMISSIONER_BOARD_STILL_OPEN"));
    }

    private TBody nextCommissionerBody() {
        return bodyService.create(new NewBody(template(BodyKind.DISTRICT_COMMISSIONER).getId(),
            "Test Komisarz kolejny", null, district("mazowieckie").getId(), "test-commissioner-next"));
    }

    @Test
    void commissionerBoardHasAtMostTwoPeople() {
        TBody commissioners = commissionerBody("mazowieckie");
        grant(newMember("Jeden", "Komisarz", "mazowieckie"), commissioners, "COMMISSIONER", today, today.plusDays(100));
        grant(newMember("Dwa", "Komisarz", "mazowieckie"), commissioners, "COMMISSIONER", today, today.plusDays(100));

        assertThat(violationsOf(newMember("Trzeci", "Komisarz", "mazowieckie"), commissioners, "COMMISSIONER", 100))
            .containsExactly(FunctionViolation.Code.BODY_COMPOSITION_FULL);
    }

    @Test
    void aPersonSitsInOneCommissionerBoardAtATime() {
        TMember member = newMember("Wedrowny", "Komisarz", "mazowieckie");
        TBody elsewhere = commissionerBody("slaskie");
        grant(member, elsewhere, "COMMISSIONER", today, today.plusDays(100));
        TBody here = commissionerBody("mazowieckie");

        AssignmentCheck check = service.check(assign(member, here, "COMMISSIONER",
            today.plusDays(10), today.plusDays(60)));

        assertThat(check.violations()).singleElement().satisfies(v -> {
            assertThat(v.code()).isEqualTo(FunctionViolation.Code.COMMISSIONER_IN_ANOTHER_BODY);
            assertThat(v.conflictingBodyName()).isEqualTo(elsewhere.getName());
        });
        // after the first board's mandate ended there is nothing in the way
        assertThat(service.check(assign(member, here, "COMMISSIONER",
            today.plusDays(100), today.plusDays(150))).violations()).isEmpty();
    }

    @Test
    void fullLegalCapacityIsAWarningThatHasToBeConfirmedBeforeAnythingIsWritten() {
        TMember member = newMember("Zdolny", "Prawnie", "mazowieckie");
        AssignMembership request = assign(member, body("national-board"), "CO_CHAIR", today, null)
            .withDecisionReference("ZK/12/2026");

        AssignmentCheck check = service.check(request);

        assertThat(check.violations()).isEmpty();
        assertThat(check.warnings()).singleElement().satisfies(w -> {
            assertThat(w.code()).isEqualTo(FunctionWarning.Code.FULL_LEGAL_CAPACITY_REQUIRED);
            assertThat(w.confirmed()).isFalse();
        });
        assertThat(check.needsConfirmation()).isTrue();

        assertThatThrownBy(() -> service.assign(request)).isInstanceOfSatisfying(ConfirmationRequiredException.class,
            e -> assertThat(e.getCheck().warnings()).extracting(FunctionWarning::code)
                            .containsExactly(FunctionWarning.Code.FULL_LEGAL_CAPACITY_REQUIRED));
        assertThat(membershipCount(member)).isZero();
        assertThat(auditCount("bodies :: membership-assign")).isZero();

        AssignmentResult result = service.assign(request.withConfirmedWarnings(
            Set.of(FunctionWarning.Code.FULL_LEGAL_CAPACITY_REQUIRED)));

        assertThat(result.membership().getDecisionReference()).isEqualTo("ZK/12/2026");
        assertThat(result.check().warnings()).singleElement().satisfies(w -> assertThat(w.confirmed()).isTrue());
        assertThat(membershipCount(member)).isEqualTo(1);
        String audit = auditPayloads("bodies :: membership-assign");
        assertThat(audit).contains("decisionReference").contains("confirmed.FULL_LEGAL_CAPACITY_REQUIRED")
                         .doesNotContain("ZK/12/2026");
    }

    @Test
    void fullLegalCapacityAppliesToTheStatutoryPositionsOnly() {
        TMember member = newMember("Test", "Zdolnosc", "mazowieckie");

        assertThat(warningsOf(member, body("audit-commission"), "CHAIR"))
            .containsExactly(FunctionWarning.Code.FULL_LEGAL_CAPACITY_REQUIRED);
        assertThat(warningsOf(member, body("peer-court"), "VICE_CHAIR"))
            .containsExactly(FunctionWarning.Code.FULL_LEGAL_CAPACITY_REQUIRED);
        assertThat(warningsOf(member, body("national-board"), "TREASURER"))
            .containsExactly(FunctionWarning.Code.FULL_LEGAL_CAPACITY_REQUIRED);
        assertThat(warningsOf(member, body("audit-commission"), "MEMBER")).isEmpty();
        assertThat(warningsOf(member, body("electoral-commission"), "CHAIR")).isEmpty();
    }

    @Test
    void districtBoardLargerThanArticle43AllowsIsAWarning() {
        TBody zo = board("mazowieckie");
        TMember first = newMember("Jedyny", "Zarzad", "mazowieckie");
        TMember second = newMember("Drugi", "Zarzad", "mazowieckie");
        grant(first, zo, "COORDINATOR", today.minusMonths(1), null);

        // fewer than 20 members in the district: a one-person board; a second person is more than art. 43 allows
        AssignMembership request = assign(second, zo, "MEMBER", today, null);
        AssignmentCheck check = service.check(request);

        assertThat(check.violations()).isEmpty();
        assertThat(check.warnings()).extracting(FunctionWarning::code)
                                    .containsExactly(FunctionWarning.Code.DISTRICT_BOARD_SIZE_EXCEEDS_STATUTE);
        assertThatThrownBy(() -> service.assign(request)).isInstanceOf(ConfirmationRequiredException.class);
        assertThat(service.assign(request.withConfirmedWarnings(
            Set.of(FunctionWarning.Code.DISTRICT_BOARD_SIZE_EXCEEDS_STATUTE))).membership().getId()).isNotNull();
    }

    @Test
    void theReportOfACompositionNeverBlocksAnything() {
        TBody zo = board("mazowieckie");
        newMember("Czlonek", "Okregu", "mazowieckie");

        assertThat(service.advisories(zo.getId())).extracting(CompositionAdvisory::code)
                                                  .containsExactly(CompositionAdvisory.Code.DISTRICT_BOARD_SIZE_MISMATCH);

        grant(newMember("Jedyny", "Koordynator", "mazowieckie"), zo, "COORDINATOR", today.minusMonths(1), null);

        assertThat(service.advisories(zo.getId())).isEmpty();
        assertThat(service.advisories(body("national-board").getId())).singleElement().satisfies(a -> {
            assertThat(a.code()).isEqualTo(CompositionAdvisory.Code.BODY_COMPOSITION_INCOMPLETE);
            assertThat(a.current()).isZero();
            assertThat(a.expected()).isEqualTo("7");
        });
    }

    @Test
    void aLocalCircleBelowSixMembersIsOnlyReported() {
        TBody circle = bodyService.create(new NewBody(template(BodyKind.LOCAL_CIRCLE).getId(), "Test Koło",
            null, district("mazowieckie").getId(), "test-circle"));
        IntStream.range(0, 5).forEach(i -> grant(newMember("Kolo" + i, "Czlonek", "mazowieckie"), circle, "MEMBER",
            today.minusMonths(1), null));

        assertThat(service.advisories(circle.getId())).singleElement().satisfies(a -> {
            assertThat(a.code()).isEqualTo(CompositionAdvisory.Code.LOCAL_CIRCLE_BELOW_MINIMUM);
            assertThat(a.current()).isEqualTo(5);
        });

        grant(newMember("Kolo5", "Czlonek", "mazowieckie"), circle, "MEMBER", today.minusMonths(1), null);

        assertThat(service.advisories(circle.getId())).isEmpty();
    }

    @Test
    void theDecisionReferenceIsTrimmedBlankMeansNoneAndLongOnesAreRejected() {
        TMember member = newMember("Uchwala", "Test", "mazowieckie");
        TBody team = bodyService.create(new NewBody(template(BodyKind.TEAM).getId(), "Test Zespół", null, null,
            "test-team"));

        assertThat(service.assign(assign(member, team, "MEMBER", today, null).withDecisionReference("  ZK/3/2026  "))
                          .membership().getDecisionReference()).isEqualTo("ZK/3/2026");

        TMember other = newMember("Bez", "Uchwaly", "mazowieckie");

        assertThat(service.assign(assign(other, team, "MEMBER", today, null).withDecisionReference("   "))
                          .membership().getDecisionReference()).isNull();

        TMember third = newMember("Zbyt", "Dluga", "mazowieckie");
        AssignMembership tooLong = assign(third, team, "MEMBER", today, null).withDecisionReference("x".repeat(201));

        assertThat(service.check(tooLong).violations()).extracting(FunctionViolation::code)
                                                       .containsExactly(FunctionViolation.Code.DECISION_REFERENCE_TOO_LONG);
    }

    private void fill(String bodyCode, BodyKind kind, int people) {
        IntStream.range(0, people).forEach(i -> grant(newMember("Sklad" + i, bodyCode.replace('-', 'x'), "mazowieckie"),
            body(bodyCode), "MEMBER", today.minusMonths(1), null));
    }

    private List<FunctionViolation.Code> violationsOf(TMember member, TBody body, String typeCode) {
        return violationsOf(member, body, typeCode, 0);
    }

    private List<FunctionViolation.Code> violationsOf(TMember member, TBody body, String typeCode, int endInDays) {
        return service.check(assign(member, body, typeCode, today, endInDays == 0 ? null : today.plusDays(endInDays)))
                      .violations().stream().map(FunctionViolation::code).toList();
    }

    private List<FunctionWarning.Code> warningsOf(TMember member, TBody body, String typeCode) {
        return service.check(assign(member, body, typeCode, today, null)).warnings().stream()
                      .map(FunctionWarning::code).toList();
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
}
