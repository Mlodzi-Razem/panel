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

package org.mlodzirazem.panel.services.panelapi.recruitment.workflow.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.PermissionService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentDecisionKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecision;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecisionRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentRules;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
class DefaultRecruitmentWorkflowService implements RecruitmentWorkflowService {
    private final TRecruitmentCaseRepository caseRepository;
    private final TRecruitmentCaseEventRepository caseEventRepository;
    private final TRecruitmentDecisionRepository decisionRepository;
    private final TBodyRepository bodyRepository;
    private final TMemberRepository members;
    private final PermissionService permissions;
    private final AuditLog auditLog;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Override
    @Transactional
    public TRecruitmentCase recordDeclaration(
        long caseId,
        long actorMemberId,
        DeclarationForm form,
        RecruitmentAssessmentTrack track,
        LocalDate receivedOn
    ) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        if (recruitmentCase.getStatus() != RecruitmentCaseStatus.AWAITING_DECLARATION) {
            throw new ConflictException("NOT_AWAITING_DECLARATION",
                "Declaration can only be recorded for a case awaiting it, not for %s"
                    .formatted(recruitmentCase.getStatus()));
        }

        requireCaseManage(actorMemberId, recruitmentCase);

        if (receivedOn.isAfter(LocalDate.now(clock))) {
            throw new RuleViolationException("DECLARATION_DATE_INVALID",
                "The declaration cannot be received in the future");
        }

        recruitmentCase.setDeclarationForm(form);
        recruitmentCase.setAssessmentTrack(track);
        recruitmentCase.setDeclarationReceivedOn(receivedOn);
        recruitmentCase.setAssessmentNotBeforeOn(RecruitmentRules.assessmentNotBeforeOn(receivedOn, track));
        recruitmentCase.setAssessmentNotLaterOn(RecruitmentRules.assessmentNotLaterOn(receivedOn));

        // one UPDATE carries the declaration data and the new status: the schema forbids declaration
        // clocks while the case is still in a pre-declaration status

        moveTo(recruitmentCase, actorMemberId, RecruitmentCaseStatus.DECLARATION_RECEIVED, List.of());

        emit(recruitmentCase, actorMemberId, RecruitmentCaseEventType.DECLARATION_RECORDED, List.of(
            "declaration_form", "declaration_received_on", "assessment_track",
            "assessment_not_before_on", "assessment_not_later_on"));

        audit(actorMemberId, recruitmentCase, "recruitment :: record-declaration", List.of(
            "declaration_form", "declaration_received_on", "assessment_track",
            "assessment_not_before_on", "assessment_not_later_on"));

