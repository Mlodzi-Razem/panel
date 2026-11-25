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

import java.util.List;
import java.util.Optional;

/**
 * Sender IBANs confirmed for exactly one member (a confirmed IBAN identifies the member of an incoming transfer).
 * Errors: IBAN_INVALID, IBAN_ALREADY_CONFIRMED (conflict, other member), NOT_FOUND.
 */
public interface SenderIbans {
    /**
     * Idempotent for the same member.
     */
    ConfirmedIban confirm(long actorMemberId, String iban, long memberId);

    /**
     * Normalised lookup; empty for unknown or invalid IBANs.
     */
    Optional<Long> memberOf(String iban);

    List<ConfirmedIban> of(long memberId);

    void forget(long actorMemberId, String iban);
}
