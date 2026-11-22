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

package org.mlodzirazem.panel.services.panelapi.recruitment;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentAdmissionService.Admission;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.SyntheticPesel;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class RecruitmentAdmissionServiceTest extends DomainTestSupport {
    private static final String CANDIDATE_PHONE = "+48500000001";

    @Autowired
    RecruitmentAdmissionService admission;

    @Autowired
    RecruitmentWorkflowService workflow;

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    @Test
    void admissionAllocatesTheNumberCreatesTheMemberAndClosesTheCase() {
        long zk = zkActor();
        LocalDate zkDate = LocalDate.now().minusDays(2);
        LocalDate zoDate = LocalDate.now().minusDays(1);
        TRecruitmentCase voting = votingCase(zk, zkDate, zoDate);
        long caseId = voting.getId();

        Admission result = admission.admit(caseId, zk);

        TMember member = result.member();
        assertThat(member.getMemberId()).matches("\\d{6}");
        assertThat(Integer.parseInt(member.getMemberId())).isBetween(1, 949_999);
        assertThat(member.getMemberSince()).isEqualTo(zoDate);
        assertThat(member.getProbationPeriodStartDate()).isEqualTo(voting.getDeclarationReceivedOn());
        assertThat(member.getProbationPeriodEndDate()).isEqualTo(zoDate);
        assertThat(member.getRequestDate()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getMembershipType()).isEqualTo(MembershipType.ORDINARY);
        assertThat(member.getFormalNameFirst()).isEqualTo("Perseusz");
        assertThat(member.getFormalNameLast()).isEqualTo("Syntetyczny");
        assertThat(member.getPreferredNameFirst()).isEqualTo("Perseusz");
        assertThat(member.getPrivateEmails()).containsExactly(email(4242));
        assertThat(member.getPhoneNumbers()).containsExactly(CANDIDATE_PHONE);
        assertThat(member.getBirthDate()).isEqualTo(LocalDate.of(2007, 4, 1));

        assertThat(jdbc.queryForObject(
            "SELECT b.code FROM member_district_affiliations a JOIN bodies b ON b.id = a.district_body_id"
                + " WHERE a.member_id = ? AND a.kind = 'PRIMARY' AND a.valid_to IS NULL",
            String.class, member.getId())).isEqualTo("district-mazowieckie");

        assertThat(jdbc.queryForObject(
            "SELECT valid_from FROM member_district_affiliations WHERE member_id = ? AND kind = 'PRIMARY'",
            LocalDate.class, member.getId())).isEqualTo(zoDate);

        TRecruitmentCase accepted = caseRepository.findById(caseId).orElseThrow();
        assertThat(accepted.getStatus()).isEqualTo(RecruitmentCaseStatus.ACCEPTED);
        assertThat(accepted.getAcceptedOn()).isEqualTo(zoDate);
        assertThat(accepted.getAcceptedMember().getId()).isEqualTo(member.getId());

        assertThat(auditCount("recruitment :: admission")).isEqualTo(1);
        assertThat(auditCount("members :: create")).isGreaterThanOrEqualTo(1);
        String payload = auditPayloads("recruitment :: admission");
        assertThat(payload).contains("caseId", "memberId", "formalIdentifier", "privateEmails", "memberSince")
                           .doesNotContain(email(4242), CANDIDATE_PHONE, "Perseusz", member.getMemberId(),
                               voting.getCandidateFormalIdentifier());
    }

    @Test
    void membershipDateIsTheLaterOfTheTwoDecisionDates() {
        long zk = zkActor();
        LocalDate later = LocalDate.now().minusDays(1);
        TRecruitmentCase voting = votingCase(zk, later, later.minusDays(1));

        TMember member = admission.admit(voting.getId(), zk).member();

        assertThat(member.getMemberSince()).isEqualTo(later);
    }

    @Test
    void anExistingFormalIdentifierRefusesGenericallyAndWritesNothing() {
        long zk = zkActor();
        TRecruitmentCase voting = votingCase(zk, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1));
        TMember existing = newMember("Istniejacy", "Czlonek", "slaskie");
        existing.setFormalIdentifier(voting.getCandidateFormalIdentifier());
        memberRepository.saveAndFlush(existing);
        long membersBefore = memberRepository.count();

        assertThatThrownBy(() -> admission.admit(voting.getId(), zk))
            .isInstanceOf(ConflictException.class)
            .hasMessageNotContaining("Istniejacy")
            .extracting("code").isEqualTo("MEMBER_ALREADY_EXISTS");

        assertThat(memberRepository.count()).isEqualTo(membersBefore);
        assertThat(caseRepository.findById(voting.getId()).orElseThrow().getStatus())
            .isEqualTo(RecruitmentCaseStatus.ZK_VOTING);
        assertThat(auditCount("recruitment :: admission")).isZero();
    }

    @Test
    void aMissingDecisionRefusesAndWritesNothing() {
        long zk = zkActor();
        TRecruitmentCase recommended = recommendedCase(zk);
        workflow.startZkVoting(recommended.getId(), zk);
        workflow.recordZkApproval(recommended.getId(), zk, body("national-board").getId(),
            LocalDate.now().minusDays(1), "ZK/90/2026", null);
        long membersBefore = memberRepository.count();

        assertThatThrownBy(() -> admission.admit(recommended.getId(), zk))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("BOTH_DECISIONS_REQUIRED");

        assertThat(memberRepository.count()).isEqualTo(membersBefore);
        assertThat(caseRepository.findById(recommended.getId()).orElseThrow().getStatus())
            .isEqualTo(RecruitmentCaseStatus.ZK_VOTING);
    }

    @Test
    void aClosedCaseIsRefused() {
        long zk = zkActor();
        TRecruitmentCase voting = votingCase(zk, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1));
        admission.admit(voting.getId(), zk);
        long membersAfterFirst = memberRepository.count();

        assertThatThrownBy(() -> admission.admit(voting.getId(), zk))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("CASE_NOT_OPEN");

        assertThat(memberRepository.count()).isEqualTo(membersAfterFirst);
    }

    @Test
    void aCaseOutsideTheVotingIsRefused() {
        long zk = zkActor();
        TRecruitmentCase recommended = recommendedCase(zk);

        assertThatThrownBy(() -> admission.admit(recommended.getId(), zk))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("NOT_ZK_VOTING");
    }

    @Test
    void onlyTheNationalApproverMayAdmit() {
        long zk = zkActor();
        TRecruitmentCase voting = votingCase(zk, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1));
        TMember district = newMember("Antoni", "Okregowy", "mazowieckie");
        grant(district, board("mazowieckie"), "COORDINATOR");

        assertThatThrownBy(() -> admission.admit(voting.getId(), district.getId()))
            .isInstanceOf(ForbiddenException.class);

        assertThat(auditCount("recruitment :: admission")).isZero();
    }

    @Test
    void allocatedNumbersNeverEnterTheReservedBands() {
        long zk = zkActor();

        for (int i = 0; i < 3; i++) {
            TRecruitmentCase voting = votingCase(zk, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1),
                4000 + i);
            long number = Long.parseLong(admission.admit(voting.getId(), zk).member().getMemberId());

            assertThat(number).isBetween(1L, 949_999L);
        }
    }

    private TRecruitmentCase votingCase(long zk, LocalDate zkDecidedOn, LocalDate zoDecidedOn) {
        return votingCase(zk, zkDecidedOn, zoDecidedOn, 4242);
    }

    private TRecruitmentCase votingCase(long zk, LocalDate zkDecidedOn, LocalDate zoDecidedOn, int pesel) {
        TRecruitmentCase recommended = recommendedCase(zk, pesel);
        long caseId = recommended.getId();

        workflow.startZkVoting(caseId, zk);
        workflow.recordZkApproval(caseId, zk, body("national-board").getId(), zkDecidedOn, "ZK/91/2026", null);
        workflow.recordAdmissionResolution(caseId, zk, board("mazowieckie").getId(), zoDecidedOn,
            "ZO/MZ/92/2026", null);

        return caseRepository.findById(caseId).orElseThrow();
    }

    private TRecruitmentCase recommendedCase(long actor) {
        return recommendedCase(actor, 4242);
    }

    /** A case the e-declaration has filled (synthetic data only), recommended by ZO long enough ago. */
    private TRecruitmentCase recommendedCase(long actor, int peselSerial) {
        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setDistrict(district("mazowieckie"));
        candidateCase.setCandidateFirstName("Perseusz");
        candidateCase.setCandidateLastName("Syntetyczny");
        candidateCase.setCandidateFormalFirstName("Perseusz");
        candidateCase.setCandidateFormalLastName("Syntetyczny");
        candidateCase.setCandidateEmail(email(peselSerial));
        candidateCase.setCandidatePhone(CANDIDATE_PHONE);
        candidateCase.setCandidateFormalIdentifier(SyntheticPesel.of(LocalDate.of(2007, 4, 1), peselSerial));
        candidateCase.setCandidateBirthDate(LocalDate.of(2007, 4, 1));
        candidateCase.setStatus(RecruitmentCaseStatus.AWAITING_DECLARATION);
        candidateCase = caseRepository.saveAndFlush(candidateCase);

        TRecruitmentCase declared = workflow.recordDeclaration(candidateCase.getId(), actor, DeclarationForm.SCAN,
            RecruitmentAssessmentTrack.STANDARD, LocalDate.now().minusDays(80));
        TRecruitmentCase probation = workflow.startProbation(declared.getId(), actor);

        return workflow.recordZoRecommendation(probation.getId(), actor, board("mazowieckie").getId(),
            declared.getAssessmentNotBeforeOn().plusDays(3), "ZO/MZ/REC/26", null);
    }

    private static String email(int serial) {
        return "przyjmowany.syntetyczny" + serial + "@example.org";
    }

    private long zkActor() {
        TMember actor = newMember("Bogna", "Krajowa", "mazowieckie");
        grant(actor, body("national-board"), "CO_CHAIR");
        return actor.getId();
    }
}
