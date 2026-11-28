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
import java.util.List;

/**
 * Art. 10 ust. 3 and art. 18 ust. 1 pkt 2 as a proposal. {@code outstanding} are the mandates the person still holds
 * at the loss date and that the operator may end; {@code conflicting} started after the loss date and need a manual
 * correction in the bodies module.
 */
public record PositionLoss(
    boolean applicable,
    @Nullable LocalDate dueOn,
    boolean due,
    List<MandateRef> outstanding,
    List<MandateRef> conflicting,
    int endedCount
) {
}
