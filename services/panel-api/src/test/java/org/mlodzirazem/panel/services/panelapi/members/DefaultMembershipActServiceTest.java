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

package org.mlodzirazem.panel.services.panelapi.members;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.InvalidInputException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipActKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipRemovalGround;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberMembershipAct;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class DefaultMembershipActServiceTest extends DomainTestSupport {
    @Autowired
    MembershipActService acts;

    private final LocalDate today = LocalDate.now();

    @Test
    void supportingGrantNeedsSixteenYearsAtTheGrantDateAndLeavesAnActBehind() {
        assertThatThrownBy(() -> acts.grantSupporting(supporting(today.minusYears(15), "UCHW-S0")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");

        TMember supporter = acts.grantSupporting(supporting(today.minusYears(16), "UCHW-S1"));

        assertThat(supporter.getMembershipType()).isEqualTo(MembershipType.SUPPORTING);
        assertThat(supporter.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(acts.history(supporter.getId())).singleElement().satisfies(act -> {
            assertThat(act.getActKind()).isEqualTo(MembershipActKind.SUPPORTING_GRANT);
            assertThat(act.getDecisionReference()).isEqualTo("UCHW-S1");
        });
    }

    @Test
    void honoraryGrantOfANewPersonHasNoAgeLimit() {
        TMember honorary = acts.grantHonorary(new MembershipActService.GrantHonorary(
            new NewMember(person("Stary", "Czlonek", today.minusYears(40)), district("mazowieckie").getId()),
            today, "UCHW-H0"));

        assertThat(honorary.getMembershipType()).isEqualTo(MembershipType.HONORARY);
        assertThat(honorary.getMemberSince()).isEqualTo(today);
        assertThat(acts.history(honorary.getId())).singleElement()
                                                  .extracting(TMemberMembershipAct::getActKind)
                                                  .isEqualTo(MembershipActKind.HONORARY_GRANT);
    }

    @Test
    void honoraryGrantToAMemberWithAnOpenMandateIsRefusedAndNothingIsEndedSilently() {
        TMember member = newMember("Ola", "Mandat", "mazowieckie");
        grant(member, board("mazowieckie"), "COORDINATOR");

        assertThatThrownBy(() -> acts.grantHonoraryToMember(
            new MembershipActService.GrantHonoraryToMember(member.getId(), today, "UCHW-H1")))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("HAS_OPEN_MANDATES");

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getMembershipType())
            .isEqualTo(MembershipType.ORDINARY);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM body_memberships WHERE member_id = ? AND valid_to IS NULL", Integer.class,
            member.getId())).isEqualTo(1);
        assertThat(acts.history(member.getId())).isEmpty();
    }

    @Test
    void honoraryGrantToAnActiveMemberWithoutMandatesIsRecorded() {
        TMember member = newMember("Ola", "Bez", "mazowieckie");

        acts.grantHonoraryToMember(new MembershipActService.GrantHonoraryToMember(member.getId(), today, "UCHW-H2"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getMembershipType())
            .isEqualTo(MembershipType.HONORARY);
        assertThat(acts.history(member.getId())).singleElement()
                                                .extracting(TMemberMembershipAct::getActKind)
                                                .isEqualTo(MembershipActKind.HONORARY_GRANT);
    }

    @Test
    void removalSetsStatusAndDateAndKeepsThePreviousStateInTheAct() {
        TMember member = newMember("Dan", "Zmarly", "mazowieckie");

        TMemberMembershipAct act = acts.remove(new MembershipActService.Remove(member.getId(),
            MembershipRemovalGround.DEATH, today, "UCHW-R1"));

        TMember loaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(loaded.getRemovedAt()).isEqualTo(today);
        assertThat(act.getRemovalGround()).isEqualTo(MembershipRemovalGround.DEATH);
        assertThat(act.getPreviousStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(act.getPreviousMembershipType()).isEqualTo(MembershipType.ORDINARY);
        assertThat(auditPayloads("members :: membership-removal"))
            .contains("removedAt")
            .doesNotContain("DEATH")
            .doesNotContain("UCHW-R1");
    }

    @Test
    void removingTwiceIsRefused() {
        TMember member = newMember("Eva", "Podwojnie", "mazowieckie");
        acts.remove(new MembershipActService.Remove(member.getId(), MembershipRemovalGround.RESIGNATION, today,
            "UCHW-R2"));

        assertThatThrownBy(() -> acts.remove(new MembershipActService.Remove(member.getId(),
            MembershipRemovalGround.RESIGNATION, today, "UCHW-R3")))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("ALREADY_REMOVED");
    }

    @Test
    void groundsAreTiedToTheMembershipType() {
        TMember supporter = acts.grantSupporting(supporting(today.minusYears(30), "UCHW-S2"));
        TMember ordinary = newMember("Ola", "Zwykla", "mazowieckie");

        assertThatThrownBy(() -> acts.remove(new MembershipActService.Remove(supporter.getId(),
            MembershipRemovalGround.AGE_26, today, "UCHW-R4")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("GROUND_NOT_APPLICABLE");

        assertThatThrownBy(() -> acts.remove(new MembershipActService.Remove(ordinary.getId(),
            MembershipRemovalGround.HONORARY_REVOKED, today, "UCHW-R5")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("GROUND_NOT_APPLICABLE");
    }

    @Test
    void honoraryMembershipIsRevokedByARemovalWithItsOwnGround() {
        TMember honorary = acts.grantHonorary(new MembershipActService.GrantHonorary(
            new NewMember(person("Hon", "Odebrany", today.minusYears(50)), district("mazowieckie").getId()),
            today, "UCHW-H3"));

        acts.remove(new MembershipActService.Remove(honorary.getId(), MembershipRemovalGround.HONORARY_REVOKED,
            today, "UCHW-WZOC-7"));

        TMember loaded = memberRepository.findById(honorary.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(loaded.getMembershipType()).isEqualTo(MembershipType.HONORARY);
    }

    @Test
    void removalCannotBeDatedInTheFuture() {
        TMember member = newMember("Fay", "Przyszla", "mazowieckie");

        assertThatThrownBy(() -> acts.remove(new MembershipActService.Remove(member.getId(),
            MembershipRemovalGround.RESIGNATION, today.plusDays(1), "UCHW-R6")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("DATE_IN_FUTURE");
    }

    @Test
    void reinstatementNeedsAReasonAndKeepsTheRemovalRow() {
        TMember member = newMember("Gosia", "Wznow", "mazowieckie");
        acts.remove(new MembershipActService.Remove(member.getId(), MembershipRemovalGround.RESIGNATION, today,
            "UCHW-R7"));

        assertThatThrownBy(() -> acts.reinstate(new MembershipActService.Reinstate(member.getId(), today,
            "UCHW-W0", " ")))
            .isInstanceOf(InvalidInputException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        acts.reinstate(new MembershipActService.Reinstate(member.getId(), today, "UCHW-W1", "pomyłka w uchwale"));

        TMember loaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(loaded.getRemovedAt()).isNull();
        assertThat(acts.history(member.getId()))
            .extracting(TMemberMembershipAct::getActKind)
            .containsExactly(MembershipActKind.REINSTATEMENT, MembershipActKind.REMOVAL);
    }

    @Test
    void reinstatementOfAMemberWhoIsNotRemovedIsRefused() {
        TMember member = newMember("Hania", "Aktywna", "mazowieckie");

        assertThatThrownBy(() -> acts.reinstate(new MembershipActService.Reinstate(member.getId(), today,
            "UCHW-W2", "powód")))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("MEMBER_NOT_REMOVED");
    }

    @Test
    void decisionReferenceIsRequiredAndBounded() {
        TMember member = newMember("Ida", "Sygnatura", "mazowieckie");

        assertThatThrownBy(() -> acts.remove(new MembershipActService.Remove(member.getId(),
            MembershipRemovalGround.RESIGNATION, today, "  ")))
            .isInstanceOf(InvalidInputException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        assertThatThrownBy(() -> acts.remove(new MembershipActService.Remove(member.getId(),
            MembershipRemovalGround.RESIGNATION, today, "x".repeat(201))))
            .isInstanceOf(InvalidInputException.class)
            .extracting("code").isEqualTo("INVALID_VALUE");
    }

    @Test
    void plainUpdateCannotRemoveHonorOrChangeTheRemovalDate() {
        TMember member = newMember("Jan", "Zwykly", "mazowieckie");

        assertThatThrownBy(() -> memberService.update(member.getId(), Map.of(MemberField.STATUS, MemberStatus.REMOVED)))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("MEMBERSHIP_ACT_REQUIRED");

        assertThatThrownBy(() -> memberService.update(member.getId(),
            Map.of(MemberField.MEMBERSHIP_TYPE, MembershipType.HONORARY)))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("MEMBERSHIP_ACT_REQUIRED");

        assertThatThrownBy(() -> memberService.update(member.getId(),
            Map.of(MemberField.MEMBERSHIP_TYPE, MembershipType.SUPPORTING)))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("MEMBERSHIP_ACT_REQUIRED");

        assertThatThrownBy(() -> memberService.update(member.getId(),
            Map.of(MemberField.REMOVED_AT, today)))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("MEMBERSHIP_ACT_REQUIRED");

        memberService.update(member.getId(), Map.of(MemberField.PREFERRED_NAME_FIRST, "Nowe"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getPreferredNameFirst()).isEqualTo("Nowe");
    }

    @Test
    void actsAreAppendOnlyInTheDatabase() {
        TMember member = newMember("Kasia", "Rejestr", "mazowieckie");
        TMemberMembershipAct act = acts.remove(new MembershipActService.Remove(member.getId(),
            MembershipRemovalGround.RESIGNATION, today, "UCHW-R8"));

        assertThatThrownBy(() -> jdbc.update(
            "UPDATE member_membership_acts SET decision_reference = 'PODMIANA' WHERE id = ?", act.getId()))
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM member_membership_acts WHERE id = ?", act.getId()))
            .hasMessageContaining("append-only");
    }

    @Test
    void reinstatementAfterHonoraryRevocationRestoresTheTypeFromBeforeTheHonoraryGrant() {
        TMember member = newMember("Hala", "Przywrot", "mazowieckie");
        acts.grantHonoraryToMember(new MembershipActService.GrantHonoraryToMember(member.getId(), today, "UCHW-H4"));
        acts.remove(new MembershipActService.Remove(member.getId(), MembershipRemovalGround.HONORARY_REVOKED,
            today, "UCHW-WZOC-8"));

        acts.reinstate(new MembershipActService.Reinstate(member.getId(), today, "UCHW-W3", "powrót"));

        TMember loaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(loaded.getMembershipType()).isEqualTo(MembershipType.ORDINARY);
    }

    @Test
    void reinstatementAfterRevocationOfANewPersonsHonoraryIsRefusedWithoutAPreviousType() {
        TMember honorary = acts.grantHonorary(new MembershipActService.GrantHonorary(
            new NewMember(person("Nowa", "Honorowa", today.minusYears(50)), district("mazowieckie").getId()),
            today, "UCHW-H5"));
        acts.remove(new MembershipActService.Remove(honorary.getId(), MembershipRemovalGround.HONORARY_REVOKED,
            today, "UCHW-WZOC-9"));

        assertThatThrownBy(() -> acts.reinstate(new MembershipActService.Reinstate(honorary.getId(), today,
            "UCHW-W4", "powrót")))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("NO_PREVIOUS_MEMBERSHIP_TYPE");
        assertThat(memberRepository.findById(honorary.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.REMOVED);
    }

    @Test
    void restoringOrdinaryMembershipChecksTheAgeLimitOfTwentySix() {
        TMember old = newMember("Stefan", "Starszy", "mazowieckie");
        memberService.update(old.getId(), Map.of(MemberField.BIRTH_DATE, today.minusYears(27)));
        acts.grantHonoraryToMember(new MembershipActService.GrantHonoraryToMember(old.getId(), today, "UCHW-H6"));
        acts.remove(new MembershipActService.Remove(old.getId(), MembershipRemovalGround.HONORARY_REVOKED, today,
            "UCHW-WZOC-10"));

        assertThatThrownBy(() -> acts.reinstate(new MembershipActService.Reinstate(old.getId(), today, "UCHW-W5",
            "powrót")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");
        assertThat(memberRepository.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.REMOVED);
    }

    @Test
    void membershipTypeChangesBetweenOrdinaryAndSupportingOnlyByAnAct() {
        TMember member = newMember("Irena", "Typowa", "mazowieckie");

        TMemberMembershipAct act = acts.changeMembershipType(new MembershipActService.ChangeMembershipType(
            member.getId(), MembershipType.SUPPORTING, today, "UCHW-T1", "rezygnacja z części praw"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getMembershipType())
            .isEqualTo(MembershipType.SUPPORTING);
        assertThat(act.getActKind()).isEqualTo(MembershipActKind.MEMBERSHIP_TYPE_CHANGE);
        assertThat(act.getPreviousMembershipType()).isEqualTo(MembershipType.ORDINARY);
        assertThat(act.getMembershipTypeAfter()).isEqualTo(MembershipType.SUPPORTING);
        assertThat(acts.history(member.getId())).singleElement()
                                                .extracting(TMemberMembershipAct::getActKind)
                                                .isEqualTo(MembershipActKind.MEMBERSHIP_TYPE_CHANGE);
    }

    @Test
    void membershipTypeChangeRefusesSameTypeHonoraryAndAgeOutOfRangeAndNeedsAReason() {
        TMember member = newMember("Jola", "Kontrola", "mazowieckie");

        assertThatThrownBy(() -> acts.changeMembershipType(new MembershipActService.ChangeMembershipType(
            member.getId(), MembershipType.ORDINARY, today, "UCHW-T2", "powód")))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("ALREADY_OF_TYPE");

        assertThatThrownBy(() -> acts.changeMembershipType(new MembershipActService.ChangeMembershipType(
            member.getId(), MembershipType.HONORARY, today, "UCHW-T3", "powód")))
            .isInstanceOf(InvalidInputException.class)
            .extracting("code").isEqualTo("INVALID_VALUE");

        assertThatThrownBy(() -> acts.changeMembershipType(new MembershipActService.ChangeMembershipType(
            member.getId(), MembershipType.SUPPORTING, today, "UCHW-T4", " ")))
            .isInstanceOf(InvalidInputException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        TMember older = newMember("Kazik", "Starszy", "mazowieckie");
        memberService.update(older.getId(), Map.of(MemberField.BIRTH_DATE, today.minusYears(30)));
        acts.changeMembershipType(new MembershipActService.ChangeMembershipType(older.getId(),
            MembershipType.SUPPORTING, today, "UCHW-T5", "zmiana"));
        assertThatThrownBy(() -> acts.changeMembershipType(new MembershipActService.ChangeMembershipType(
            older.getId(), MembershipType.ORDINARY, today, "UCHW-T6", "zmiana")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");
    }

    private MembershipActService.GrantSupporting supporting(LocalDate birthDate, String reference) {
        return new MembershipActService.GrantSupporting(new NewMember(person("Wspierajaca", "Osoba", birthDate),
            district("mazowieckie").getId()), today, reference);
    }

    private static Map<MemberField, Object> person(String first, String last, LocalDate birthDate) {
        Map<MemberField, Object> values = new EnumMap<>(MemberField.class);
        values.put(MemberField.PREFERRED_NAME_FIRST, first);
        values.put(MemberField.PREFERRED_NAME_LAST, last);
        values.put(MemberField.FORMAL_NAME_FIRST, first);
        values.put(MemberField.FORMAL_NAME_LAST, last);
        values.put(MemberField.BIRTH_DATE, birthDate);
        return values;
    }
}
