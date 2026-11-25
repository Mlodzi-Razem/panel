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

/**
 * Every statutory rule of an admission-ban uchwałа (statut jednolity 2026, art. 9 ust. 5) that the
 * service can refuse with. The codes are the back-office vocabulary; the public side never sees any
 * of them — the e-declaration of a banned person stays generically blocked (anti-enumeration, §0.5).
 */
public enum AdmissionBanRule {
    /** The PESEL is missing. */
    BAN_PERSONAL_ID_REQUIRED,
    /** The PESEL length, digits, checksum or birth-date part is invalid (same code as the e-form). */
    PERSONAL_ID_INVALID,
    /** The reason of the uchwałа is mandatory (G1 §7 item 5). */
    BAN_REASON_REQUIRED,
    /** The ZK uchwałа must be recorded with its signature. */
    BAN_DECISION_SIGNATURE_REQUIRED,
    /** The last day of the ban is mandatory. */
    BAN_PERIOD_REQUIRED,
    /** The last day is not after the first day. */
    BAN_PERIOD_INVALID,
    /** The ban period exceeds the statutory 365 days (art. 9 ust. 5). */
    BAN_PERIOD_OVER_365_DAYS,
    /** The ban would still run more than 365 days after its recording. */
    BAN_EXPIRY_OVER_365_DAYS,
    /** A recorded ban of the same person overlaps the requested period. */
    BAN_OVERLAPS_EXISTING
}
