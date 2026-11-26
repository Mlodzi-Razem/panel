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

/**
 * A rule the statute leaves to the people (art. 17 ust. 1, 17 ust. 3, 43): the assignment is
 * allowed, but only after the caller has explicitly confirmed this warning's {@link Code}.
 */
public record FunctionWarning(
    Code code,
    String message,
    @Nullable Integer consecutiveTerms,
    @Nullable Integer maxConsecutiveTerms,
    @Nullable Long affectedMembershipId,
    @Nullable Long affectedMemberId,
    @Nullable String affectedMemberName,
    @Nullable String affectedBodyName,
    boolean confirmed
) {
    public enum Code {
        /** Art. 17 ust. 1: more than 2 consecutive terms in the same organ (G-7: warn, never block). */
        TERM_LIMIT_EXCEEDED,
        SINGLE_PERSON_EXEMPTION_LOST,
        /**
         * Art. 17 ust. 3 and art. 29 ust. 3: the function requires full legal capacity. The panel
         * cannot know about an incapacitation, so the person recording the decision confirms it.
         */
        FULL_LEGAL_CAPACITY_REQUIRED,
        /** Art. 43 ust. 1: the board gets larger than the statute allows for the district's size. */
        DISTRICT_BOARD_SIZE_EXCEEDS_STATUTE
    }

    public static FunctionWarning termLimitExceeded(String message, int consecutiveTerms, int maxConsecutiveTerms) {
        return new FunctionWarning(Code.TERM_LIMIT_EXCEEDED, message, consecutiveTerms, maxConsecutiveTerms,
            null, null, null, null, false);
    }

    public static FunctionWarning singlePersonExemptionLost(
        String message,
        long affectedMembershipId,
        long affectedMemberId,
        String affectedMemberName,
        String affectedBodyName
    ) {
        return new FunctionWarning(Code.SINGLE_PERSON_EXEMPTION_LOST, message, null, null,
            affectedMembershipId, affectedMemberId, affectedMemberName, affectedBodyName, false);
    }

    public static FunctionWarning of(Code code, String message) {
        return new FunctionWarning(code, message, null, null, null, null, null, null, false);
    }

    public FunctionWarning withConfirmed(boolean value) {
        return new FunctionWarning(code, message, consecutiveTerms, maxConsecutiveTerms, affectedMembershipId,
            affectedMemberId, affectedMemberName, affectedBodyName, value);
    }
}
