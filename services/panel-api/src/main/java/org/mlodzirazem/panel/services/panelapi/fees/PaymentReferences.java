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

package org.mlodzirazem.panel.services.panelapi.fees;

/**
 * Personal payment references {@code MR-<number>-<check>}. The number is the member number when it is purely numeric
 * (up to 9 digits), otherwise the database id of the member; the check digit is the ISO 7064 mod 11-10 digit of the
 * number. References are derived, never stored.
 */
public interface PaymentReferences {
    /**
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException if there is no such member
     */
    String referenceOf(long memberId);

    /**
     * Finds a reference with a correct check digit anywhere in a transfer title (case, spaces, dashes and similar
     * separators are ignored) and returns the member id. Empty when there is none, the check digit is wrong, the
     * member is unknown or the title holds references of different members.
     */
    java.util.Optional<Long> parse(String transferTitle);
}
