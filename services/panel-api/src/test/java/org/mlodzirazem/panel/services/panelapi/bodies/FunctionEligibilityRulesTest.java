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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Who may hold a function in an organ at all: hard violations that no confirmation lifts. */
@SpringBootTest
@PostgresTest
class FunctionEligibilityRulesTest extends DomainTestSupport {
    @Autowired
    BodyMembershipService service;

    @Autowired
    BodyService bodyService;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void cleanSuspensions() {
        jdbc.execute("DELETE FROM suspensions");
    }

    @Test
    void anActiveOrdinaryMemberIsEligible() {
        assertThat(codes(newMember("Ola", "Zwyczajna", "mazowieckie"), board("mazowieckie"), today, null)).isEmpty();
    }

    @Test
    void supportingAndHonoraryMembersAreRefusedAndNothingIsWritten() {
        for (String type : List.of("SUPPORTING", "HONORARY")) {
            TMember member = withType(newMember("Inny", type, "mazowieckie"), type);
            AssignMembership request = request(member, board("mazowieckie"), "COORDINATOR", today, null)
                .withConfirmedWarnings(Set.of(FunctionWarning.Code.values()));

            assertThat(service.check(request).violations()).extracting(FunctionViolation::code)
                .containsExactly(FunctionViolation.Code.FUNCTION_REQUIRES_ORDINARY_MEMBERSHIP);
            assertThatThrownBy(() -> service.assign(request)).isInstanceOf(AssignmentBlockedException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE member_id = ?", Integer.class,
                member.getId())).isZero();
        }
    }

    @Test
    void theInitialPeriodAndASuspendedStatusBlock() {
        for (String status : List.of("PROBATION", "SUSPENDED")) {
            TMember member = withStatus(newMember("Stan", status, "mazowieckie"), status);

            assertThat(codes(member, board("mazowieckie"), today, null))
                .containsExactly(FunctionViolation.Code.FUNCTION_REQUIRES_ACTIVE_MEMBER);
        }
    }

    @Test
    void aSuspensionOverlappingTheMandateBlocks() {
        TMember member = newMember("Zawieszona", "Okresowo", "mazowieckie");
        suspend(member, today.plusDays(10), today.plusDays(20), null);

        assertThat(codes(member, board("mazowieckie"), today, null))
            .containsExactly(FunctionViolation.Code.MEMBER_SUSPENDED_IN_PERIOD);
        assertThat(codes(member, board("mazowieckie"), today, today.plusDays(11)))
            .containsExactly(FunctionViolation.Code.MEMBER_SUSPENDED_IN_PERIOD);
        assertThat(codes(member, board("mazowieckie"), today.plusDays(19), today.plusDays(40)))
            .containsExactly(FunctionViolation.Code.MEMBER_SUSPENDED_IN_PERIOD);
        // both periods are half open: touching is not overlapping
        assertThat(codes(member, board("mazowieckie"), today, today.plusDays(10))).isEmpty();
        assertThat(codes(member, board("mazowieckie"), today.plusDays(20), null)).isEmpty();
    }

    @Test
    void aLiftedSuspensionCountsOnlyUntilTheDayItWasLifted() {
        TMember member = newMember("Uchylona", "Kara", "mazowieckie");
        suspend(member, today.minusDays(30), null, today.minusDays(5));

        assertThat(codes(member, board("mazowieckie"), today, null)).isEmpty();
        assertThat(codes(member, board("mazowieckie"), today.minusDays(10), today))
            .containsExactly(FunctionViolation.Code.MEMBER_SUSPENDED_IN_PERIOD);

        TMember never = newMember("Cofnieta", "Odrazu", "mazowieckie");
        suspend(never, today.minusDays(3), null, today.minusDays(3));

        assertThat(codes(never, board("mazowieckie"), today.minusDays(30), null)).isEmpty();
    }

    @Test
    void aMandateCannotStartAfterTheOrdinaryMembershipEnded() {
        TMember member = newMember("Rocznik", "Koncowy", "mazowieckie");
        LocalDate lastDay = LocalDate.of(2029, 12, 31); // born 2003 (art. 11 ust. 1 pkt 5)

        assertThat(codes(member, board("mazowieckie"), lastDay, null)).isEmpty();
        assertThat(codes(member, board("mazowieckie"), lastDay.plusDays(1), null))
            .containsExactly(FunctionViolation.Code.MEMBERSHIP_ENDED_BEFORE_MANDATE);
    }

    @Test
    void everyOrganInScopeIsCovered() {
        TMember member = withType(newMember("Wspierajaca", "Wszedzie", "mazowieckie"), "SUPPORTING");
        TBody commissioners = bodyService.create(new NewBody(template(BodyKind.DISTRICT_COMMISSIONER).getId(),
            "Test Komisarz", null, district("mazowieckie").getId(), "test-commissioner-eligibility"));

        for (TBody organ : List.of(body("national-board"), body("peer-court"), body("audit-commission"),
            body("electoral-commission"), board("mazowieckie"), commissioners)) {
            String kind = jdbc.queryForObject("SELECT t.kind::text FROM bodies b JOIN body_templates t "
                + "ON t.id = b.body_template_id WHERE b.id = ?", String.class, organ.getId());
            String code = switch (kind) {
                case "DISTRICT_COMMISSIONER" -> "COMMISSIONER";
                case "DISTRICT_BOARD" -> "COORDINATOR";
                default -> "MEMBER";
            };
            assertThat(service.check(request(member, organ, code, today, today.plusDays(30))).violations())
                .as(organ.getName())
                .extracting(FunctionViolation::code)
                .contains(FunctionViolation.Code.FUNCTION_REQUIRES_ORDINARY_MEMBERSHIP);
        }
    }

    @Test
    void bodiesOutsideTheScopeAreNotAffected() {
        TMember member = withStatus(withType(newMember("Poza", "Zakresem", "mazowieckie"), "SUPPORTING"), "SUSPENDED");
        suspend(member, today.minusDays(1), null, null);
        TBody team = bodyService.create(new NewBody(template(BodyKind.TEAM).getId(), "Test Zespół", null, null,
            "test-team-eligibility"));

        assertThat(service.check(request(member, team, "MEMBER", today, null)).violations()).isEmpty();
        assertThat(service.check(request(member, body("national-council"), "ZK_MEMBER", today, null)).violations())
            .isEmpty();
        assertThat(service.check(request(member, body("national-it-proxy"), "PROXY", today, null)).violations())
            .isEmpty();
    }

    private List<FunctionViolation.Code> codes(TMember member, TBody zo, LocalDate from, LocalDate to) {
        return service.check(request(member, zo, "COORDINATOR", from, to)).violations().stream()
                      .map(FunctionViolation::code).toList();
    }

    private AssignMembership request(TMember member, TBody body, String typeCode, LocalDate from, LocalDate to) {
        long typeId = jdbc.queryForObject("SELECT mt.id FROM body_template_member_types mt JOIN bodies b "
            + "ON b.body_template_id = mt.body_template_id WHERE b.id = ? AND mt.code = ?", Long.class,
            body.getId(), typeCode);

        return new AssignMembership(member.getId(), body.getId(), typeId, from, to);
    }

    private TMember withType(TMember member, String type) {
        jdbc.update("UPDATE members SET membership_type = ?::membership_type WHERE id = ?", type, member.getId());
        return member;
    }

    private TMember withStatus(TMember member, String status) {
        jdbc.update("UPDATE members SET status = ?::member_status WHERE id = ?", status, member.getId());
        return member;
    }

    private void suspend(TMember member, LocalDate start, LocalDate end, LocalDate liftedAt) {
        jdbc.update("INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start, "
            + "suspension_end, lifted_at, lifted_reason) VALUES (?, ?, ?, ?, ?, ?)", member.getId(),
            body("national-board").getId(), start, end, liftedAt, liftedAt == null ? null : "test");
    }
}
