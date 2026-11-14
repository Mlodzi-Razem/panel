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

package org.mlodzirazem.panel.services.panelapi.api.v1.bodies;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignmentCheck;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyScope;

import java.time.LocalDate;
import java.util.List;

public final class BodyResponses {
    private BodyResponses() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BodyResponse(
        long id,
        String name,
        @Nullable String code,
        @Nullable String email,
        @Nullable BodyKind kind,
        @Nullable BodyScope scope,
        @Nullable Long templateId,
        @Nullable String templateName,
        @Nullable Long parentBodyId
    ) {
    }

    public record MemberTypeResponse(long id, String name, @Nullable String code, boolean isDefault) {
    }

    public record CompositionEntry(
        long membershipId,
        long memberId,
        String memberName,
        long memberTypeId,
        String memberTypeName,
        LocalDate validFrom,
        @Nullable LocalDate validTo
    ) {
    }

    public record BodyDetailResponse(
        BodyResponse body,
        List<CompositionEntry> composition,
        List<MemberTypeResponse> memberTypes
    ) {
    }

    public record TemplateResponse(
        long id,
        String name,
        BodyKind kind,
        BodyScope scope,
        @Nullable Long parentTemplateId,
        @Nullable Integer maxConsecutiveTerms,
        @Nullable Integer termLengthMonths,
        @Nullable Integer maxTermMonths,
        @Nullable Integer maxExtensions,
        List<MemberTypeResponse> memberTypes
    ) {
    }

    public record MembershipResponse(
        long id,
        long bodyId,
        long memberId,
        String memberName,
        long memberTypeId,
        String memberTypeName,
        LocalDate validFrom,
        @Nullable LocalDate validTo
    ) {
    }

    public record AssignmentResponse(
        MembershipResponse membership,
        List<MembershipResponse> endedMemberships,
        AssignmentCheck check
    ) {
    }
}
