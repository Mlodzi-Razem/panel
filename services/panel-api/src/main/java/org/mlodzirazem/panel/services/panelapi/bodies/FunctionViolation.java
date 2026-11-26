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

public record FunctionViolation(
    Code code,
    String message,
    @Nullable Long conflictingMembershipId,
    @Nullable Long conflictingBodyId,
    @Nullable String conflictingBodyName
) {
    public enum Code {
        MEMBER_NOT_ELIGIBLE,
        /** Art. 7 ust. 2 pkt 1, art. 8 ust. 2: a function in an organ is for ordinary members. */
        FUNCTION_REQUIRES_ORDINARY_MEMBERSHIP,
        /** Art. 9 ust. 4, art. 10: not during the initial period or while the member is suspended. */
        FUNCTION_REQUIRES_ACTIVE_MEMBER,
        /** Art. 10 ust. 3: a suspension covers part of the mandate's period. */
        MEMBER_SUSPENDED_IN_PERIOD,
        /** Art. 11 ust. 1 pkt 5: the ordinary membership ends before the mandate starts. */
        MEMBERSHIP_ENDED_BEFORE_MANDATE,
        MEMBER_TYPE_MISMATCH,
        INVALID_PERIOD,
        DUPLICATE_MEMBERSHIP,
        INCOMPATIBLE_MEMBERSHIP,
        COMMISSIONER_VALID_TO_REQUIRED,
        /** Art. 44 ust. 3: a commissioner board exists for 186 days at most, once — no extensions. */
        COMMISSIONER_BODY_TERM_EXCEEDED,
        /** Art. 44 ust. 4: a post in at most one commissioner board. */
        COMMISSIONER_IN_ANOTHER_BODY,
        /** Two commissioner boards of one district never exist at the same time. */
        COMMISSIONER_BOARDS_OVERLAP,
        /**
         * Between the starts of two commissioner boards of one district a ZO mandate has to start (and before
         * the later board does): otherwise the later board only carries the earlier one past 186 days.
         */
        COMMISSIONER_ZO_REQUIRED_BETWEEN,
        /** The statute fixes how many people one function has (e.g. 2 co-chairs, 1 secretary of the ZK). */
        FUNCTION_SLOTS_FULL,
        /** The statute fixes the size of the organ (ZK 7, KSK 9, KKR 5, KKW 5, commissioner board 2). */
        BODY_COMPOSITION_FULL,
        DECISION_REFERENCE_TOO_LONG,
        LAST_BODIES_MANAGER
    }

    public FunctionViolation(Code code, String message) {
        this(code, message, null, null, null);
    }

    public static FunctionViolation of(
        Code code,
        String message,
        long conflictingMembershipId,
        long conflictingBodyId,
        String conflictingBodyName
    ) {
        return new FunctionViolation(code, message, conflictingMembershipId, conflictingBodyId, conflictingBodyName);
    }

    /**
     * Art. 18 ust. 1 pkt 6 of the statute (2026): a position within {ZK, KSK, KKR, KKW, ZO} cannot
     * overlap with another one from that group. The conflicting mandate and body are pointed at by
     * name and id so the caller can act without a stacktrace.
     */
    public static FunctionViolation incompatible(
        String message,
        long conflictingMembershipId,
        long conflictingBodyId,
        String conflictingBodyName
    ) {
        return new FunctionViolation(
            Code.INCOMPATIBLE_MEMBERSHIP,
            message,
            conflictingMembershipId,
            conflictingBodyId,
            conflictingBodyName
        );
    }
}
