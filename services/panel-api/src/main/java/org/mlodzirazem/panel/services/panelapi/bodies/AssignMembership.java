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

package org.mlodzirazem.panel.services.panelapi.bodies;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Set;

/**
 * A request to give a member a function (mandate) in a body.
 *
 * <p>{@code decisionReference} is the signature of the resolution that justifies the assignment
 * (stored in {@code body_memberships.decision_reference}; the reference only, never the
 * document). {@code confirmedWarnings} are the warning codes the caller has explicitly
 * confirmed: a warning that is not in this set stops the assignment until it is confirmed.
 */
public record AssignMembership(
    long memberId,
    long bodyId,
    long memberTypeId,
    LocalDate validFrom,
    @Nullable LocalDate validTo,
    @Nullable String decisionReference,
    Set<FunctionWarning.Code> confirmedWarnings
) {
    public static final int MAX_DECISION_REFERENCE_LENGTH = 200;

    public AssignMembership {
        confirmedWarnings = Set.copyOf(confirmedWarnings);
        decisionReference = decisionReference == null || decisionReference.isBlank()
            ? null
            : decisionReference.strip();
    }

    public AssignMembership(long memberId, long bodyId, long memberTypeId, LocalDate validFrom, @Nullable LocalDate validTo) {
        this(memberId, bodyId, memberTypeId, validFrom, validTo, null, Set.of());
    }

    public AssignMembership withDecisionReference(@Nullable String reference) {
        return new AssignMembership(memberId, bodyId, memberTypeId, validFrom, validTo, reference, confirmedWarnings);
    }

    public AssignMembership withConfirmedWarnings(Set<FunctionWarning.Code> codes) {
        return new AssignMembership(memberId, bodyId, memberTypeId, validFrom, validTo, decisionReference, codes);
    }
}
