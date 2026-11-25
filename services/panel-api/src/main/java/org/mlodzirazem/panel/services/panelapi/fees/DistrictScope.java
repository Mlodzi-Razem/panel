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

package org.mlodzirazem.panel.services.panelapi.fees;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * Filter on the primary district of members; {@link #all()} disables it.
 */
public record DistrictScope(@Nullable Set<Long> districtIds) {
    public DistrictScope {
        districtIds = districtIds == null ? null : Set.copyOf(districtIds);
    }

    public static DistrictScope all() {
        return new DistrictScope(null);
    }

    public static DistrictScope of(Collection<Long> districtIds) {
        return new DistrictScope(Set.copyOf(districtIds));
    }

    public boolean includes(@Nullable Long districtId) {
        return districtIds == null || (districtId != null && districtIds.contains(districtId));
    }
}
