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

import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliation;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the invariants: exactly one open PRIMARY affiliation and exactly one open affiliation with votes_here per
 * member. Changes take effect today; ended rows get {@code valid_to = today}. All mutations are audited.
 */
public interface AffiliationService {
    List<TMemberDistrictAffiliation> active(long memberId);

    MemberDistricts districtsOf(long memberId);

    Optional<TBody> primaryDistrict(long memberId);

    Optional<TBody> votingDistrict(long memberId);

    /**
     * Sets or moves the primary district. The new primary takes over {@code votes_here} when the previous primary
     * (or nothing) held it. A supporting affiliation to the target district is promoted.
     */
    TMemberDistrictAffiliation setPrimaryDistrict(long memberId, long districtId, boolean keepPreviousAsSupporting);

    /**
     * As {@link #setPrimaryDistrict(long, long, boolean)}, but the new affiliation starts on {@code validFrom}
     * ({@code null} means today). Meant for a member's first affiliation (admission); a date in the future is
     * refused with {@code AFFILIATION_DATE_IN_FUTURE}.
     */
    TMemberDistrictAffiliation setPrimaryDistrict(
        long memberId,
        long districtId,
        boolean keepPreviousAsSupporting,
        @Nullable LocalDate validFrom
    );

    TMemberDistrictAffiliation addSupportingDistrict(long memberId, long districtId, boolean votesHere);

    /**
     * Ends a supporting affiliation; if it held votes_here the primary district gets it back.
     */
    void endSupportingDistrict(long memberId, long districtId);

    /**
     * Moves votes_here to the given district, which must be one of the member's active affiliations.
     */
    void setVotingDistrict(long memberId, long districtId);
}
