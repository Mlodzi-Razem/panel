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

public record FunctionViolation(Code code, String message) {
    public enum Code {
        MEMBER_NOT_ELIGIBLE,
        MEMBER_TYPE_MISMATCH,
        INVALID_PERIOD,
        DUPLICATE_MEMBERSHIP,
        INCOMPATIBLE_LATER_MANDATE,
        COMMISSIONER_VALID_TO_REQUIRED,
        COMMISSIONER_TERM_TOO_LONG,
        COMMISSIONER_EXTENSIONS_EXCEEDED,
        COMMISSIONER_TOTAL_TOO_LONG,
        LAST_BODIES_MANAGER
    }
}
