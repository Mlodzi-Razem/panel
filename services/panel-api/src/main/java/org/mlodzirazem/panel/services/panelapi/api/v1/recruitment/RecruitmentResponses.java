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

package org.mlodzirazem.panel.services.panelapi.api.v1.recruitment;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.PersonRef;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentDecisionKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentSource;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public final class RecruitmentResponses {
    private RecruitmentResponses() {
    }

    public record DistrictRef(long id, String name, @Nullable String code) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseSummaryResponse(
        long id,
        DistrictRef district,
        String candidateFirstName,
        @Nullable String candidateLastName,
        @Nullable String candidateEmail,
        @Nullable String candidatePhone,
        RecruitmentCaseStatus status,
        RecruitmentSource source,
        ZonedDateTime createdAt,
        @Nullable ZonedDateTime updatedAt,
        @Nullable LocalDate closedAt,
        @Nullable RecruitmentRejectionReason rejectionReason
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseDetailResponse(
        long id,
        DistrictRef district,
        String candidateFirstName,
        @Nullable String candidateLastName,
        @Nullable String candidateEmail,
        @Nullable String candidatePhone,
        @Nullable String candidateSocialProfileUrl,
        @Nullable LocalDate candidateBirthDate,
        RecruitmentCaseStatus status,
        RecruitmentSource source,
        @Nullable RecruitmentRejectionReason rejectionReason,
        @Nullable LocalDate closedAt,
        ZonedDateTime createdAt,
        @Nullable ZonedDateTime updatedAt,
        @Nullable DeclarationForm declarationForm,
        @Nullable String declarationReference,
        @Nullable LocalDate declarationReceivedOn,
        @Nullable RecruitmentAssessmentTrack assessmentTrack,
        @Nullable LocalDate assessmentNotBeforeOn,
        @Nullable LocalDate assessmentNotLaterOn,
        int extensionCount,
        @Nullable LocalDate extensionDecidedOn,
        @Nullable LocalDate secondAssessmentNotLaterOn,
        @Nullable LocalDate zoRecommendedOn,
        @Nullable LocalDate admissionResolutionNotLaterOn,
        List<CaseDecisionResponse> decisions,
        List<CaseEventResponse> events
    ) {
    }

    /**
     * One collegial resolution recorded on the case (M3 engine). The collegial note stays in the
     * module's audit trail and is not served over HTTP; the deciding body identifies who decided.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseDecisionResponse(
        long id,
        RecruitmentDecisionKind kind,
        LocalDate decidedOn,
        String decisionSignature,
        long decidingBodyId,
        String decidingBodyName
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseEventResponse(
        long id,
        RecruitmentCaseEventType eventType,
        @Nullable JsonNode payload,
        ZonedDateTime createdAt,
        @Nullable PersonRef actor
    ) {
    }

    /**
     * One candidate move of the transitions endpoint: the same verdict the transition POST would
     * give from the current status — the front end renders the buttons from this and never keeps a
     * local copy of the state map.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseTransitionResponse(
        RecruitmentCaseStatus target,
        RecruitmentCaseEventType type,
        boolean allowed,
        @Nullable List<RecruitmentRejectionReason> rejectionReasons,
        @Nullable String blockedReason
    ) {
    }

    public record CaseTransitionsResponse(List<CaseTransitionResponse> transitions) {
    }
}
