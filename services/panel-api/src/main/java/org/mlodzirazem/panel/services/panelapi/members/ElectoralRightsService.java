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

import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.util.List;

/**
 * Electoral rights are computed from the current register and never stored.
 */
public interface ElectoralRightsService {
    ElectoralRights rightsOf(long memberId);

    List<TMember> nationalElectorate();

    /**
     * Members whose primary district is the given one (Art. 41.7).
     */
    List<TMember> districtElectorate(long districtId);

    /**
     * Members whose voting district (votes_here) is the given one (Art. 41.8).
     */
    List<TMember> delegateElectorate(long districtId);

    List<KkwDistrictFunction> kkwMembersWithDistrictFunctions();
}
