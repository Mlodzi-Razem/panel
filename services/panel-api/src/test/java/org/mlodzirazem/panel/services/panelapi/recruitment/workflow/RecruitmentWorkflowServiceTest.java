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

package org.mlodzirazem.panel.services.panelapi.recruitment.workflow;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentDecisionKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecision;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecisionRepository;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class RecruitmentWorkflowServiceTest extends DomainTestSupport {
    @Autowired
    RecruitmentWorkflowService workflowService;

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    @Autowired
    TRecruitmentDecisionRepository decisionRepository;

    @Autowired
    TRecruitmentCaseEventRepository caseEventRepository;

    @Test
    void declarationStartsTheStatutoryClock() {
        long actor = districtActor();
        TRecruitmentCase waiting = awaitingCase("zegar@example.org");
        LocalDate receivedOn = LocalDate.now().minusDays(80);

        TRecruitmentCase declared = workflowService.recordDeclaration(waiting.getId(), actor,
            DeclarationForm.ELECTRONIC, RecruitmentAssessmentTrack.STANDARD, receivedOn);

        assertThat(declared.getStatus()).isEqualTo(RecruitmentCaseStatus.DECLARATION_RECEIVED);
        assertThat(declared.getDeclarationReceivedOn()).isEqualTo(receivedOn);
        assertThat(declared.getDeclarationForm()).isEqualTo(DeclarationForm.ELECTRONIC);
        assertThat(declared.getAssessmentNotBeforeOn()).isEqualTo(receivedOn.plusDays(62));
        assertThat(declared.getAssessmentNotLaterOn()).isEqualTo(receivedOn.plusDays(76));

        List<String> events = eventTypes(waiting.getId());
        assertThat(events).contains(RecruitmentCaseEventType.DECLARATION_RECORDED.name(),
            RecruitmentCaseEventType.STATUS_CHANGED.name());
        assertThat(payloads(waiting.getId())).contains("assessment_not_before_on").doesNotContain(
            "zegar@example.org");
        assertThat(auditCount("recruitment :: record-declaration")).isEqualTo(1);
    }

    @Test
    void declarationIsRecordedOnlyWhileAwaiting() {
        long actor = districtActor();
        TRecruitmentCase freshCase = awaitingCase("nowa@example.org");

        assertThatThrownBy(() -> workflowService.startProbation(freshCase.getId(), actor))
            .isInstanceOf(ConflictException.class)
            .extracting("code")
            .isEqualTo("NOT_DECLARATION_RECEIVED");

        TRecruitmentCase freshNew = newCase("swieza@example.org");

        assertThatThrownBy(() -> workflowService.recordDeclaration(freshNew.getId(), actor,
            DeclarationForm.SCAN, RecruitmentAssessmentTrack.STANDARD, LocalDate.now()))
            .isInstanceOf(ConflictException.class)
            .extracting("code")
            .isEqualTo("NOT_AWAITING_DECLARATION");
    }

    @Test
    void declarationCannotBeReceivedInTheFuture() {
        long actor = districtActor();
        TRecruitmentCase waiting = awaitingCase("zprzyszlosci@example.org");

        assertThatThrownBy(() -> workflowService.recordDeclaration(waiting.getId(), actor,
            DeclarationForm.SCAN, RecruitmentAssessmentTrack.STANDARD, LocalDate.now().plusDays(1)))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code")
            .isEqualTo("DECLARATION_DATE_INVALID");
    }

    @Test
    void probationStartsAfterTheDeclarationAndNotifiesZk() {
        long actor = districtActor();
        long caseId = declaredCase("probacyjna@example.org").getId();

        TRecruitmentCase probation = workflowService.startProbation(caseId, actor);

        assertThat(probation.getStatus()).isEqualTo(RecruitmentCaseStatus.PROBATION);
        assertThat(eventTypes(caseId)).contains(RecruitmentCaseEventType.ZK_NOTIFIED_PROBATION_START.name());
        assertThat(auditCount("recruitment :: start-probation")).isEqualTo(1);
    }

    @Test
    void assessmentExtensionIsOnceOnlyAndGivesThirtyOneDays() {
        long actor = districtActor();
        TRecruitmentCase probation = probationCase("wydłużana@example.org");
        LocalDate decidedOn = LocalDate.now().minusDays(10);

        TRecruitmentCase extended = workflowService.extendAssessment(probation.getId(), actor,
            board("mazowieckie").getId(), decidedOn, "ZO/MZ/20/26", null);

        assertThat(extended.getStatus()).isEqualTo(RecruitmentCaseStatus.PROBATION_EXTENDED);
        assertThat(extended.getExtensionCount()).isEqualTo(1);
        assertThat(extended.getExtensionDecidedOn()).isEqualTo(decidedOn);
        assertThat(extended.getSecondAssessmentNotLaterOn()).isEqualTo(decidedOn.plusDays(31));
        assertThat(hasDecision(probation.getId(), RecruitmentDecisionKind.PERIOD_EXTENSION)).isTrue();

        assertThatThrownBy(() -> workflowService.extendAssessment(probation.getId(), actor,
            board("mazowieckie").getId(), LocalDate.now(), "ZO/MZ/21/26", null))
            .isInstanceOf(ConflictException.class)
            .extracting("code")
            .isEqualTo("EXTENSION_ALREADY_USED");
    }

    @Test
    void assessmentTooEarlyIsBlocked() {
        long actor = districtActor();
        TRecruitmentCase probation = probationCase("pospieszna@example.org");

        assertThatThrownBy(() -> workflowService.recordZoRecommendation(probation.getId(), actor,
            board("mazowieckie").getId(), probation.getDeclarationReceivedOn().plusDays(30),
            "ZO/MZ/30/26", null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code")
            .isEqualTo("ASSESSMENT_TOO_EARLY");
    }

    @Test
    void recommendationSetsTheAdmissionResolutionDeadline() {
        long actor = districtActor();
        TRecruitmentCase probation = probationCase("rekomendowana@example.org");
        LocalDate decidedOn = probation.getAssessmentNotBeforeOn().plusDays(5);

        TRecruitmentCase recommended = workflowService.recordZoRecommendation(probation.getId(), actor,
            board("mazowieckie").getId(), decidedOn, "ZO/MZ/40/26", "aktywna w projekcie");

        assertThat(recommended.getStatus()).isEqualTo(RecruitmentCaseStatus.ZO_RECOMMENDED);
        assertThat(recommended.getZoRecommendedOn()).isEqualTo(decidedOn);
        assertThat(recommended.getAdmissionResolutionNotLaterOn()).isEqualTo(decidedOn.plusDays(7));
        assertThat(eventTypes(probation.getId())).contains(RecruitmentCaseEventType.RECOMMENDATION_SENT.name());
    }

    @Test
    void zoKindsDecideTheBoardOfTheCaseDistrictOnly() {
        long actor = districtActor();
        TRecruitmentCase probation = probationCase("cudzoziemska@example.org");
        LocalDate decidedOn = probation.getAssessmentNotBeforeOn().plusDays(5);

        assertThatThrownBy(() -> workflowService.recordZoRecommendation(probation.getId(), actor,
            body("national-board").getId(), decidedOn, "ZK/50/26", null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code")
            .isEqualTo("DECIDING_BODY_NOT_ZO");

        TBody foreignBoard = board("dolnoslaskie");

        assertThatThrownBy(() -> workflowService.recordZoRecommendation(probation.getId(), actor,
            foreignBoard.getId(), decidedOn, "ZO/DS/51/26", null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code")
            .isEqualTo("DECIDING_BODY_NOT_ZO");
    }

    @Test
    void fullStringReachesAcceptedWithBothDecisionsRecordedFirst() {
        long zkActor = zkActor();
        TRecruitmentCase recommended = recommendedCase("przyjety@example.org",
            LocalDate.now().minusDays(70));
        long caseId = recommended.getId();

        TRecruitmentCase voting = workflowService.startZkVoting(caseId, zkActor);

        assertThat(voting.getStatus()).isEqualTo(RecruitmentCaseStatus.ZK_VOTING);
        assertThat(eventTypes(caseId)).contains(RecruitmentCaseEventType.VOTE_STARTED.name());

        workflowService.recordZkApproval(caseId, zkActor, body("national-board").getId(),
            recommended.getZoRecommendedOn().plusDays(1), "ZK/60/26", "opinia pozytywna");

        TMember acceptedMember = newMember("Pawel", "Przyjety", "mazowieckie");

        assertThatThrownBy(() -> workflowService.accept(caseId, zkActor,
            recommended.getZoRecommendedOn().plusDays(3), acceptedMember.getId()))
            .isInstanceOf(ConflictException.class)
            .extracting("code")
            .isEqualTo("BOTH_DECISIONS_REQUIRED");

        workflowService.recordAdmissionResolution(caseId, zkActor, board("mazowieckie").getId(),
            recommended.getZoRecommendedOn().plusDays(2), "ZO/MZ/61/26", null);

        TRecruitmentCase accepted = workflowService.accept(caseId, zkActor,
            recommended.getZoRecommendedOn().plusDays(3), acceptedMember.getId());

        assertThat(accepted.getStatus()).isEqualTo(RecruitmentCaseStatus.ACCEPTED);
        assertThat(accepted.getAcceptedOn()).isEqualTo(recommended.getZoRecommendedOn().plusDays(3));
        assertThat(accepted.getAcceptedMember().getId()).isEqualTo(acceptedMember.getId());
        assertThat(accepted.getClosedAt()).isEqualTo(accepted.getAcceptedOn());
        assertThat(eventTypes(caseId)).contains(RecruitmentCaseEventType.CASE_CLOSED.name());
        assertThat(auditCount("recruitment :: accept")).isEqualTo(1);

        assertThat(decisionRepository.findAllByRecruitmentCaseId(caseId))
            .extracting(TRecruitmentDecision::getDecisionKind)
            .containsExactlyInAnyOrder(RecruitmentDecisionKind.ZO_ASSESSMENT_POSITIVE,
                RecruitmentDecisionKind.ZK_APPROVAL, RecruitmentDecisionKind.ZO_ADMISSION_RESOLUTION);
    }

    @Test
    void admissionResolutionRequiresThePriorZkApproval() {
        long zkActor = zkActor();
        TRecruitmentCase recommended = recommendedCase("bezopinii@example.org", LocalDate.now().minusDays(70));
        long caseId = recommended.getId();
        workflowService.startZkVoting(caseId, zkActor);

        assertThatThrownBy(() -> workflowService.recordAdmissionResolution(caseId, zkActor,
            board("mazowieckie").getId(), recommended.getZoRecommendedOn().plusDays(1), "ZO/MZ/62/26", null))
            .isInstanceOf(ConflictException.class)
            .extracting("code")
            .isEqualTo("ZK_APPROVAL_REQUIRED");
    }

    @Test
    void tacitZkApprovalCarriesTheStatutoryNote() {
        long zkActor = zkActor();
        TRecruitmentCase recommended = recommendedCase("milczaca@example.org", LocalDate.now().minusDays(70));
        long caseId = recommended.getId();
        workflowService.startZkVoting(caseId, zkActor);

        workflowService.recordTacitZkApproval(caseId, zkActor, body("national-board").getId(),
            "ZK/PRT/63/26", null);

        TRecruitmentDecision opinion = decisionRepository
            .findByRecruitmentCaseIdAndDecisionKind(caseId, RecruitmentDecisionKind.ZK_APPROVAL)
            .orElseThrow();

        assertThat(opinion.getNote()).isEqualTo(RecruitmentRules.TACIT_ZK_APPROVAL_NOTE);
        assertThat(opinion.getDecidedOn()).isEqualTo(LocalDate.now(java.time.Clock.systemUTC()));
        assertThat(opinion.getDecisionSignature()).isEqualTo("ZK/PRT/63/26");
        assertThat(auditCount("recruitment :: record-tacit-zk-approval")).isEqualTo(1);
    }

    @Test
    void zkOpinionsAreDecidedByZkAndOnceOnly() {
        long zkActor = zkActor();
        TRecruitmentCase recommended = recommendedCase("podwójna@example.org", LocalDate.now().minusDays(70));
        long caseId = recommended.getId();
        workflowService.startZkVoting(caseId, zkActor);

        workflowService.recordZkApproval(caseId, zkActor, body("national-board").getId(),
            recommended.getZoRecommendedOn().plusDays(1), "ZK/64/26", null);

        assertThatThrownBy(() -> workflowService.recordZkApproval(caseId, zkActor,
            body("national-board").getId(), recommended.getZoRecommendedOn().plusDays(2), "ZK/65/26", null))
            .isInstanceOf(ConflictException.class)
            .extracting("code")
            .isEqualTo("DECISION_ALREADY_RECORDED");

        assertThatThrownBy(() -> workflowService.recordZkApproval(caseId, zkActor,
            board("mazowieckie").getId(), recommended.getZoRecommendedOn().plusDays(2), "ZO/MZ/66/26", null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code")
            .isEqualTo("DECIDING_BODY_NOT_ZK");
    }

    @Test
    void caseManagementNeedsDistrictPermissionsAndZkOpinionsNeedTheNationalApproval() {
        long strangerActor = newMember("Obca", "Osoba", "mazowieckie").getId();
        long actor = districtActor();
        TRecruitmentCase declared = declaredCase("gaeditowana@example.org");

        assertThatThrownBy(() -> workflowService.startProbation(declared.getId(), strangerActor))
            .isInstanceOf(ForbiddenException.class)
            .extracting("code")
            .isEqualTo("RECRUITMENT_MANAGE_REQUIRED");

        workflowService.startProbation(declared.getId(), actor);

        TRecruitmentCase recommended = recommendedCase("glosowana@example.org", LocalDate.now().minusDays(70));
        long caseId = recommended.getId();
        workflowService.startZkVoting(caseId, zkActor());

        assertThatThrownBy(() -> workflowService.recordZkApproval(caseId, actor,
            body("national-board").getId(), recommended.getZoRecommendedOn().plusDays(1), "ZK/67/26", null))
            .isInstanceOf(ForbiddenException.class)
            .extracting("code")
            .isEqualTo("RECRUITMENT_APPROVE_REQUIRED");
    }

    @Test
    void rejectionDuringAssessmentRecordsTheResolutionAndClosesTheCase() {
        long actor = districtActor();
        TRecruitmentCase probation = probationCase("odrzucana@example.org");
        LocalDate decidedOn = probation.getAssessmentNotBeforeOn().plusDays(5);

        TRecruitmentCase rejected = workflowService.reject(probation.getId(), actor,
            board("mazowieckie").getId(), decidedOn, RecruitmentRejectionReason.NOT_FIT, "ZO/MZ/70/26", null);

        assertThat(rejected.getStatus()).isEqualTo(RecruitmentCaseStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo(RecruitmentRejectionReason.NOT_FIT);
        assertThat(rejected.getClosedAt()).isEqualTo(decidedOn);
        assertThat(hasDecision(probation.getId(), RecruitmentDecisionKind.REJECTION)).isTrue();
        assertThat(eventTypes(probation.getId())).contains(RecruitmentCaseEventType.CASE_CLOSED.name());
        assertThat(auditCount("recruitment :: reject")).isEqualTo(1);
    }

    @Test
    void rejectionReasonsFollowTheSourceStatus() {
        long actor = districtActor();
        TRecruitmentCase probation = probationCase("niespójna@example.org");

        assertThatThrownBy(() -> workflowService.reject(probation.getId(), actor,
            board("mazowieckie").getId(), LocalDate.now(), RecruitmentRejectionReason.VOTE_FAILED,
            "ZO/MZ/71/26", null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code")
            .isEqualTo("REJECTION_REASON_NOT_ALLOWED");

        TRecruitmentCase recommended = recommendedCase("zbanowana@example.org", LocalDate.now().minusDays(70));
        long caseId = recommended.getId();
        workflowService.startZkVoting(caseId, zkActor());

        assertThatThrownBy(() -> workflowService.reject(caseId, actor, board("mazowieckie").getId(),
            LocalDate.now(), RecruitmentRejectionReason.NO_DECLARATION, "ZO/MZ/72/26", null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code")
            .isEqualTo("REJECTION_REASON_NOT_ALLOWED");

        TRecruitmentCase rejected = workflowService.reject(caseId, actor, board("mazowieckie").getId(),
            LocalDate.now(), RecruitmentRejectionReason.ZK_BAN, "ZO/MZ/73/26", null);

        assertThat(rejected.getRejectionReason()).isEqualTo(RecruitmentRejectionReason.ZK_BAN);
    }

    @Test
    void votingStartsOnlyAfterTheRecommendation() {
        long actor = districtActor();
        TRecruitmentCase probation = probationCase("nagotowaniu@example.org");

        assertThatThrownBy(() -> workflowService.startZkVoting(probation.getId(), actor))
            .isInstanceOf(ConflictException.class)
            .extracting("code")
            .isEqualTo("NOT_RECOMMENDED");
    }

    @Test
    void postDeclarationRejectsHappenDuringAssessmentOrVoting() {
        long actor = districtActor();
        TRecruitmentCase declared = declaredCase("zarazpo@example.org");

        assertThatThrownBy(() -> workflowService.reject(declared.getId(), actor,
            board("mazowieckie").getId(), LocalDate.now(), RecruitmentRejectionReason.NOT_FIT,
            "ZO/MZ/74/26", null))
            .isInstanceOf(ConflictException.class)
            .extracting("code")
            .isEqualTo("NOT_REJECTABLE_STATUS");
    }

    @Test
    void eventPayloadsCarryFieldNamesOnly() {
        long actor = districtActor();
        long caseId = declaredCase("danekontaktowe@example.org").getId();

        workflowService.startProbation(caseId, actor);

        String payload = payloads(caseId);

        assertThat(payload).contains("\"fields\"").contains("status");
        assertThat(payload).doesNotContain("danekontaktowe@example.org").doesNotContain("Kolendra");
    }

    private TRecruitmentCase newCase(String email) {
        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setDistrict(district("mazowieckie"));
        candidateCase.setCandidateFirstName("Kolendra");
        candidateCase.setCandidateLastName("Kowalska");
        candidateCase.setCandidateEmail(email);
        candidateCase.setCandidateBirthDate(LocalDate.of(2007, 4, 1));
        return caseRepository.saveAndFlush(candidateCase);
    }

    /** A case at the M2 boundary: the intake module has qualified it and it waits for the declaration. */
    private TRecruitmentCase awaitingCase(String email) {
        TRecruitmentCase candidateCase = newCase(email);
        candidateCase.setStatus(RecruitmentCaseStatus.AWAITING_DECLARATION);
        return caseRepository.saveAndFlush(candidateCase);
    }

    private TRecruitmentCase declaredCase(String email) {
        long actor = districtActor();
        TRecruitmentCase waiting = awaitingCase(email);
        return workflowService.recordDeclaration(waiting.getId(), actor, DeclarationForm.ELECTRONIC,
            RecruitmentAssessmentTrack.STANDARD, LocalDate.now().minusDays(80));
    }

    private TRecruitmentCase probationCase(String email) {
        TRecruitmentCase declared = declaredCase(email);
        return workflowService.startProbation(declared.getId(), districtActor());
    }

    private TRecruitmentCase recommendedCase(String email, LocalDate receivedOn) {
        long actor = districtActor();
        TRecruitmentCase waiting = awaitingCase(email);
        TRecruitmentCase declared = workflowService.recordDeclaration(waiting.getId(), actor,
            DeclarationForm.SCAN, RecruitmentAssessmentTrack.STANDARD, receivedOn);
        TRecruitmentCase probation = workflowService.startProbation(declared.getId(), actor);

        return workflowService.recordZoRecommendation(probation.getId(), actor, board("mazowieckie").getId(),
            declared.getAssessmentNotBeforeOn().plusDays(3), "ZO/MZ/REC/26", null);
    }

    private long districtActor() {
        TMember actor = newMember("Antoni", "Okręgowy", "mazowieckie");
        grant(actor, board("mazowieckie"), "COORDINATOR");
        return actor.getId();
    }

    private long zkActor() {
        TMember actor = newMember("Bogna", "Krajowa", "mazowieckie");
        grant(actor, body("national-board"), "CO_CHAIR");
        return actor.getId();
    }

    private boolean hasDecision(long caseId, RecruitmentDecisionKind kind) {
        return decisionRepository.findByRecruitmentCaseIdAndDecisionKind(caseId, kind).isPresent();
    }

    private List<String> eventTypes(long caseId) {
        return caseEventRepository.findAllByRecruitmentCaseIdOrderByCreatedAtAscIdAsc(caseId)
            .stream()
            .map(event -> event.getEventType().name())
            .toList();
    }

    private String payloads(long caseId) {
        return caseEventRepository.findAllByRecruitmentCaseIdOrderByCreatedAtAscIdAsc(caseId)
            .stream()
            .map(event -> event.getPayload() == null ? "" : event.getPayload())
            .reduce("", (all, payload) -> all + payload);
    }

}
