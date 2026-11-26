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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipChanged;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.main.web-application-type=none",
    "panel.admin.assign-function=national-board:SECRETARY",
    "panel.admin.autorun=false"
})
@PostgresTest
@Import(BreakGlassAssignerTest.EventCollector.class)
class BreakGlassAssignerTest extends DomainTestSupport {
    private static final String FUNCTION = "national-board:SECRETARY";

    @Autowired
    BreakGlassAssigner assigner;

    @Autowired
    EventCollector events;

    private final LocalDate today = LocalDate.now();

    TMember member;

    @BeforeEach
    void setUp() {
        events.events.clear();
        member = newMember("Beata", "Awaryjna", "mazowieckie");
        jdbc.update("UPDATE members SET member_id = '9001' WHERE id = ?", member.getId());
    }

    @Test
    void dryRunWritesNothingAndPublishesNothing() {
        BreakGlassReport report = run(FUNCTION, "9001", false);

        assertThat(report.exitCode()).isZero();
        assertThat(report.render()).contains("dry run", "would create");
        assertThat(membershipCount()).isZero();
        assertThat(auditCount("admin :: break-glass")).isZero();
        assertThat(events.events).isEmpty();
    }

    @Test
    void applyCreatesTheMembershipDespiteAnArt21ConflictAndEndsNothing() {
        TBodyMembership ksk = grant(member, body("peer-court"), "MEMBER", today.minusMonths(6), null);

        BreakGlassReport report = run(FUNCTION, "9001", true);

        assertThat(report.exitCode()).isZero();
        assertThat(report.render()).contains("created membership");
        assertThat(jdbc.queryForObject("SELECT valid_to FROM body_memberships WHERE id = ?", LocalDate.class,
            ksk.getId())).isNull();

        TBodyMembership created = bodyMembershipRepository.findAllByMemberIdAndBodyId(member.getId(),
            body("national-board").getId()).getFirst();

        assertThat(created.getValidFrom()).isEqualTo(today);
        assertThat(created.getValidTo()).isNull();
        assertThat(created.getMemberType().getCode()).isEqualTo("SECRETARY");
        assertThat(membershipCount()).isEqualTo(2);

        assertThat(events.events).singleElement().satisfies(event -> {
            assertThat(event.change()).isEqualTo(BodyMembershipChanged.Change.CREATED);
            assertThat(event.bodyMembershipId()).isEqualTo(created.getId());
            assertThat(event.memberId()).isEqualTo(member.getId());
        });

        assertThat(auditCount("admin :: break-glass")).isEqualTo(1);
        assertThat(auditPayloads("admin :: break-glass")).contains("Alice Operator", "lockout recovery",
            String.valueOf(created.getId()), String.valueOf(member.getId()),
            String.valueOf(body("national-board").getId()), String.valueOf(created.getMemberType().getId()));
    }

    @Test
    void recordsTheDecisionReferenceOnTheMembershipWithoutPrintingIt() {
        String reference = "Uchwała ZK nr 12/2026; zatwierdzona uchwałą RK nr 3/2026";

        BreakGlassReport report = assigner.run("national-it-proxy:PROXY", "9001", "Alice Operator",
            "lockout recovery", reference, true);

        assertThat(report.exitCode()).isZero();
        assertThat(report.render()).contains("Decision reference: recorded").doesNotContain("12/2026");

        TBodyMembership created = bodyMembershipRepository.findAllByMemberIdAndBodyId(member.getId(),
            body("national-it-proxy").getId()).getFirst();

        assertThat(created.getDecisionReference()).isEqualTo(reference);
        assertThat(created.getMemberType().getCode()).isEqualTo("PROXY");
        assertThat(auditPayloads("admin :: break-glass")).contains("decisionReference").doesNotContain("12/2026");
        assertThat(run(FUNCTION, "9001", false).render()).contains("Decision reference: none");
    }

    @Test
    void bootstrapsTheFirstNationalBoardMemberAndRepeatedApplyIsANoOp() {
        assertThat(run(FUNCTION, "9001", true).exitCode()).isZero();

        BreakGlassReport again = run(FUNCTION, "9001", true);

        assertThat(again.exitCode()).isZero();
        assertThat(again.render()).contains("already holds");
        assertThat(membershipCount()).isEqualTo(1);
        assertThat(auditCount("admin :: break-glass")).isEqualTo(1);
        assertThat(events.events).hasSize(1);
    }

    @Test
    void membersAreFoundByDomainEmailMemberIdOrDatabaseId() {
        assertThat(run(FUNCTION, member.getDomainEmail().toUpperCase(), false).render()).contains("would create");
        assertThat(run(FUNCTION, "9001", false).render()).contains("would create");
        assertThat(run(FUNCTION, "id:" + member.getId(), false).render()).contains("would create");
    }

