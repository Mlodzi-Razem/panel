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

package org.mlodzirazem.panel.services.panelapi.suspensions;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Ends the chosen mandates on the day the positions were lost. Nothing is ended unless it is listed here. */
public record EndSuspendedMandates(Set<Long> membershipIds, @Nullable String decisionReference) {
    public EndSuspendedMandates {
        membershipIds = membershipIds == null ? Set.of() : Set.copyOf(membershipIds);
    }
}
