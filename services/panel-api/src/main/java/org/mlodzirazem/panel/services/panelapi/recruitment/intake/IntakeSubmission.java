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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake;

import org.jspecify.annotations.Nullable;

/**
 * The raw values of the public form.
 *
 * @param website the honeypot: hidden from people, so anything in it marks an automated sender
 * @param clientAddress the address of the sender as seen by a trusted proxy, only used in memory for the per-client
 *     limit; null when there is no trusted proxy
 */
public record IntakeSubmission(
    @Nullable String firstName,
    @Nullable String lastName,
    @Nullable String email,
    @Nullable String phone,
    @Nullable String birthDate,
    @Nullable Long districtBodyId,
    @Nullable String locality,
    boolean consent,
    @Nullable String consentVersion,
    @Nullable String formToken,
    @Nullable String website,
    @Nullable String clientAddress
) {
    /** Never prints the values: the record must not leak through a log statement. */
    @Override
    public String toString() {
        return "IntakeSubmission[redacted]";
    }
}