    @Test
    void aDatabaseIdIsNeverUsedWithoutTheExplicitPrefix() {
        // F0-1: a number no member carries must not silently become a database id (a typo could
        // hand the global function to someone else); the operator has to write id:<n> on purpose.
        BreakGlassReport bare = run(FUNCTION, String.valueOf(member.getId()), false);

        assertThat(bare.exitCode()).isEqualTo(1);
        assertThat(bare.render()).contains("Unknown member").doesNotContain("resolved by database id");

        BreakGlassReport prefixed = run(FUNCTION, "id:" + member.getId(), false);

        assertThat(prefixed.render()).contains("resolved by database id", "would create");
        assertThat(prefixed.render()).doesNotContain(member.getDomainEmail());
    }

    @Test
    void aZeroPaddedNumberIsAlwaysAMemberNumberNeverADatabaseId() {
        // Stage after the register import: the owner's record is 000205, another member has database id 205.
        TMember other = newMember("Olga", "Inna", "slaskie");
        String padded = String.format("%06d", other.getId());
        jdbc.update("UPDATE members SET member_id = ? WHERE id = ?", padded, member.getId());

        BreakGlassReport report = run(FUNCTION, padded, true);

        assertThat(report.exitCode()).as(report.render()).isZero();
        assertThat(events.events).singleElement()
                                 .satisfies(event -> assertThat(event.memberId()).isEqualTo(member.getId()));
    }

    @Test
    void anUnpaddedNumberMatchesThePaddedRegisterNumber() {
        jdbc.update("UPDATE members SET member_id = '009001' WHERE id = ?", member.getId());

        assertThat(run(FUNCTION, "9001", false).render()).contains("would create");
        assertThat(run(FUNCTION, "0009001", false).render()).contains("would create");
    }

    @Test
    void anUnpaddedNumberNeverSilentlyPicksAnotherMembersDatabaseId() {
        TMember other = newMember("Olga", "Inna", "slaskie");
        jdbc.update("UPDATE members SET member_id = ? WHERE id = ?",
            String.format("%06d", other.getId()), member.getId());

        // Unpadded digits are a member number only: the member carrying it is the one resolved.
        BreakGlassReport report = run(FUNCTION, String.valueOf(other.getId()), false);

        assertThat(report.render()).contains("resolved by member number").doesNotContain("resolved by database id");
        assertThat(membershipCount()).isZero();
    }

    @Test
    void aNumberSharedByTwoRecordsIsRejected() {
        TMember other = newMember("Olga", "Inna", "slaskie");
        jdbc.update("UPDATE members SET member_id = '000777' WHERE id = ?", member.getId());
        jdbc.update("UPDATE members SET member_id = '777' WHERE id = ?", other.getId());

        BreakGlassReport report = run(FUNCTION, "000777", true);

        assertThat(report.exitCode()).isEqualTo(1);
        assertThat(report.render()).contains("Ambiguous");
        assertThat(membershipCount()).isZero();
    }

    @Test
    void operatorAndReasonAreRequired() {
        assertThat(assigner.run(FUNCTION, "9001", null, "why", true).exitCode()).isEqualTo(1);
        assertThat(assigner.run(FUNCTION, "9001", "  ", "why", true).exitCode()).isEqualTo(1);
        assertThat(assigner.run(FUNCTION, "9001", "who", null, true).exitCode()).isEqualTo(1);
        assertThat(assigner.run(FUNCTION, "9001", "who", "", true).exitCode()).isEqualTo(1);
        assertThat(membershipCount()).isZero();
        assertThat(auditCount("admin :: break-glass")).isZero();
    }

    @Test
    void unknownOrInvalidInputsExitWithProblemsAndWriteNothing() {
        for (String[] input : new String[][] {
            {"no-such-body:SECRETARY", "9001"},
            {"national-board:NO_SUCH_TYPE", "9001"},
            {"national-board:VICE_CHAIR", "9001"},
            {"national-board", "9001"},
            {"national-board:", "9001"},
            {FUNCTION, "424242"},
            {FUNCTION, "nobody@example.org"}
        }) {
            BreakGlassReport report = run(input[0], input[1], true);

            assertThat(report.exitCode()).as(String.join(" / ", input)).isEqualTo(1);
            assertThat(report.render()).contains("Nothing was written");
        }

        assertThat(membershipCount()).isZero();
        assertThat(events.events).isEmpty();
    }

    @Test
    void removedMembersCannotBeAssigned() {
        jdbc.update("UPDATE members SET status = 'REMOVED'::member_status WHERE id = ?", member.getId());

        BreakGlassReport report = run(FUNCTION, "9001", true);

        assertThat(report.exitCode()).isEqualTo(1);
        assertThat(report.render()).contains("REMOVED");
        assertThat(membershipCount()).isZero();
    }

    @Test
    void outputNeverContainsPersonalData() {
        String output = run(FUNCTION, "9001", true).render() + run("no-such-body:X", "9001", true).render();

        assertThat(output).doesNotContain("Beata", "Awaryjna", "example.org", "Alice Operator", "lockout recovery");
    }

    private BreakGlassReport run(String function, String identifier, boolean apply) {
        return assigner.run(function, identifier, "Alice Operator", "lockout recovery", apply);
    }

    private int membershipCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships", Integer.class);
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
