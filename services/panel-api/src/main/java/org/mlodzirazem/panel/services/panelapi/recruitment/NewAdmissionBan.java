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

package org.mlodzirazem.panel.services.panelapi.recruitment;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * A ZK uchwała banning a person from being admitted (statut jednolity 2026, art. 9 ust. 5), as it
 * enters the Panel. The formal identifier is the PESEL in the e-declaration format; the checksum
 * rules live in the service, so callers pass the value they have. A blank component is a rule
 * violation the service reports — never an implicit default (the reason, the uchwała signature
 * and the last day of the period are statutory input, G1 §7 item 5).
 *
 * @param personalId the PESEL of the banned person, digit-normalised by the service
 * @param effectiveFrom first day of the ban; {@code null} means today — the uchwała is recorded
 *                      on the day it is entered and takes effect immediately (art. 9 ust. 5)
 * @param effectiveUntil last day of the ban (inclusive), at most 365 days from the first
 * @param decisionSignature the signature of the ZK uchwała, e.g. {@code ZK/BAN/41/26}
 * @param reason the statutory reason of the uchwała; back-office only, never public (§0.5)
 * @param caseId optional anchor to the candidate case the uchwała closed; the ban outlives the
 *               case, so the identifier is the primary anchor either way (V0.0.7.00)
 */
public record NewAdmissionBan(
    String personalId,
    @Nullable LocalDate effectiveFrom,
    @Nullable LocalDate effectiveUntil,
    @Nullable String decisionSignature,
    @Nullable String reason,
    @Nullable Long caseId
) {
}
