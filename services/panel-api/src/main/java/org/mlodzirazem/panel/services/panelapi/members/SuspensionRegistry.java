/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

import java.time.LocalDate;
import java.util.Set;

/**
 * Derived state of suspensions (art. 10 ust. 3): whether a member's competences in organs and positions are
 * suspended on a given day. Computed from the register; it never changes member status.
 */
public interface SuspensionRegistry {
    /**
     * An active FULL or PASSIVE_ELECTORAL suspension covers the day. PARTIAL_OTHER, voided and lifted rows
     * (before the day) do not count.
     */
    boolean competencesSuspended(long memberId, LocalDate on);

    Set<Long> competenceSuspendedMemberIds(LocalDate on);
}
