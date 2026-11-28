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

import java.time.LocalDate;
import java.util.Set;

/**
 * A new suspension. {@code start} defaults to today; {@code end} is exclusive and optional.
 * {@code confirmedWarnings} are warning codes the operator has confirmed.
 */
public record SuspendMember(
    long memberId,
    SuspensionKind kind,
    SuspensionScope scope,
    @Nullable String scopeDescription,
    long suspendingBodyId,
    @Nullable LocalDate start,
    @Nullable LocalDate end,
    @Nullable LocalDate decisionDate,
    @Nullable String decisionSignature,
    Set<String> confirmedWarnings
) {
    public SuspendMember {
        confirmedWarnings = confirmedWarnings == null ? Set.of() : Set.copyOf(confirmedWarnings);
    }
}
