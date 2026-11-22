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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * One recruitment case of an okręg: candidate to ordinary membership, from
 * the intake ("wpada zgłoszenie") until PRZYJĘTA or ODRZUCONA.
 * Candidate data stays here — never in members/member_emails before acceptance.
 */
@Getter
@Setter
@Entity
@Table(name = "recruitment_cases")
@SequenceGenerator(
    name = "seq_recruitment_cases_id",
    sequenceName = "seq_recruitment_cases_id",
    allocationSize = 1
)
public class TRecruitmentCase extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_recruitment_cases_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_body_id", nullable = false)
    private TBody district;

    @Column(name = "candidate_first_name")
    private @Nullable String candidateFirstName;

    @Column(name = "candidate_last_name")
    private @Nullable String candidateLastName;

    @Column(name = "candidate_email")
    private @Nullable String candidateEmail;

    @Column(name = "candidate_phone")
    private @Nullable String candidatePhone;

    @Column(name = "candidate_social_profile_url")
    private @Nullable String candidateSocialProfileUrl;

    @Column(name = "candidate_birth_date")
    private @Nullable LocalDate candidateBirthDate;

    /** Official name ("w dowodzie", paper form A.1/A.2) — carries over into members at PRZYJĘTA. */
    @Column(name = "candidate_formal_first_name")
    private @Nullable String candidateFormalFirstName;

    @Column(name = "candidate_formal_last_name")
    private @Nullable String candidateFormalLastName;

    /** Preferred name (paper form A.3/A.4), optional. */
    @Column(name = "candidate_preferred_first_name")
    private @Nullable String candidatePreferredFirstName;

    @Column(name = "candidate_preferred_last_name")
    private @Nullable String candidatePreferredLastName;

    /** Pronouns used by the candidate (paper form A.6), optional. */
    @Column(name = "candidate_pronouns")
    private @Nullable String candidatePronouns;

    /** Candidate PESEL (paper form A.5), same idiom as members.formal_identifier; GDPR Art. 9. */
    @Column(name = "candidate_formal_identifier")
    private @Nullable String candidateFormalIdentifier;

    /**
     * Provisional trial-account number (97xxxx-99xxxx, dev/README §7.3), UPN/employeeId marker only;
     * provisioning, PRZYJĘTA rename and REJECTED cleanup belong to M6. NULL until DEKLARACJA DOSZŁA.
     */
    @Column(name = "trial_account_number")
    private @Nullable Long trialAccountNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source", nullable = false, columnDefinition = "recruitment_source")
    private RecruitmentSource source = RecruitmentSource.MANUAL;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "recruitment_case_status")
    private RecruitmentCaseStatus status = RecruitmentCaseStatus.NEW;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rejection_reason", columnDefinition = "recruitment_rejection_reason")
    private @Nullable RecruitmentRejectionReason rejectionReason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "declaration_form", columnDefinition = "declaration_form")
    private @Nullable DeclarationForm declarationForm;

    @Column(name = "declaration_reference")
    private @Nullable String declarationReference;

    @Column(name = "declaration_received_on")
    private @Nullable LocalDate declarationReceivedOn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "assessment_track", nullable = false, columnDefinition = "recruitment_assessment_track")
    private RecruitmentAssessmentTrack assessmentTrack = RecruitmentAssessmentTrack.STANDARD;

    @Column(name = "assessment_not_before_on")
    private @Nullable LocalDate assessmentNotBeforeOn;

    @Column(name = "assessment_not_later_on")
    private @Nullable LocalDate assessmentNotLaterOn;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount;

    @Column(name = "extension_decided_on")
    private @Nullable LocalDate extensionDecidedOn;

    @Column(name = "second_assessment_not_later_on")
    private @Nullable LocalDate secondAssessmentNotLaterOn;

    @Column(name = "admission_resolution_not_later_on")
    private @Nullable LocalDate admissionResolutionNotLaterOn;

    @Column(name = "zo_recommended_on")
    private @Nullable LocalDate zoRecommendedOn;

    @Column(name = "accepted_on")
    private @Nullable LocalDate acceptedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_member_id")
    private @Nullable TMember acceptedMember;

    @Column(name = "closed_at")
    private @Nullable LocalDate closedAt;

    @Column(name = "anonymized_at")
    private @Nullable ZonedDateTime anonymizedAt;
}
