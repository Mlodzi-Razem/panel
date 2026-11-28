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

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Map;

/**
 * @param allowMembershipType internal path only: membership type other than ORDINARY may be set on creation
 *                            (membership acts and the transitional import of supporting members). Anything else
 *                            goes through {@link MembershipActService}.
 */
public record NewMember(
    Map<MemberField, @Nullable Object> values,
    long primaryDistrictId,
    boolean enforceAdmissionAge,
    @Nullable LocalDate affiliationValidFrom,
    boolean allowMembershipType
) {
    public NewMember(Map<MemberField, @Nullable Object> values, long primaryDistrictId, boolean enforceAdmissionAge,
        @Nullable LocalDate affiliationValidFrom) {
        this(values, primaryDistrictId, enforceAdmissionAge, affiliationValidFrom, false);
    }

    public NewMember(Map<MemberField, @Nullable Object> values, long primaryDistrictId, boolean enforceAdmissionAge) {
        this(values, primaryDistrictId, enforceAdmissionAge, null, false);
    }

    public NewMember(Map<MemberField, @Nullable Object> values, long primaryDistrictId) {
        this(values, primaryDistrictId, true, null, false);
    }
}
