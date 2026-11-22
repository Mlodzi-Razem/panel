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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.PermissionService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationArtifactKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationFormSessionStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationSignatureType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentConsentKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBanRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TDeclarationArtifact;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TDeclarationArtifactRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TDeclarationFormSession;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TDeclarationFormSessionRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentConsent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentConsentRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationArtifactStore;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationFormAccessException;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationPdfRenderer;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationProperties;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationSignatureValidator;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformRules;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformService;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;

/**
 * The e-declaration flow behind the magic link (M4a). One live session per case: issuing a new
 * link expires the previous one. The token itself lives only in the link response (the session
 * row keeps its SHA-256 hash). Form submissions stay anonymous on the wire — the values land on
 * the case columns, the session remembers submitted field names only, and every audit trail and
 * case event carries names, never values. The declaration recording always runs as the link
 * creator (the RECRUITMENT_MANAGE holder who invited the candidate): the anonymous candidate
 * cannot appear as the deciding actor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class DefaultRecruitmentEformService implements RecruitmentEformService {
    // the entity enum shares its name with the DeclarationForm response record of the interface;
    // the fully qualified constants keep the two apart
    private static final org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm
        ELECTRONIC_FORM = org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm.ELECTRONIC;
    private static final org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm
        SCAN_FORM = org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm.SCAN;

    private static final String CODE_TOKEN_INVALID = "TOKEN_INVALID";
    private static final String CODE_TOKEN_EXPIRED = "TOKEN_EXPIRED";
    private static final String CODE_CASE_CLOSED = "CASE_CLOSED";

    private final TRecruitmentCaseRepository cases;
    private final TDeclarationFormSessionRepository sessions;
    private final TDeclarationArtifactRepository artifacts;
    private final TRecruitmentConsentRepository consents;
    private final TRecruitmentCaseEventRepository caseEvents;
    private final TAdmissionBanRepository bans;
    private final TMemberRepository members;
    private final PermissionService permissions;
    private final RecruitmentWorkflowService workflow;
    private final DeclarationSignatureValidator signatureValidator;
    private final DeclarationPdfRenderer pdfRenderer;
    private final DeclarationArtifactStore artifactStore;
    private final DeclarationProperties properties;
    private final AuditLog auditLog;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public DeclarationLink createDeclarationLink(long caseId, long actorMemberId) {
        TRecruitmentCase candidateCase = cases.findByIdForUpdate(caseId)
            .orElseThrow(() -> new NotFoundException("recruitment case", caseId));

        if (candidateCase.getStatus() != RecruitmentCaseStatus.AWAITING_DECLARATION) {
            throw new ConflictException("NOT_AWAITING_DECLARATION",
                "The e-declaration link is issued for a case awaiting the declaration, not for %s"
                    .formatted(candidateCase.getStatus()));
        }

        requireCaseManage(actorMemberId, candidateCase);
        expireLiveSessions(caseId);

        byte[] rawToken = new byte[RecruitmentEformRules.TOKEN_BYTES];
        secureRandom.nextBytes(rawToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
        ZonedDateTime expiresAt = ZonedDateTime.now(clock)
            .plusHours(RecruitmentEformRules.LINK_TTL_HOURS);

        TDeclarationFormSession session = new TDeclarationFormSession();
        session.setRecruitmentCase(candidateCase);
        session.setTokenHash(RecruitmentEformRules.sha256Hex(token));
        session.setStatus(DeclarationFormSessionStatus.CREATED);
        session.setExpiresAt(expiresAt);
        session.setCreatedBy(members.getReferenceById(actorMemberId));
        sessions.saveAndFlush(session);

        emit(candidateCase, actorMemberId, RecruitmentCaseEventType.DECLARATION_LINK_CREATED,
            List.of("token_hash", "expires_at"));

        auditLog.record("recruitment :: declaration-link",
            AuditPayload.of("caseId", candidateCase.getId())
                .withId("actorId", actorMemberId)
                .withFields(List.of("token_hash", "expires_at", "status"))
                .withOutcome(session.getStatus().name()));

        return new DeclarationLink(token, properties.getPublicBaseUrl() + token, expiresAt);
    }

    @Override
    @Transactional
    public DeclarationForm getForm(String token) {
        TDeclarationFormSession session = sessions.findByTokenHash(hashOf(token))
            .orElseThrow(DefaultRecruitmentEformService::tokenInvalid);

        requireLive(session, session.getRecruitmentCase());

        return formView(session, token);
    }

    @Override
    @Transactional
    public DeclarationForm submitForm(String token, DeclarationFormSubmission submission) {
        TDeclarationFormSession session = sessions.findCaseByTokenHashForUpdate(hashOf(token))
            .orElseThrow(DefaultRecruitmentEformService::tokenInvalid);
        TRecruitmentCase candidateCase = session.getRecruitmentCase();

        requireLive(session, candidateCase);

        if (session.getStatus() == DeclarationFormSessionStatus.SIGNED) {
            throw new ConflictException("DECLARATION_ALREADY_SIGNED",
                "The declaration of this form has already been signed");
        }

        RecruitmentEformRules.assertNamePair(submission.formalFirstName(), submission.formalLastName(),
            "NAMES_ARE_A_PAIR");
        RecruitmentEformRules.assertNamePair(submission.preferredFirstName(),
            submission.preferredLastName(), "NAMES_ARE_A_PAIR");
        requirePresent(submission.formalFirstName(), "The formal first name is required");
        requirePresent(submission.formalLastName(), "The formal last name is required");
        requirePresent(submission.personalId(), "The personal identifier is required");

        // art. 9 ust. 2 lit. c: the special-category consent is a precondition of the declaration
        if (!submission.specialCategoryConsent()) {
            throw new RuleViolationException("FIELD_REQUIRED",
                "The special-category data consent decision is required");
        }

        String personalId = RecruitmentEformRules.normalizePersonalId(submission.personalId(),
            candidateCase.getCandidateBirthDate());
        LocalDate birthDate = RecruitmentEformRules.peselBirthDate(personalId)
            .orElseThrow(() -> new RuleViolationException("PERSONAL_ID_INVALID",
                "The personal identifier is not valid"));

        RecruitmentEformRules.assertAdmissionAge(birthDate, LocalDate.now(clock));
        requireNotBlocked(personalId, candidateCase);

        candidateCase.setCandidateFormalFirstName(submission.formalFirstName().strip());
        candidateCase.setCandidateFormalLastName(submission.formalLastName().strip());

        if (filled(submission.preferredFirstName())) {
            candidateCase.setCandidatePreferredFirstName(submission.preferredFirstName().strip());
            candidateCase.setCandidatePreferredLastName(submission.preferredLastName().strip());
        }

        if (filled(submission.pronouns())) {
            candidateCase.setCandidatePronouns(submission.pronouns().strip());
        }

        if (filled(submission.phone())) {
            candidateCase.setCandidatePhone(submission.phone().strip());
        }

        if (filled(submission.email())) {
            candidateCase.setCandidateEmail(submission.email().strip());
        }

        candidateCase.setCandidateFormalIdentifier(personalId);
        cases.saveAndFlush(candidateCase);

        byte[] template = pdfRenderer.render(candidateCase, null);
        TDeclarationArtifact templateArtifact = new TDeclarationArtifact();
        templateArtifact.setRecruitmentCase(candidateCase);
        templateArtifact.setSession(session);
        templateArtifact.setArtifactKind(DeclarationArtifactKind.TEMPLATE_PDF);
        templateArtifact.setContentSha256(RecruitmentEformRules.sha256Hex(template));
        templateArtifact.setStorageRef(artifactStore.save(template));
        templateArtifact.setTemplateVersion(RecruitmentEformRules.TEMPLATE_VERSION);
        artifacts.saveAndFlush(templateArtifact);

        recordConsents(candidateCase, submission, templateArtifact);

        session.setStatus(DeclarationFormSessionStatus.FILLED);

        if (session.getFilledAt() == null) {
            session.setFilledAt(ZonedDateTime.now(clock));
        }

        session.setSubmittedFields(namesOnly(List.of(
            "candidate_formal_first_name", "candidate_formal_last_name",
            "candidate_preferred_first_name", "candidate_preferred_last_name", "candidate_pronouns",
            "candidate_formal_identifier", "candidate_phone", "candidate_email",
            "recruitment_consents")));
        sessions.saveAndFlush(session);

        auditLog.record("recruitment :: declaration-submit",
            AuditPayload.of("caseId", candidateCase.getId())
                .withId("actorId", linkCreatorId(session))
                .withFields(List.of(
                    "candidate_formal_first_name", "candidate_formal_last_name",
                    "candidate_preferred_first_name", "candidate_preferred_last_name",
                    "candidate_pronouns", "candidate_formal_identifier", "candidate_phone",
                    "candidate_email", "recruitment_consents"))
                .withOutcome(session.getStatus().name()));

        return formView(session, token);
    }

    @Override
    @Transactional
    public byte[] declarationPdf(String token) {
        TDeclarationFormSession session = sessions.findCaseByTokenHashForUpdate(hashOf(token))
            .orElseThrow(DefaultRecruitmentEformService::tokenInvalid);

        requireLive(session, session.getRecruitmentCase());

        TDeclarationArtifact template = artifacts.findAllBySessionId(session.getId())
            .stream()
            .filter(artifact -> artifact.getArtifactKind() == DeclarationArtifactKind.TEMPLATE_PDF)
            .reduce((older, newer) -> newer)
            .orElseThrow(() -> new ConflictException("FORM_NOT_FILLED",
                "The form must be submitted before the declaration PDF becomes available"));

        return artifactStore.load(template.getStorageRef());
    }

    @Override
    @Transactional
    public DeclarationForm attachSignedDeclaration(String token, byte[] pdfBytes) {
        TDeclarationFormSession session = sessions.findCaseByTokenHashForUpdate(hashOf(token))
            .orElseThrow(DefaultRecruitmentEformService::tokenInvalid);
        TRecruitmentCase candidateCase = session.getRecruitmentCase();

        requireLive(session, candidateCase);
        requirePdf(pdfBytes);

        if (session.getStatus() != DeclarationFormSessionStatus.FILLED) {
            throw new ConflictException("FORM_NOT_FILLED",
                "The candidate fills the form before signing the declaration");
        }

        DeclarationSignatureValidator.ValidatedSignature signature = signatureValidator.validate(pdfBytes);

        if (!signature.valid()
            || (signature.signerSerialNumber() != null
                && !signature.signerSerialNumber().equals(candidateCase.getCandidateFormalIdentifier()))) {
            throw new RuleViolationException("SIGNATURE_INVALID",
                "The signed declaration cannot be accepted");
        }

        TDeclarationArtifact signedArtifact = new TDeclarationArtifact();
        signedArtifact.setRecruitmentCase(candidateCase);
        signedArtifact.setSession(session);
        signedArtifact.setArtifactKind(DeclarationArtifactKind.SIGNED_PDF);
        signedArtifact.setContentSha256(RecruitmentEformRules.sha256Hex(pdfBytes));
        signedArtifact.setStorageRef(artifactStore.save(pdfBytes));
        signedArtifact.setDocumentSha256(templateOf(session).map(TDeclarationArtifact::getContentSha256)
            .orElse(null));
        signedArtifact.setTemplateVersion(RecruitmentEformRules.TEMPLATE_VERSION);
        signedArtifact.setSignatureType(DeclarationSignatureType.QES);
        signedArtifact.setSignerName(signature.signerName());
        signedArtifact.setValidatedAt(ZonedDateTime.now(clock));
        signedArtifact.setValidationResult(signatureNamesOnly(signature));
        artifacts.saveAndFlush(signedArtifact);

        long recorder = linkCreatorId(session);

        session.setStatus(DeclarationFormSessionStatus.SIGNED);
        session.setSignedAt(ZonedDateTime.now(clock));
        sessions.saveAndFlush(session);

        // the anonymous upload is recorded by the RECRUITMENT_MANAGE holder who issued the link
        workflow.recordDeclaration(candidateCase.getId(), recorder, ELECTRONIC_FORM,
            candidateCase.getAssessmentTrack(), LocalDate.now(clock));

        auditLog.record("recruitment :: declaration-signature",
            AuditPayload.of("caseId", candidateCase.getId())
                .withId("actorId", recorder)
                .withFields(List.of("signature_type", "signer_name", "validation_result",
                    "content_sha256", "document_sha256"))
                .withOutcome(session.getStatus().name()));

        return formView(session, token);
    }

    @Override
    @Transactional
    public DeclarationForm attachScan(long caseId, long actorMemberId, byte[] pdfBytes) {
        TRecruitmentCase candidateCase = cases.findByIdForUpdate(caseId)
            .orElseThrow(() -> new NotFoundException("recruitment case", caseId));

        requireCaseManage(actorMemberId, candidateCase);

        if (candidateCase.getStatus() != RecruitmentCaseStatus.AWAITING_DECLARATION) {
            throw new ConflictException("NOT_AWAITING_DECLARATION",
                "The paper scan lands on a case awaiting the declaration, not on %s"
                    .formatted(candidateCase.getStatus()));
        }

        requirePdf(pdfBytes);

        TDeclarationArtifact scanArtifact = new TDeclarationArtifact();
        scanArtifact.setRecruitmentCase(candidateCase);
        scanArtifact.setArtifactKind(DeclarationArtifactKind.SCAN);
        scanArtifact.setContentSha256(RecruitmentEformRules.sha256Hex(pdfBytes));
        scanArtifact.setStorageRef(artifactStore.save(pdfBytes));
        scanArtifact.setSignatureType(DeclarationSignatureType.SCAN);
        scanArtifact.setSignerName((candidateCase.getCandidateFirstName() + " "
            + candidateCase.getCandidateLastName()).strip());
        scanArtifact.setValidatedAt(ZonedDateTime.now(clock));
        scanArtifact.setValidationResult(signatureNamesOnly(new DeclarationSignatureValidator.ValidatedSignature(
            true, null, null, List.of("PAPER_SCAN_FALLBACK"), List.of())));
        artifacts.saveAndFlush(scanArtifact);

        workflow.recordDeclaration(caseId, actorMemberId, SCAN_FORM,
            candidateCase.getAssessmentTrack(), LocalDate.now(clock));

        auditLog.record("recruitment :: declaration-scan",
            AuditPayload.of("caseId", caseId)
                .withId("actorId", actorMemberId)
                .withFields(List.of("signature_type", "storage_ref", "content_sha256"))
                .withOutcome(DeclarationArtifactKind.SCAN.name()));

        return new DeclarationForm(DeclarationFormSessionStatus.SIGNED.name(), null,
            greeting(candidateCase), null);
    }

    private void expireLiveSessions(long caseId) {
        sessions.findAllByRecruitmentCaseId(caseId)
            .stream()
            .filter(session -> session.getStatus() == DeclarationFormSessionStatus.CREATED
                || session.getStatus() == DeclarationFormSessionStatus.FILLED)
            .forEach(session -> {
                session.setStatus(DeclarationFormSessionStatus.EXPIRED);
                sessions.saveAndFlush(session);
            });
    }

    private void requireNotBlocked(String personalId, TRecruitmentCase candidateCase) {
        // one generic message, no detail which rule fired (phishing/ban hygiene, art. 9 ust. 5);
        // the reason category stays in the server log for the okręg board (case-level, no values)
        if (members.findByFormalIdentifier(personalId).isPresent()) {
            blocked(candidateCase, "existing-member");
        }

        LocalDate today = LocalDate.now(clock);

        boolean activeBan = bans.findAllByFormalIdentifier(personalId)
            .stream()
            .anyMatch(ban -> !today.isBefore(ban.getEffectiveFrom()) && !today.isAfter(ban.getEffectiveUntil()));

        if (activeBan) {
            blocked(candidateCase, "admission-ban");
        }

        if (cases.existsAnotherOpenCaseByFormalIdentifier(personalId, candidateCase.getId())) {
            blocked(candidateCase, "duplicate-open-case");
        }
    }

    private void blocked(TRecruitmentCase candidateCase, String reason) {
        log.warn("declaration blocked for case {} of district {}: {}",
            candidateCase.getId(), candidateCase.getDistrict().getId(), reason);
        throw RecruitmentEformService.declarationBlocked();
    }

    private void recordConsents(
        TRecruitmentCase candidateCase,
        DeclarationFormSubmission submission,
        TDeclarationArtifact templateArtifact
    ) {
        List<TRecruitmentConsent> existing = consents.findAllByRecruitmentCaseId(candidateCase.getId());

        recordConsent(candidateCase, existing, RecruitmentConsentKind.SPECIAL_CATEGORY_9_2D,
            templateArtifact);

        if (submission.imageConsent()) {
            recordConsent(candidateCase, existing, RecruitmentConsentKind.IMAGE, templateArtifact);
        }
    }

    private void recordConsent(
        TRecruitmentCase candidateCase,
        List<TRecruitmentConsent> existing,
        RecruitmentConsentKind kind,
        TDeclarationArtifact templateArtifact
    ) {
        boolean grantedBefore = existing.stream()
            .anyMatch(consent -> consent.getConsentKind() == kind);

        if (grantedBefore) {
            return;
        }

        TRecruitmentConsent consent = new TRecruitmentConsent();
        consent.setRecruitmentCase(candidateCase);
        consent.setConsentKind(kind);
        consent.setGrantedBy("self");
        consent.setGrantedAt(ZonedDateTime.now(clock));
        consent.setArtifact(templateArtifact);
        consents.saveAndFlush(consent);
    }

    private java.util.Optional<TDeclarationArtifact> templateOf(TDeclarationFormSession session) {
        return artifacts.findAllBySessionId(session.getId())
            .stream()
            .filter(artifact -> artifact.getArtifactKind() == DeclarationArtifactKind.TEMPLATE_PDF)
            .reduce((older, newer) -> newer);
    }

    private DeclarationForm formView(TDeclarationFormSession session, String token) {
        String pdfUrl = switch (session.getStatus()) {
            case FILLED, SIGNED -> properties.getPublicBaseUrl() + token + "/pdf";
            default -> null;
        };

        return new DeclarationForm(session.getStatus().name(), session.getExpiresAt(),
            greeting(session.getRecruitmentCase()), pdfUrl);
    }

    private void requireLive(TDeclarationFormSession session, TRecruitmentCase candidateCase) {
        if (session.getStatus() == DeclarationFormSessionStatus.EXPIRED
            || session.getExpiresAt().isBefore(ZonedDateTime.now(clock))) {
            throw new DeclarationFormAccessException(CODE_TOKEN_EXPIRED,
                "The declaration form link has expired — the okręg board can issue a new one");
        }

        if (candidateCase.getClosedAt() != null) {
            throw new DeclarationFormAccessException(CODE_CASE_CLOSED,
                "The recruitment case is already closed");
        }
    }

    private void requireCaseManage(long actorMemberId, TRecruitmentCase candidateCase) {
        if (permissions.districtsWith(actorMemberId, PermissionType.RECRUITMENT_MANAGE)
            .contains(candidateCase.getDistrict().getId())
            || permissions.of(actorMemberId).canGlobally(PermissionType.RECRUITMENT_APPROVE)) {
            return;
        }

        throw new ForbiddenException("RECRUITMENT_MANAGE_REQUIRED",
            "The e-declaration flow requires RECRUITMENT_MANAGE for the case's district"
                + " or a global RECRUITMENT_APPROVE");
    }

    private void requirePdf(byte[] pdfBytes) {
        if (pdfBytes.length > properties.getMaxUploadMb() * 1024 * 1024) {
            throw new RuleViolationException("FILE_TOO_LARGE",
                "The document exceeds the accepted size");
        }

        if (!RecruitmentEformRules.isPdf(pdfBytes)) {
            throw new RuleViolationException("FILE_INVALID", "The document must be a PDF file");
        }
    }

    private long linkCreatorId(TDeclarationFormSession session) {
        TMember creator = session.getCreatedBy();

        if (creator == null) {
            throw new ConflictException("LINK_WITHOUT_OWNER",
                "The session has no link owner to record the declaration under");
        }

        return creator.getId();
    }

    private static void requirePresent(@Nullable String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RuleViolationException("FIELD_REQUIRED", message);
        }
    }

    private static boolean filled(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private void emit(
        TRecruitmentCase candidateCase,
        long actorMemberId,
        RecruitmentCaseEventType type,
        List<String> fields
    ) {
        TRecruitmentCaseEvent event = new TRecruitmentCaseEvent();
        event.setRecruitmentCase(candidateCase);
        event.setEventType(type);
        event.setPayload(namesOnly(fields));
        event.setCreatedBy(members.getReferenceById(actorMemberId));
        caseEvents.saveAndFlush(event);
    }

    private String namesOnly(List<String> fields) {
        ObjectNode node = jsonMapper.createObjectNode();
        ArrayNode array = node.putArray("fields");
        fields.forEach(array::add);

        return jsonMapper.writeValueAsString(node);
    }

    private String signatureNamesOnly(DeclarationSignatureValidator.ValidatedSignature signature) {
        ObjectNode node = jsonMapper.createObjectNode();
        ArrayNode checks = node.putArray("checks");
        signature.performedChecks().forEach(checks::add);
        ArrayNode failed = node.putArray("failed");
        signature.failedChecks().forEach(failed::add);

        return jsonMapper.writeValueAsString(node);
    }

    private static String greeting(TRecruitmentCase candidateCase) {
        String first = candidateCase.getCandidateFirstName() == null
            ? "" : candidateCase.getCandidateFirstName();
        String last = candidateCase.getCandidateLastName() == null
            ? "" : candidateCase.getCandidateLastName();

        return (first + " " + last).strip();
    }

    private static DeclarationFormAccessException tokenInvalid() {
        return new DeclarationFormAccessException(CODE_TOKEN_INVALID,
            "The declaration form link is not valid");
    }

    private static String hashOf(String token) {
        return RecruitmentEformRules.sha256Hex(token);
    }
}