        return recruitmentCase;
    }

    @Override
    @Transactional
    public TRecruitmentCase startProbation(long caseId, long actorMemberId) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        if (recruitmentCase.getStatus() != RecruitmentCaseStatus.DECLARATION_RECEIVED) {
            throw new ConflictException("NOT_DECLARATION_RECEIVED",
                "The okres wstępny starts right after the declaration arrives, not from %s"
                    .formatted(recruitmentCase.getStatus()));
        }

        requireCaseManage(actorMemberId, recruitmentCase);

        moveTo(recruitmentCase, actorMemberId, RecruitmentCaseStatus.PROBATION, List.of());
        emit(recruitmentCase, actorMemberId, RecruitmentCaseEventType.ZK_NOTIFIED_PROBATION_START, List.of(
            "declaration_received_on"));

        audit(actorMemberId, recruitmentCase, "recruitment :: start-probation", List.of("status"));

        return recruitmentCase;
    }

    @Override
    @Transactional
    public TRecruitmentCase extendAssessment(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    ) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        if (recruitmentCase.getStatus() == RecruitmentCaseStatus.PROBATION_EXTENDED) {
            throw new ConflictException("EXTENSION_ALREADY_USED",
                "The okres wstępny can be extended once only (art. 9 ust. 4 pkt 5-6)");
        }

        if (recruitmentCase.getStatus() != RecruitmentCaseStatus.PROBATION) {
            throw new ConflictException("NOT_ON_ASSESSMENT",
                "An extension is decided during the assessment, not in %s".formatted(recruitmentCase.getStatus()));
        }

        requireCaseManage(actorMemberId, recruitmentCase);
        requireCandidateBody(decidingBodyId, recruitmentCase, RecruitmentDecisionKind.PERIOD_EXTENSION);
        requireSignature(signature);
        requireNotBeforeDeclaration(recruitmentCase, decidedOn, "EXTENSION_BEFORE_DECLARATION");

        saveDecision(recruitmentCase, actorMemberId, decidingBodyId, RecruitmentDecisionKind.PERIOD_EXTENSION,
            decidedOn, signature, note);

        recruitmentCase.setExtensionCount(1);
        recruitmentCase.setExtensionDecidedOn(decidedOn);
        recruitmentCase.setSecondAssessmentNotLaterOn(RecruitmentRules.secondAssessmentNotLaterOn(decidedOn));

        moveTo(recruitmentCase, actorMemberId, RecruitmentCaseStatus.PROBATION_EXTENDED, List.of(
            "extension_count", "extension_decided_on", "second_assessment_not_later_on"));

        audit(actorMemberId, recruitmentCase, "recruitment :: extend-assessment", List.of(
            "extension_count", "extension_decided_on", "second_assessment_not_later_on"));

        return recruitmentCase;
    }

    @Override
    @Transactional
    public TRecruitmentCase recordZoRecommendation(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    ) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        requireAssessmentStatus(recruitmentCase);
        requireCaseManage(actorMemberId, recruitmentCase);
        requireCandidateBody(decidingBodyId, recruitmentCase, RecruitmentDecisionKind.ZO_ASSESSMENT_POSITIVE);
        requireSignature(signature);

        if (recruitmentCase.getExtensionCount() == 1) {
            if (decidedOn.isBefore(recruitmentCase.getExtensionDecidedOn())) {
                throw new RuleViolationException("ASSESSMENT_BEFORE_EXTENSION",
                    "The second assessment cannot predate the extension decision");
            }
        } else {
            RecruitmentRules.assertAssessmentNotTooEarly(decidedOn, recruitmentCase.getAssessmentNotBeforeOn());
        }

        saveDecision(recruitmentCase, actorMemberId, decidingBodyId, RecruitmentDecisionKind.ZO_ASSESSMENT_POSITIVE,
            decidedOn, signature, note);

        recruitmentCase.setZoRecommendedOn(decidedOn);
        recruitmentCase.setAdmissionResolutionNotLaterOn(RecruitmentRules.admissionResolutionNotLaterOn(decidedOn));

        moveTo(recruitmentCase, actorMemberId, RecruitmentCaseStatus.ZO_RECOMMENDED, List.of(
            "zo_recommended_on", "admission_resolution_not_later_on"));
        emit(recruitmentCase, actorMemberId, RecruitmentCaseEventType.RECOMMENDATION_SENT, List.of(
            "zo_recommended_on"));
        events.publishEvent(new org.mlodzirazem.panel.services.panelapi.recruitment.workflow.ZoRecommendationRecorded(
            recruitmentCase.getId()));

        audit(actorMemberId, recruitmentCase, "recruitment :: record-zo-recommendation", List.of(
            "zo_recommended_on", "admission_resolution_not_later_on"));

        return recruitmentCase;
    }

    @Override
    @Transactional
    public TRecruitmentCase startZkVoting(long caseId, long actorMemberId) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        if (recruitmentCase.getStatus() != RecruitmentCaseStatus.ZO_RECOMMENDED) {
            throw new ConflictException("NOT_RECOMMENDED",
                "ZK voting starts after a ZO recommendation, not from %s".formatted(recruitmentCase.getStatus()));
        }

        requireCaseManage(actorMemberId, recruitmentCase);

        moveTo(recruitmentCase, actorMemberId, RecruitmentCaseStatus.ZK_VOTING, List.of());
        emit(recruitmentCase, actorMemberId, RecruitmentCaseEventType.VOTE_STARTED, List.of("status"));

        audit(actorMemberId, recruitmentCase, "recruitment :: start-zk-voting", List.of("status"));

        return recruitmentCase;
    }

    @Override
    @Transactional
    public TRecruitmentCase recordZkApproval(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    ) {
        return recordZkOpinion(caseId, actorMemberId, decidingBodyId, decidedOn, signature, note,
            "recruitment :: record-zk-approval");
    }

    @Override
    @Transactional
    public TRecruitmentCase recordTacitZkApproval(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        String protocolSignature,
        @Nullable String note
    ) {
        return recordZkOpinion(caseId, actorMemberId, decidingBodyId, LocalDate.now(clock), protocolSignature,
            note == null || note.isBlank()
                ? RecruitmentRules.TACIT_ZK_APPROVAL_NOTE
                : RecruitmentRules.TACIT_ZK_APPROVAL_NOTE + " — " + note,
            "recruitment :: record-tacit-zk-approval");
    }

    @Override
    @Transactional
    public TRecruitmentCase recordAdmissionResolution(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    ) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        requireVotingStatus(recruitmentCase);
        requireCaseManage(actorMemberId, recruitmentCase);
        requireCandidateBody(decidingBodyId, recruitmentCase, RecruitmentDecisionKind.ZO_ADMISSION_RESOLUTION);
        requireSignature(signature);

        if (decisionRepository.findByRecruitmentCaseIdAndDecisionKind(caseId, RecruitmentDecisionKind.ZK_APPROVAL)
            .isEmpty()) {
            throw new ConflictException("ZK_APPROVAL_REQUIRED",
                "The admission resolution requires a prior ZK approval (explicit or tacit, art. 9 ust. 4 pkt 6, 8)");
        }

        if (decidedOn.isBefore(recruitmentCase.getZoRecommendedOn())) {
            throw new RuleViolationException("RESOLUTION_BEFORE_RECOMMENDATION",
                "The admission resolution cannot predate the ZO recommendation");
        }

        saveDecision(recruitmentCase, actorMemberId, decidingBodyId, RecruitmentDecisionKind.ZO_ADMISSION_RESOLUTION,
            decidedOn, signature, note);

        audit(actorMemberId, recruitmentCase, "recruitment :: record-admission-resolution",
            List.of("admission_resolution_not_later_on"));

        return recruitmentCase;
    }

    @Override
    @Transactional
    public TRecruitmentCase accept(long caseId, long actorMemberId, LocalDate acceptedOn, long acceptedMemberId) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        requireVotingStatus(recruitmentCase);
        requireZkApproval(actorMemberId);

        boolean zkApproved = decisionRepository
            .findByRecruitmentCaseIdAndDecisionKind(caseId, RecruitmentDecisionKind.ZK_APPROVAL)
            .isPresent();
        boolean resolved = decisionRepository
            .findByRecruitmentCaseIdAndDecisionKind(caseId, RecruitmentDecisionKind.ZO_ADMISSION_RESOLUTION)
            .isPresent();

        if (!zkApproved || !resolved) {
            throw new ConflictException("BOTH_DECISIONS_REQUIRED",
                "Accepting a case requires the ZK approval and the ZO admission resolution; missing: %s"
                    .formatted(zkApproved ? "ZO_ADMISSION_RESOLUTION" : "ZK_APPROVAL"));
        }

        TMember acceptedMember = members.findById(acceptedMemberId)
            .orElseThrow(() -> new NotFoundException("Member", acceptedMemberId));

        if (!acceptedOn.isAfter(recruitmentCase.getDeclarationReceivedOn())
            || acceptedOn.isBefore(recruitmentCase.getZoRecommendedOn())) {
            throw new RuleViolationException("ACCEPTANCE_TIMING_INVALID",
                "The acceptance follows the declaration and the recommendation");
        }

        recruitmentCase.setAcceptedOn(acceptedOn);
        recruitmentCase.setAcceptedMember(acceptedMember);
        recruitmentCase.setClosedAt(acceptedOn);

        moveTo(recruitmentCase, actorMemberId, RecruitmentCaseStatus.ACCEPTED, List.of(
            "accepted_on", "accepted_member_id", "closed_at"));
        emit(recruitmentCase, actorMemberId, RecruitmentCaseEventType.CASE_CLOSED, List.of(
            "accepted_on", "accepted_member_id", "closed_at"));

        audit(actorMemberId, recruitmentCase, "recruitment :: accept",
            List.of("accepted_on", "accepted_member_id", "closed_at"));

        return recruitmentCase;
    }

    @Override
    @Transactional
    public TRecruitmentCase reject(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        RecruitmentRejectionReason reason,
        String signature,
        @Nullable String note
    ) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        List<RecruitmentCaseStatus> rejectable = List.of(RecruitmentCaseStatus.PROBATION,
            RecruitmentCaseStatus.PROBATION_EXTENDED, RecruitmentCaseStatus.ZK_VOTING);

        if (!rejectable.contains(recruitmentCase.getStatus())) {
            throw new ConflictException("NOT_REJECTABLE_STATUS",
                "A post-declaration case is rejected during the assessment or the voting, not from %s"
                    .formatted(recruitmentCase.getStatus()));
        }

        requireCaseManage(actorMemberId, recruitmentCase);
        requireCandidateBody(decidingBodyId, recruitmentCase, RecruitmentDecisionKind.REJECTION);
        requireSignature(signature);
        requireNotBeforeDeclaration(recruitmentCase, decidedOn, "REJECTION_BEFORE_DECLARATION");

        if (!RecruitmentRules.allowedRejectionReasons(recruitmentCase.getStatus()).contains(reason)) {
            throw new RuleViolationException("REJECTION_REASON_NOT_ALLOWED",
                "Rejection %s is not allowed from %s".formatted(reason, recruitmentCase.getStatus()));
        }

        saveDecision(recruitmentCase, actorMemberId, decidingBodyId, RecruitmentDecisionKind.REJECTION,
            decidedOn, signature, note);

        recruitmentCase.setRejectionReason(reason);
        recruitmentCase.setClosedAt(decidedOn);

        moveTo(recruitmentCase, actorMemberId, RecruitmentCaseStatus.REJECTED, List.of(
            "rejection_reason", "closed_at"));
        emit(recruitmentCase, actorMemberId, RecruitmentCaseEventType.CASE_CLOSED, List.of(
            "rejection_reason", "closed_at"));

        audit(actorMemberId, recruitmentCase, "recruitment :: reject", List.of("rejection_reason", "closed_at"));

        return recruitmentCase;
    }

    private TRecruitmentCase recordZkOpinion(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note,
        String operationId
    ) {
        TRecruitmentCase recruitmentCase = lockedCase(caseId);

        requireVotingStatus(recruitmentCase);
        requireZkApproval(actorMemberId);
        requireCandidateBody(decidingBodyId, recruitmentCase, RecruitmentDecisionKind.ZK_APPROVAL);
        requireSignature(signature);

        if (decidedOn.isBefore(recruitmentCase.getZoRecommendedOn())) {
            throw new RuleViolationException("OPINION_BEFORE_RECOMMENDATION",
                "The ZK opinion follows the ZO recommendation it is about");
        }

        saveDecision(recruitmentCase, actorMemberId, decidingBodyId, RecruitmentDecisionKind.ZK_APPROVAL,
            decidedOn, signature, note);

        audit(actorMemberId, recruitmentCase, operationId, List.of("decision_kind", "decided_on"));

        return recruitmentCase;
    }

    private void requireAssessmentStatus(TRecruitmentCase recruitmentCase) {
        if (recruitmentCase.getStatus() != RecruitmentCaseStatus.PROBATION
            && recruitmentCase.getStatus() != RecruitmentCaseStatus.PROBATION_EXTENDED) {
            throw new ConflictException("NOT_ON_ASSESSMENT",
                "The assessment happens during the okres wstępny, not in %s".formatted(recruitmentCase.getStatus()));
        }
    }

    private void requireVotingStatus(TRecruitmentCase recruitmentCase) {
        if (recruitmentCase.getStatus() != RecruitmentCaseStatus.ZK_VOTING) {
            throw new ConflictException("NOT_ZK_VOTING",
                "The case is in %s, not in the ZK voting".formatted(recruitmentCase.getStatus()));
        }
    }

    private void requireNotBeforeDeclaration(TRecruitmentCase recruitmentCase, LocalDate decidedOn, String code) {
        if (decidedOn.isBefore(recruitmentCase.getDeclarationReceivedOn())) {
            throw new RuleViolationException(code, "The decision cannot predate the declaration");
        }
    }

    private TRecruitmentDecision saveDecision(
        TRecruitmentCase recruitmentCase,
        long actorMemberId,
        long decidingBodyId,
        RecruitmentDecisionKind kind,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    ) {
        RecruitmentRules.assertDecisionNotInFuture(decidedOn, LocalDate.now(clock));

        if (decisionRepository.findByRecruitmentCaseIdAndDecisionKind(recruitmentCase.getId(), kind).isPresent()) {
            throw new ConflictException("DECISION_ALREADY_RECORDED",
                "The case already has a %s decision".formatted(kind));
        }

        TBody decidingBody = bodyRepository.findById(decidingBodyId)
            .orElseThrow(() -> new NotFoundException("Body", decidingBodyId));

        TRecruitmentDecision decision = new TRecruitmentDecision();
        decision.setRecruitmentCase(recruitmentCase);
        decision.setDecidingBody(decidingBody);
        decision.setDecisionKind(kind);
        decision.setDecisionSignature(signature);
        decision.setDecidedOn(decidedOn);
        decision.setNote(note);
        decision.setCreatedBy(members.getReferenceById(actorMemberId));

        decisionRepository.saveAndFlush(decision);
        emit(recruitmentCase, actorMemberId, RecruitmentCaseEventType.DECISION_RECORDED, List.of(
            "decision_kind", "deciding_body_id", "decided_on", "decision_signature"));

        return decision;
    }

    private TRecruitmentCase moveTo(
        TRecruitmentCase recruitmentCase,
        long actorMemberId,
        RecruitmentCaseStatus target,
        List<String> changedFields
    ) {
        if (!RecruitmentRules.canTransition(recruitmentCase.getStatus(), target)) {
            throw new ConflictException("FORBIDDEN_TRANSITION",
                "The recruitment case cannot move from %s to %s"
                    .formatted(recruitmentCase.getStatus(), target));
        }

        List<String> fields = new ArrayList<>(changedFields);
        fields.add("status");

        recruitmentCase.setStatus(target);
        recruitmentCase.setUpdatedBy(members.getReferenceById(actorMemberId));
        caseRepository.saveAndFlush(recruitmentCase);

        emit(recruitmentCase, actorMemberId, RecruitmentCaseEventType.STATUS_CHANGED, fields);

        return recruitmentCase;
    }

    private void emit(
        TRecruitmentCase recruitmentCase,
        long actorMemberId,
        RecruitmentCaseEventType type,
        List<String> fields
    ) {
        TRecruitmentCaseEvent event = new TRecruitmentCaseEvent();
        event.setRecruitmentCase(recruitmentCase);
        event.setEventType(type);
        event.setPayload(namesOnly(fields));
        event.setCreatedBy(members.getReferenceById(actorMemberId));
        caseEventRepository.saveAndFlush(event);
    }

    /** GDPR discipline of the module: event payloads carry field names, never values. */
    private String namesOnly(List<String> fields) {
        ObjectNode node = jsonMapper.createObjectNode();
        ArrayNode array = node.putArray("fields");
        fields.forEach(array::add);

        return jsonMapper.writeValueAsString(node);
    }

    private void audit(long actorMemberId, TRecruitmentCase recruitmentCase, String operationId,
        List<String> changedFields) {
        auditLog.record(operationId,
            AuditPayload.of("caseId", recruitmentCase.getId())
                .withId("actorId", actorMemberId)
                .withFields(changedFields)
                .withOutcome(recruitmentCase.getStatus().name()));
    }

    private void requireCaseManage(long actorMemberId, TRecruitmentCase recruitmentCase) {
        if (permissions.districtsWith(actorMemberId, PermissionType.RECRUITMENT_MANAGE)
            .contains(recruitmentCase.getDistrict().getId())
            || permissions.of(actorMemberId).canGlobally(PermissionType.RECRUITMENT_APPROVE)) {
            return;
        }

        throw new ForbiddenException("RECRUITMENT_MANAGE_REQUIRED",
            "Recruitment case management requires RECRUITMENT_MANAGE for the case's district");
    }

    private void requireZkApproval(long actorMemberId) {
        if (!permissions.of(actorMemberId).canGlobally(PermissionType.RECRUITMENT_APPROVE)) {
            throw new ForbiddenException("RECRUITMENT_APPROVE_REQUIRED",
                "Recording a ZK opinion requires the global RECRUITMENT_APPROVE permission");
        }
    }

    private void requireCandidateBody(
        long decidingBodyId,
        TRecruitmentCase recruitmentCase,
        RecruitmentDecisionKind kind
    ) {
        TBody decidingBody = bodyRepository.findById(decidingBodyId)
            .orElseThrow(() -> new NotFoundException("Body", decidingBodyId));

        switch (kind) {
            case ZO_ASSESSMENT_POSITIVE, ZO_ADMISSION_RESOLUTION -> {
                if (!isDistrictBoardOf(decidingBody, recruitmentCase)) {
                    throw new RuleViolationException("DECIDING_BODY_NOT_ZO",
                        "Kind %s is decided by the Zarząd Okręgu of the case's district (art. 9 ust. 2 pkt 2)"
                            .formatted(kind));
                }
            }
            case ZK_APPROVAL -> {
                if (decidingBody.kind() != BodyKind.NATIONAL_BOARD) {
                    throw new RuleViolationException("DECIDING_BODY_NOT_ZK",
                        "ZK opinions are decided by the Zarząd Krajowy (art. 9 ust. 4 pkt 6, 8)");
                }
            }
            case PERIOD_EXTENSION, ZO_ASSESSMENT_NEGATIVE, REJECTION -> {
                if (decidingBody.kind() != BodyKind.NATIONAL_BOARD
                    && !isDistrictBoardOf(decidingBody, recruitmentCase)) {
                    throw new RuleViolationException("DECIDING_BODY_NOT_ZO_OR_ZK",
                        "Kind %s is decided by the Zarząd Okręgu or the Zarząd Krajowy".formatted(kind));
                }
            }
        }
    }

    private boolean isDistrictBoardOf(TBody body, TRecruitmentCase recruitmentCase) {
        if (body.kind() != BodyKind.DISTRICT_BOARD || body.getParentBody() == null) {
            return false;
        }

        return body.getParentBody().getId().equals(recruitmentCase.getDistrict().getId());
    }

    private void requireSignature(String signature) {
        if (signature == null || signature.isBlank()) {
            throw new RuleViolationException("SIGNATURE_REQUIRED",
                "Every collegial decision carries the signature of its uchwała (decision_signature)");
        }
    }

    private TRecruitmentCase lockedCase(long caseId) {
        return caseRepository.findByIdForUpdate(caseId)
            .orElseThrow(() -> new NotFoundException("Recruitment case", caseId));
    }
}
