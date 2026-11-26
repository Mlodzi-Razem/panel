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
 * A non-blocking finding about a body's current composition (a report, not a rule): the statute
 * says how many people an organ has, but the panel records outcomes of elections held elsewhere,
 * so a gap is information, never a reason to refuse a legitimate record.
 */
public record CompositionAdvisory(
    Code code,
    String message,
    int current,
    @Nullable String expected
) {
    public enum Code {
        /** Art. 43 ust. 1: the board's size is not one of the sizes allowed for the district's membership. */
        DISTRICT_BOARD_SIZE_MISMATCH,
        /** Art. 45 ust. 3: a local circle has at least 6 members. */
        LOCAL_CIRCLE_BELOW_MINIMUM,
        /** Art. 22 ust. 1, 26 ust. 1, 32 ust. 1, 35 ust. 1: fewer people than the statute's fixed size (a vacancy). */
        BODY_COMPOSITION_INCOMPLETE
    }
}
