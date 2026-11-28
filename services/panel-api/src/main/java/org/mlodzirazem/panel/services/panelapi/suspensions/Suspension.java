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

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the append-only register. {@code end} is exclusive and never changes; {@code liftedOn} and the
 * appeal and void fields are written once.
 */
public record Suspension(
    long id,
    long memberId,
    SuspensionKind kind,
    SuspensionScope scope,
    @Nullable String scopeDescription,
    long suspendingBodyId,
    @Nullable String suspendingBodyName,
    LocalDate start,
    @Nullable LocalDate end,
    @Nullable LocalDate decisionDate,
    @Nullable String decisionSignature,
    @Nullable LocalDate liftedOn,
    @Nullable String liftedReason,
    @Nullable Long liftedBodyId,
    @Nullable String liftDecisionSignature,
    @Nullable LocalDate appealEndedOn,
    @Nullable String appealEndedReference,
    boolean voided,
    @Nullable Instant voidedAt,
    @Nullable String voidReason,
    @Nullable String voidDecisionReference,
    Instant createdAt
) {
    public SuspensionState state(LocalDate today) {
        return SuspensionRules.state(this, today);
    }

    /** The earlier of the end and the lift date; null while the suspension has neither. */
    public @Nullable LocalDate effectiveEnd() {
        return SuspensionRules.effectiveEnd(end, liftedOn);
    }
}
