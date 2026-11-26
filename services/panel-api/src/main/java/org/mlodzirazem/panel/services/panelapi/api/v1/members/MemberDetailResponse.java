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

package org.mlodzirazem.panel.services.panelapi.api.v1.members;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.AffiliationKind;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberDetailResponse(
    long id,
    Map<String, @Nullable Object> fields,
    Access access,
    @Nullable List<AffiliationView> affiliations,
    List<BodyFunctionView> bodyFunctions,
    @Nullable LocalDate membershipEndsOn,
    @Nullable ElectoralRightsView electoralRights
) {
    public record Access(
        boolean readContact,
        boolean readFormal,
        boolean manageContact,
        boolean manageFormal,
        boolean formalIdentifierRevealed
    ) {
    }

    public record AffiliationView(
        long districtId,
        String districtName,
        AffiliationKind kind,
        boolean votesHere,
        LocalDate validFrom
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BodyFunctionView(
        long membershipId,
        long bodyId,
        String bodyName,
        @Nullable BodyKind bodyKind,
        long memberTypeId,
        @Nullable String memberTypeCode,
        String memberTypeName,
        LocalDate validFrom,
        @Nullable LocalDate validTo
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ElectoralRightsView(
        boolean activeOrdinaryMember,
        boolean suspended,
        boolean feeArrears,
        boolean eligible,
        boolean canVoteInNationalElection,
        @Nullable Long primaryDistrictId,
        @Nullable Long votingDistrictId
    ) {
    }
}
