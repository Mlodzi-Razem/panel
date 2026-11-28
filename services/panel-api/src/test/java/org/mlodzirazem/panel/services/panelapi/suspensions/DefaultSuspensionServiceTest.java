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

package org.mlodzirazem.panel.services.panelapi.suspensions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.DomainException;
import org.mlodzirazem.panel.services.panelapi.members.SuspensionRegistry;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** G-10 service rules (art. 10 statutu): nothing closes a mandate on its own, history is kept, codes are stable. */
@SpringBootTest
@PostgresTest
class DefaultSuspensionServiceTest extends DomainTestSupport {
    private static final String SIGNATURE = "ZO/MZ/TEST/1";

    @Autowired
    SuspensionService service;

    @Autowired
    SuspensionRegistry registry;

    private TMember member;
    private TBody zo;
    private TBody zk;

    @BeforeEach
    void setUp() {
        member = newMember("Ada", "Arrears", "mazowieckie");
        zo = board("mazowieckie");
        zk = body("national-board");
    }

    private SuspendMember request(SuspensionKind kind, SuspensionScope scope, long bodyId, LocalDate start,
                                  LocalDate end, Set<String> confirmed) {
        // data uchwały (W4): one day before the start, the same day when the start is today
        LocalDate decisionDate = kind == SuspensionKind.VOLUNTARY
            ? null
            : (start != null ? start : LocalDate.now()).minusDays(1);
        return new SuspendMember(member.getId(), kind, scope, null, bodyId, start, end, decisionDate, SIGNATURE,
            confirmed);
    }

    @Test
    void warningsBlockUntilConfirmedAndThenTheEntryIsWritten() {
        grant(member, zo, "MEMBER", LocalDate.now().minusYears(1), null);
        SuspendMember unconfirmed = request(SuspensionKind.ARREARS, SuspensionScope.FULL, zo.getId(), null, null, Set.of());

        assertThatThrownBy(() -> service.suspend(unconfirmed))
            .isInstanceOfSatisfying(SuspensionConfirmationRequiredException.class, e -> assertThat(e.getCheck().warnings())
                .extracting(SuspensionWarning::code)
                .contains("ARREARS_BELOW_THRESHOLD", "MANDATES_WILL_BE_SUSPENDED"));
        assertThat(service.historyOf(member.getId())).isEmpty();

        SuspensionResult result = service.suspend(request(SuspensionKind.ARREARS, SuspensionScope.FULL, zo.getId(), null,
            null, Set.of("ARREARS_BELOW_THRESHOLD", "MANDATES_WILL_BE_SUSPENDED")));

        assertThat(result.suspension().kind()).isEqualTo(SuspensionKind.ARREARS);
        assertThat(result.positionLoss().applicable()).isTrue();
        assertThat(result.positionLoss().dueOn()).isNull();
        assertThat(registry.competencesSuspended(member.getId(), LocalDate.now())).isTrue();
        // art. 10 ust. 3: the mandate stays open; only an explicit end with a resolution closes it
        assertThat(bodyMembershipRepository.findAllByMemberId(member.getId())).allMatch(m -> m.getValidTo() == null);
        assertThat(auditCount("suspensions :: suspend")).isEqualTo(1);
        assertThat(auditPayloads("suspensions :: suspend")).doesNotContain(SIGNATURE)
            .contains("decisionSignature", "confirmed.MANDATES_WILL_BE_SUSPENDED");
    }

