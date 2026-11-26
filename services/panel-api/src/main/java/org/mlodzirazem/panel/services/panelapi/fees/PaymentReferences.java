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
 * Personal payment references. A member with a six-digit member number is referenced by that number, as in the
 * association's transfer title {@code <number> SKŁADKA CZŁONKOWSKA STOWARZYSZENIE MŁODZI RAZEM}. Others get
 * {@code MR-<number>-<check>}: the number is the member number when it is purely numeric (up to 9 digits), otherwise
 * the database id of the member; the check digit is the ISO 7064 mod 11-10 digit of the number. References are
 * derived, never stored.
 */
public interface PaymentReferences {
    /**
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException if there is no such member
     */
    String referenceOf(long memberId);

    /**
     * The member a transfer title identifies: by an {@code MR-} reference with a correct check digit anywhere in it,
     * or by a title that follows the association's template (the number first, then "składka członkowska",
     * optionally the name of the association and nothing else; {@code <number>@mlodzirazem.org} is accepted too).
     * Case, diacritics, spaces and punctuation are ignored. Empty when there is neither, the member is unknown or the
     * title holds references of different members.
     */
    java.util.Optional<Long> parse(String transferTitle);

    /**
     * Every member the title names, by an {@code MR-} reference or by a standalone member number, template or not.
     * Used to keep a confirmed sender IBAN from claiming a transfer that names somebody else.
     */
    java.util.Set<Long> membersNamed(String transferTitle);
}
