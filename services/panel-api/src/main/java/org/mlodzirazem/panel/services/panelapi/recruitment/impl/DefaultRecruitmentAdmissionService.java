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

package org.mlodzirazem.panel.services.panelapi.recruitment.impl;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.NewMember;
import org.mlodzirazem.panel.services.panelapi.members.PermissionService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentDecisionKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecision;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecisionRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentAdmissionService;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformRules;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class DefaultRecruitmentAdmissionService implements RecruitmentAdmissionService {
    static final int MAX_ALLOCATION_ATTEMPTS = 50;

    private final TRecruitmentCaseRepository cases;
    private final TRecruitmentDecisionRepository decisions;
    private final TMemberRepository members;
    private final MemberService memberService;
    private final RecruitmentWorkflowService workflow;
    private final PermissionService permissions;
    private final AuditLog auditLog;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Admission admit(long caseId, long actorMemberId) {
        // Checked before anything is written or a number is drawn from the sequence.
        if (!permissions.of(actorMemberId).canGlobally(PermissionType.RECRUITMENT_APPROVE)) {
            throw new ForbiddenException("RECRUITMENT_APPROVE_REQUIRED",
                "Admitting a candidate requires the global RECRUITMENT_APPROVE permission");
        }

        TRecruitmentCase candidateCase = cases.findByIdForUpdate(caseId)
            .orElseThrow(() -> new NotFoundException("Recruitment case", caseId));

        if (candidateCase.getClosedAt() != null) {
            throw new ConflictException("CASE_NOT_OPEN", "The case is closed; closed cases are read-only");
        }

        if (candidateCase.getStatus() != RecruitmentCaseStatus.ZK_VOTING) {
            throw new ConflictException("NOT_ZK_VOTING",
                "The case is in %s, not in the ZK voting".formatted(candidateCase.getStatus()));
        }

        TRecruitmentDecision zkApproval = decisions
            .findByRecruitmentCaseIdAndDecisionKind(caseId, RecruitmentDecisionKind.ZK_APPROVAL).orElse(null);
        TRecruitmentDecision resolution = decisions
            .findByRecruitmentCaseIdAndDecisionKind(caseId, RecruitmentDecisionKind.ZO_ADMISSION_RESOLUTION)
            .orElse(null);

        if (zkApproval == null || resolution == null) {
            throw new ConflictException("BOTH_DECISIONS_REQUIRED",
                "Admitting a candidate requires the ZK approval and the ZO admission resolution; missing: %s"
                    .formatted(zkApproval == null ? "ZK_APPROVAL" : "ZO_ADMISSION_RESOLUTION"));
        }

        LocalDate acceptedOn = zkApproval.getDecidedOn().isAfter(resolution.getDecidedOn())
            ? zkApproval.getDecidedOn() : resolution.getDecidedOn();

        String identifier = candidateCase.getCandidateFormalIdentifier();

        if (identifier != null && members.findByFormalIdentifier(identifier).isPresent()) {
            // Generic on purpose: no hint which member holds the identifier.
            throw new ConflictException("MEMBER_ALREADY_EXISTS",
                "The candidate cannot be admitted: the person is already registered as a member");
        }

        Map<MemberField, Object> values = memberValues(candidateCase, acceptedOn);
        values.put(MemberField.MEMBER_ID, allocateMemberNumber());

        TMember member = memberService.create(
            new NewMember(values, candidateCase.getDistrict().getId(), true, acceptedOn));

        TRecruitmentCase accepted = workflow.accept(caseId, actorMemberId, acceptedOn, member.getId());

        auditLog.record("recruitment :: admission",
            AuditPayload.of("caseId", caseId)
                        .withId("memberId", member.getId())
                        .withFields(values.keySet().stream().map(MemberField::propertyName).toList()));

        return new Admission(accepted, member);
    }

    /**
     * Copies only what the case holds and the member model has a column for. Preferred names fall back
     * to the intake names and finally to the formal ones (the member row needs a preferred name; the
     * candidate then goes by the official one).
     */
    private static Map<MemberField, Object> memberValues(TRecruitmentCase c, LocalDate acceptedOn) {
        Map<MemberField, Object> values = new EnumMap<>(MemberField.class);

        String preferredFirst = c.getCandidatePreferredFirstName();
        String preferredLast = c.getCandidatePreferredLastName();

        if (blank(preferredFirst) || blank(preferredLast)) {
            preferredFirst = c.getCandidateFirstName();
            preferredLast = c.getCandidateLastName();
        }

        if (blank(preferredFirst) || blank(preferredLast)) {
            preferredFirst = c.getCandidateFormalFirstName();
            preferredLast = c.getCandidateFormalLastName();
        }

        put(values, MemberField.PREFERRED_NAME_FIRST, preferredFirst);
        put(values, MemberField.PREFERRED_NAME_LAST, preferredLast);
        put(values, MemberField.FORMAL_NAME_FIRST, c.getCandidateFormalFirstName());
        put(values, MemberField.FORMAL_NAME_LAST, c.getCandidateFormalLastName());
        put(values, MemberField.FORMAL_IDENTIFIER, c.getCandidateFormalIdentifier());
        put(values, MemberField.PRONOUNS, c.getCandidatePronouns());
        put(values, MemberField.SOCIAL_PROFILE_URL, c.getCandidateSocialProfileUrl());
        put(values, MemberField.DECLARATION_REFERENCE, c.getDeclarationReference());

        if (!blank(c.getCandidateEmail())) {
            values.put(MemberField.PRIVATE_EMAILS, List.of(c.getCandidateEmail().strip()));
        }

        if (!blank(c.getCandidatePhone())) {
            values.put(MemberField.PHONE_NUMBERS, List.of(c.getCandidatePhone().strip()));
        }

        LocalDate birthDate = c.getCandidateBirthDate();

        if (birthDate == null) {
            birthDate = birthDateFromPesel(c.getCandidateFormalIdentifier());
        }

        if (birthDate != null) {
            values.put(MemberField.BIRTH_DATE, birthDate);
        }

        values.put(MemberField.MEMBER_SINCE, acceptedOn);

        // The okres wstępny as the case clocks ran it: from the declaration to the admission.
        if (c.getDeclarationReceivedOn() != null) {
            values.put(MemberField.PROBATION_PERIOD_START_DATE, c.getDeclarationReceivedOn());
            values.put(MemberField.PROBATION_PERIOD_END_DATE, acceptedOn);
        }

        return values;
    }

    private static @Nullable LocalDate birthDateFromPesel(@Nullable String pesel) {
        if (pesel == null || !pesel.matches("\\d{11}")) {
            return null;
        }

        return RecruitmentEformRules.peselBirthDate(pesel).orElse(null);
    }

    private static void put(Map<MemberField, Object> values, MemberField field, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            values.put(field, value.strip());
        }
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * Next free six-digit number of the allocation sequence. Existence is checked before the insert
     * (a unique violation would abort the PostgreSQL transaction); numbers taken by manual creation are
     * skipped and the sequence gaps are accepted. The sequence itself is capped below the reserved bands.
     */
    String allocateMemberNumber() {
        for (int attempt = 0; attempt < MAX_ALLOCATION_ATTEMPTS; attempt++) {
            long number = ((Number) entityManager
                .createNativeQuery("select nextval('seq_member_number_allocation')")
                .getSingleResult()).longValue();

            if (number >= MemberRules.RESERVED_TEST_MEMBER_NUMBER_FROM) {
                throw new IllegalStateException("The member number allocation reached the reserved band");
            }

            if (members.findByMemberNumber(Long.toString(number)).isEmpty()) {
                return "%06d".formatted(number);
            }
        }

        throw new IllegalStateException(
            "No free member number after %d allocation attempts".formatted(MAX_ALLOCATION_ATTEMPTS));
    }
}