    @Test
    void sameKindAndScopeOverlapIsRefusedWhileOtherScopeIsFine() {
        service.suspend(request(SuspensionKind.ARREARS, SuspensionScope.FULL, zo.getId(), null, null,
            Set.of("ARREARS_BELOW_THRESHOLD")));

        assertThatThrownBy(() -> service.suspend(request(SuspensionKind.ARREARS, SuspensionScope.FULL, zo.getId(), null,
            null, Set.of("ARREARS_BELOW_THRESHOLD"))))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("SUSPENSION_ALREADY_ACTIVE"));
    }

    @Test
    void onlyTheCompetentBodySuspends() {
        assertThatThrownBy(() -> service.suspend(request(SuspensionKind.ARREARS, SuspensionScope.FULL, zk.getId(), null,
            null, Set.of("ARREARS_BELOW_THRESHOLD"))))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("SUSPENDING_BODY_NOT_COMPETENT"));
    }

    @Test
    void passiveSuspensionProposesTheLossButEndsMandatesOnlyWhenAskedWithAResolution() {
        grant(member, zo, "MEMBER", LocalDate.now().minusDays(200), null);
        long mandateId = bodyMembershipRepository.findAllByMemberId(member.getId()).getFirst().getId();
        LocalDate start = LocalDate.now().minusDays(70);

        SuspensionResult result = service.suspend(request(SuspensionKind.BY_ORGAN, SuspensionScope.PASSIVE_ELECTORAL,
            zo.getId(), start, null, Set.of("MANDATES_WILL_BE_SUSPENDED")));

        PositionLoss proposal = result.positionLoss();
        assertThat(proposal.due()).isTrue();
        assertThat(proposal.dueOn()).isEqualTo(start.plusDays(62));
        assertThat(proposal.outstanding()).extracting(MandateRef::membershipId).containsExactly(mandateId);
        assertThat(bodyMembershipRepository.findAllByMemberId(member.getId())).allMatch(m -> m.getValidTo() == null);

        long suspensionId = result.suspension().id();
        assertThatThrownBy(() -> service.endMandates(suspensionId,
            new EndSuspendedMandates(Set.of(mandateId + 1000), SIGNATURE)))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("MANDATE_NOT_IN_PROPOSAL"));

        PositionLoss after = service.endMandates(suspensionId, new EndSuspendedMandates(Set.of(mandateId), "UCH/1/2026"));

        assertThat(after.endedCount()).isEqualTo(1);
        assertThat(after.outstanding()).isEmpty();
        assertThat(bodyMembershipRepository.findAllByMemberId(member.getId()).getFirst().getValidTo())
            .isEqualTo(start.plusDays(62));
        assertThatThrownBy(() -> service.voidSuspension(suspensionId, "omyłka", null))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("SUSPENSION_POSITION_LOSS_RECORDED"));
    }

    @Test
    void voluntarySuspensionStartsAtOnceAndItsAppealDateIsTheStart() {
        SuspensionResult result = service.suspend(request(SuspensionKind.VOLUNTARY, SuspensionScope.FULL, zk.getId(),
            null, null, Set.of()));

        assertThat(result.suspension().appealEndedOn()).isEqualTo(LocalDate.now());
        assertThat(result.suspension().appealEndedReference()).isEqualTo(SIGNATURE);
        assertThat(result.positionLoss().dueOn()).isEqualTo(LocalDate.now());
        assertThatThrownBy(() -> service.recordAppealEnded(result.suspension().id(), LocalDate.now(), SIGNATURE))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("APPEAL_NOT_APPLICABLE"));
    }

    @Test
    void liftNeedsACompetentBodyAndIsWriteOnce() {
        long id = service.suspend(request(SuspensionKind.ARREARS, SuspensionScope.FULL, zo.getId(), null, null,
            Set.of("ARREARS_BELOW_THRESHOLD"))).suspension().id();

        assertThatThrownBy(() -> service.lift(id, new LiftSuspension(null, "spłacono", zk.getId(), "ZK/1", Set.of())))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("SUSPENDING_BODY_NOT_COMPETENT"));

        Suspension lifted = service.lift(id, new LiftSuspension(null, "spłacono", zo.getId(), "ZO/2/TEST", Set.of()));

        assertThat(lifted.state(LocalDate.now())).isEqualTo(SuspensionState.LIFTED);
        assertThat(registry.competencesSuspended(member.getId(), LocalDate.now())).isFalse();
        assertThatThrownBy(() -> service.lift(id, new LiftSuspension(null, "again", zo.getId(), "ZO/3", Set.of())))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("SUSPENSION_ALREADY_LIFTED"));
    }

    @Test
    void theOnlyBodiesManagerCannotBeSuspended() {
        grant(member, zk, "SECRETARY");

        assertThatThrownBy(() -> service.suspend(request(SuspensionKind.VOLUNTARY, SuspensionScope.FULL, zk.getId(),
            null, null, Set.of())))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("LAST_BODIES_MANAGER"));
    }

    @Test
    void decisionDateIsRequiredForAResolutionAndCheckedAgainstTheStart() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> service.suspend(new SuspendMember(member.getId(), SuspensionKind.BY_ORGAN,
            SuspensionScope.FULL, null, zo.getId(), null, null, null, SIGNATURE, Set.of())))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("DECISION_DATE_REQUIRED"));

        assertThatThrownBy(() -> service.suspend(new SuspendMember(member.getId(), SuspensionKind.VOLUNTARY,
            SuspensionScope.FULL, null, zk.getId(), null, null, today, SIGNATURE, Set.of())))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("DECISION_DATE_NOT_ALLOWED"));

        assertThatThrownBy(() -> service.suspend(new SuspendMember(member.getId(), SuspensionKind.BY_ORGAN,
            SuspensionScope.FULL, null, zo.getId(), today.minusDays(10), null, today.minusDays(5), SIGNATURE, Set.of())))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("DECISION_DATE_AFTER_START"));

        assertThatThrownBy(() -> service.suspend(new SuspendMember(member.getId(), SuspensionKind.BY_ORGAN,
            SuspensionScope.FULL, null, zo.getId(), today, null, today.plusDays(1), SIGNATURE, Set.of())))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getCode()).isEqualTo("DECISION_DATE_IN_FUTURE"));
    }

    @Test
    void theResolutionDateIsStoredAndTheAppealHintIsCountedFromItForAFutureStart() {
        LocalDate decided = LocalDate.now().minusDays(3);
        LocalDate start = LocalDate.now().plusDays(5);

        Suspension created = service.suspend(new SuspendMember(member.getId(), SuspensionKind.ARREARS,
            SuspensionScope.FULL, null, zo.getId(), start, null, decided, SIGNATURE,
            Set.of("ARREARS_BELOW_THRESHOLD"))).suspension();

        assertThat(created.decisionDate()).isEqualTo(decided);
        assertThat(created.start()).isEqualTo(start);
        assertThat(created.state(LocalDate.now())).isEqualTo(SuspensionState.SCHEDULED);
        assertThat(service.get(created.id()).decisionDate()).isEqualTo(decided);
        assertThat(auditPayloads("suspensions :: suspend")).contains("decisionDate");
    }
}
