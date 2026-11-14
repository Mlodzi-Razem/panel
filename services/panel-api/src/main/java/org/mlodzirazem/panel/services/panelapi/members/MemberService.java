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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.util.List;
import java.util.Map;

public interface MemberService {
    /**
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException if there is no such member
     */
    TMember get(long memberId);

    /**
     * Requires the preferred and formal name parts and the birth date; status defaults to ACTIVE and the membership
     * type to ORDINARY. Also creates the primary district affiliation (voting here). Audited.
     */
    TMember create(NewMember newMember);

    /**
     * Applies the given values and audits the names of the fields that really changed. Who may edit which field
     * group is the caller's responsibility, see {@link MemberField#group()}.
     */
    MemberUpdate update(long memberId, Map<MemberField, @Nullable Object> changes);

    /**
     * Ordinary and supporting members (active, suspended or on probation) who turn 26 in the given year, so their
     * membership ends on 31 December of it (Art. 17.1.2).
     */
    List<TMember> membersEndingIn(int year);
}
