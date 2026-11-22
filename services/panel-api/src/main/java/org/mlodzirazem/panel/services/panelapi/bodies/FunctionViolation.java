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
        MEMBER_TYPE_MISMATCH,
        INVALID_PERIOD,
        DUPLICATE_MEMBERSHIP,
        INCOMPATIBLE_MEMBERSHIP,
        COMMISSIONER_VALID_TO_REQUIRED,
        COMMISSIONER_TERM_TOO_LONG,
        COMMISSIONER_EXTENSIONS_EXCEEDED,
        COMMISSIONER_TOTAL_TOO_LONG,
        LAST_BODIES_MANAGER
    }

    public FunctionViolation(Code code, String message) {
        this(code, message, null, null, null);
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
