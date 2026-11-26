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
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class LastBodiesManagerTest extends DomainTestSupport {
    @Autowired
    BodyMembershipService service;

    private final LocalDate today = LocalDate.now();

    TMember last;
    TBodyMembership lastSeat;

    @BeforeEach
    void setUp() {
        last = newMember("Lena", "Last", "mazowieckie");
        lastSeat = grant(last, body("national-board"), "SECRETARY");
    }

    @Test
    void movingTheLastManagerIntoKskIsBlockedAsIncompatibleNotAsLastManager() {
        AssignMembership request = toKsk(last);

        AssignmentCheck check = service.check(request);

        assertThat(check.conflicts()).isEmpty();
        assertThat(check.violations()).extracting(FunctionViolation::code)
                                      .containsExactly(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);

        assertThatThrownBy(() -> service.assign(request, true))
            .isInstanceOf(AssignmentBlockedException.class)
            .extracting(e -> ((AssignmentBlockedException) e).getCode())
            .isEqualTo("INCOMPATIBLE_MEMBERSHIP");
        assertThat(validTo(lastSeat)).isNull();
        assertThat(membershipCount(last)).isEqualTo(1);
        assertThatThrownBy(() -> service.end(lastSeat.getId(), null, null)).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void aSuccessorAllowsEndingTheLastMandateButNotAnIncompatibleMove() {
        TMember successor = newMember("Sven", "Successor", "slaskie");

        service.assign(new AssignMembership(successor.getId(), body("national-board").getId(),
            memberType(BodyKind.NATIONAL_BOARD, "MEMBER").getId(), today, null), false);

        assertThat(service.check(toKsk(last)).violations())
            .extracting(FunctionViolation::code)
            .containsExactly(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);

        service.end(lastSeat.getId(), null, null);
        assertThat(validTo(lastSeat)).isEqualTo(today);
        assertThat(service.check(toKsk(last)).violations()).isEmpty();
    }

    @Test
    void successorThatIsRemovedNotOpenEndedOrNotYetActiveDoesNotCount() {
        TMember removed = newMember("Rita", "Removed", "slaskie");
        grant(removed, body("national-board"), "MEMBER");
        jdbc.update("UPDATE members SET status = 'REMOVED'::member_status WHERE id = ?", removed.getId());

        TMember fixedTerm = newMember("Fred", "Fixed", "slaskie");
        grant(fixedTerm, body("national-board"), "CO_CHAIR", today.minusMonths(1), today.plusMonths(5));

        TMember future = newMember("Fiona", "Future", "slaskie");
        grant(future, body("national-board"), "TREASURER", today.plusDays(10), null);

        // The move to KSK is blocked by the statute first: an incompatible mandate is never
        // silently closed anymore, so the check reports INCOMPATIBLE_MEMBERSHIP (not the guard).
        assertThat(service.check(toKsk(last)).violations()).extracting(FunctionViolation::code)
                                                           .containsExactly(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
        // The last-manager guard still ignores successors that are removed, not open-ended or
        // not yet active — ending the last mandate stays refused and nothing is mutated.
        assertLastManagerRefusal(() -> service.end(lastSeat.getId(), null, null));
        assertThat(validTo(lastSeat)).isNull();
        assertThat(auditCount("bodies :: membership-end")).isZero();
    }

    @Test
    void endingTheLastManagerIsRefusedNowAndWithAFutureDate() {
        assertLastManagerRefusal(() -> service.end(lastSeat.getId(), null, null));
        assertLastManagerRefusal(() -> service.end(lastSeat.getId(), today.plusDays(30), null));
        assertThat(validTo(lastSeat)).isNull();
        assertThat(auditCount("bodies :: membership-end")).isZero();
    }

    @Test
    void endingOneOfTwoManagersIsAllowed() {
        TMember other = newMember("Olek", "Other", "slaskie");
        TBodyMembership otherSeat = grant(other, body("national-board"), "CO_CHAIR");

        service.end(lastSeat.getId(), null, null);
        assertThat(validTo(lastSeat)).isEqualTo(today);

        assertLastManagerRefusal(() -> service.end(otherSeat.getId(), today.plusDays(1), null));
    }

    @Test
    void twoManagersEndingEachOtherConcurrentlyCannotBothSucceed() throws Exception {
        TMember other = newMember("Olek", "Other", "slaskie");
        TBodyMembership otherSeat = grant(other, body("national-board"), "CO_CHAIR");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Future<Boolean>> results = List.of(
                pool.submit(ending(lastSeat, start)),
                pool.submit(ending(otherSeat, start))
            );

            start.countDown();

            long succeeded = 0;

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE valid_to IS NULL",
                Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void endingANonManagerSeatIsNeverBlocked() {
        TMember ksk = newMember("Kasia", "Court", "slaskie");
        TBodyMembership seat = grant(ksk, body("peer-court"), "MEMBER");

        service.end(seat.getId(), null, null);

        assertThat(validTo(seat)).isEqualTo(today);
    }

    @Test
    void removingTheLastManagerIsRefusedButOtherStatusChangesAreNot() {
        assertLastManagerRefusal(() -> memberService.update(last.getId(), Map.of(MemberField.STATUS, MemberStatus.REMOVED)));
        assertThat(jdbc.queryForObject("SELECT status::text FROM members WHERE id = ?", String.class, last.getId()))
            .isEqualTo("ACTIVE");

        memberService.update(last.getId(), Map.of(MemberField.STATUS, MemberStatus.SUSPENDED));

        TMember other = newMember("Olek", "Other", "slaskie");
        grant(other, body("national-board"), "CO_CHAIR");

        memberService.update(last.getId(), Map.of(MemberField.STATUS, MemberStatus.REMOVED));

        assertThat(jdbc.queryForObject("SELECT status::text FROM members WHERE id = ?", String.class, last.getId()))
            .isEqualTo("REMOVED");
        assertLastManagerRefusal(() -> memberService.update(other.getId(),
            Map.of(MemberField.STATUS, MemberStatus.REMOVED)));
    }

    @Test
    void guardDoesNotBlockWhenNobodyHoldsThePermissionToBeginWith() {
        jdbc.execute("DELETE FROM body_memberships");

        TMember ksk = newMember("Kasia", "Court", "slaskie");
        grant(ksk, body("peer-court"), "MEMBER", today.minusMonths(6), null);

        assertThat(service.check(toKsk(ksk, "CHAIR")).violations()).isEmpty();

        // Nobody holds BODIES_MANAGE, so nothing blocks creating the first manager (bootstrap).
        TMember bootstrap = newMember("Borys", "Bootstrap", "slaskie");
        service.assign(new AssignMembership(bootstrap.getId(), body("national-board").getId(),
            memberType(BodyKind.NATIONAL_BOARD, "SECRETARY").getId(), today, null), false);
        assertThat(membershipCount(bootstrap)).isEqualTo(1);

        // Kasia holding an open KSK seat makes a cross-body ZK assignment a statutory violation
        // (never auto-closed anymore) — but the guard itself does not add a LAST_BODIES_MANAGER.
        assertThat(service.check(new AssignMembership(ksk.getId(), body("national-board").getId(),
            memberType(BodyKind.NATIONAL_BOARD, "SECRETARY").getId(), today, null)).violations())
            .extracting(FunctionViolation::code)
            .containsExactly(FunctionViolation.Code.INCOMPATIBLE_MEMBERSHIP);
    }

    private Callable<Boolean> ending(TBodyMembership seat, CountDownLatch start) {
        return () -> {
            start.await();

            try {
                service.end(seat.getId(), null, null);
                return true;
            } catch (RuleViolationException e) {
                if (!"LAST_BODIES_MANAGER".equals(e.getCode())) {
                    throw e;
                }

                return false;
            }
        };
    }

    private AssignMembership toKsk(TMember member) {
        return toKsk(member, "MEMBER");
    }

    private AssignMembership toKsk(TMember member, String typeCode) {
        return new AssignMembership(member.getId(), body("peer-court").getId(),
            memberType(BodyKind.PEER_COURT, typeCode).getId(), today, null);
    }

    private void assertLastManagerRefusal(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(RuleViolationException.class)
            .extracting(e -> ((RuleViolationException) e).getCode())
            .isEqualTo("LAST_BODIES_MANAGER");
    }

    private LocalDate validTo(TBodyMembership membership) {
        return jdbc.queryForObject("SELECT valid_to FROM body_memberships WHERE id = ?", LocalDate.class,
            membership.getId());
    }

    private int membershipCount(TMember member) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE member_id = ?", Integer.class,
            member.getId());
    }
}
