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

package org.mlodzirazem.panel.services.panelapi.members;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@PostgresTest
class ElectoralRightsServiceTest extends DomainTestSupport {
    @Autowired
    ElectoralRightsService service;

    @Autowired
    AffiliationService affiliationService;

    @Test
    void activeOrdinaryMemberMayVoteInPrimaryDistrictAndNationally() {
        TMember member = newMember("Eli", "Elector", "mazowieckie");

        ElectoralRights rights = service.rightsOf(member.getId());

        assertThat(rights.eligible()).isTrue();
        assertThat(rights.canVoteInNationalElection()).isTrue();
        assertThat(rights.canVoteInDistrictElection(district("mazowieckie").getId())).isTrue();
        assertThat(rights.canVoteInDistrictElection(district("slaskie").getId())).isFalse();
        assertThat(rights.canVoteInDelegateElection(district("mazowieckie").getId())).isTrue();
        assertThat(rights.feeArrears()).isFalse();
    }

    @Test
    void onlyActiveOrdinaryNonSuspendedMembersAreEligible() {
        TMember supporting = newMember("Sup", "Porting", "mazowieckie");
        TMember honorary = newMember("Hon", "Orary", "mazowieckie");
        TMember removed = newMember("Rem", "Oved", "mazowieckie");
        TMember probation = newMember("Pro", "Bation", "mazowieckie");
        TMember statusSuspended = newMember("Sus", "Pended", "mazowieckie");
        TMember suspended = newMember("Sus", "Pension", "mazowieckie");
        TMember pastSuspension = newMember("Pas", "Tsus", "mazowieckie");
        TMember eligible = newMember("Eli", "Gible", "mazowieckie");

        memberService.update(supporting.getId(), Map.of(MemberField.MEMBERSHIP_TYPE, MembershipType.SUPPORTING));
        memberService.update(honorary.getId(), Map.of(MemberField.MEMBERSHIP_TYPE, MembershipType.HONORARY));
        memberService.update(removed.getId(), Map.of(MemberField.STATUS, MemberStatus.REMOVED));
        memberService.update(probation.getId(), Map.of(MemberField.STATUS, MemberStatus.PROBATION));
        memberService.update(statusSuspended.getId(), Map.of(MemberField.STATUS, MemberStatus.SUSPENDED));
        suspend(suspended, LocalDate.now().minusDays(10), null);
        suspend(pastSuspension, LocalDate.now().minusDays(30), LocalDate.now().minusDays(5));

        assertThat(service.rightsOf(suspended.getId()).suspended()).isTrue();
        assertThat(service.nationalElectorate()).extracting(TMember::getId)
                                                .containsExactlyInAnyOrder(eligible.getId(), pastSuspension.getId());
        assertThat(service.districtElectorate(district("mazowieckie").getId())).extracting(TMember::getId)
                                                                               .containsExactlyInAnyOrder(
                                                                                   eligible.getId(),
                                                                                   pastSuspension.getId());
    }

    @Test
    void districtElectionsUsePrimaryAndDelegateElectionsUseTheVotingDistrict() {
        TMember home = newMember("Hom", "E", "mazowieckie");
        TMember voter = newMember("Vot", "Er", "mazowieckie");
        TMember stranger = newMember("Str", "Anger", "slaskie");
        affiliationService.addSupportingDistrict(voter.getId(), district("slaskie").getId(), true);

        assertThat(service.districtElectorate(district("mazowieckie").getId())).extracting(TMember::getId)
                                                                               .containsExactlyInAnyOrder(home.getId(), voter.getId());
        assertThat(service.districtElectorate(district("slaskie").getId())).extracting(TMember::getId)
                                                                           .containsExactly(stranger.getId());
        assertThat(service.delegateElectorate(district("mazowieckie").getId())).extracting(TMember::getId)
                                                                               .containsExactly(home.getId());
        assertThat(service.delegateElectorate(district("slaskie").getId())).extracting(TMember::getId)
                                                                           .containsExactlyInAnyOrder(voter.getId(), stranger.getId());

        ElectoralRights rights = service.rightsOf(voter.getId());
        assertThat(rights.canVoteInDistrictElection(district("slaskie").getId())).isFalse();
        assertThat(rights.canVoteInDelegateElection(district("slaskie").getId())).isTrue();
    }

    @Test
    void kkwMembersWithDistrictFunctionsAreFlagged() {
        TMember kkwAndZo = newMember("Kay", "Both", "mazowieckie");
        TMember kkwOnly = newMember("Kim", "Only", "mazowieckie");
        TMember zoOnly = newMember("Zoe", "Only", "mazowieckie");
        TMember kkwAndTreasurer = newMember("Kit", "Cash", "slaskie");
        grant(kkwAndZo, body("electoral-commission"), "MEMBER");
        grant(kkwAndZo, board("mazowieckie"), "COORDINATOR");
        grant(kkwOnly, body("electoral-commission"), "CHAIR");
        grant(zoOnly, board("mazowieckie"), "COORDINATOR");
        grant(kkwAndTreasurer, body("electoral-commission"), "MEMBER");
        grant(kkwAndTreasurer, board("slaskie"), "TREASURER");

        assertThat(service.kkwMembersWithDistrictFunctions()).extracting(KkwDistrictFunction::memberId)
                                                             .containsExactlyInAnyOrder(kkwAndZo.getId(), kkwAndTreasurer.getId());
        assertThat(service.kkwMembersWithDistrictFunctions()).filteredOn(f -> f.memberId() == kkwAndZo.getId())
                                                             .singleElement()
                                                             .satisfies(f -> {
                                                                 assertThat(f.districtIds()).containsExactly(district("mazowieckie").getId());
                                                                 assertThat(f.kkwBodyId()).isEqualTo(body("electoral-commission").getId());
                                                             });
    }

    @Test
    void onlyAnActiveFullSuspensionTakesTheVoteButAPassiveOneStillBlocksFunctions() {
        TMember passive = newMember("Pas", "Sive", "mazowieckie");
        jdbc.update("INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start, decision_date, "
            + "scope) VALUES (?, ?, ?, CURRENT_DATE - 30, 'PASSIVE_ELECTORAL')", passive.getId(),
            body("peer-court").getId(), LocalDate.now().minusDays(5));

        assertThat(service.rightsOf(passive.getId()).suspended()).isFalse();
        assertThat(service.suspendedDuring(passive.getId(), LocalDate.now().minusDays(1), null)).isTrue();

        TMember voided = newMember("Voi", "Ded", "mazowieckie");
        jdbc.update("INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start, decision_date, "
            + "status, void_reason, voided_at) VALUES (?, ?, ?, CURRENT_DATE - 30, 'VOIDED', 'omyłka', now())", voided.getId(),
            body("peer-court").getId(), LocalDate.now().minusDays(5));

        assertThat(service.rightsOf(voided.getId()).suspended()).isFalse();
        assertThat(service.suspendedDuring(voided.getId(), LocalDate.now().minusDays(1), null)).isFalse();
    }

    private void suspend(TMember member, LocalDate start, LocalDate end) {
        jdbc.update("INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start, suspension_end, "
            + "decision_date) VALUES (?, ?, ?, ?, ?)", member.getId(), body("peer-court").getId(), start, end, start);
    }
}
