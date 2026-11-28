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

package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

/**
 * What the guard and the executor need to know about an Entra user; read by object id, never stored. Personal data
 * (the user principal name, the {@code employeeId}) lives only in memory for the duration of one task.
 */
record GraphUser(
    String id,
    @Nullable String userPrincipalName,
    @Nullable String employeeId,
    @Nullable Boolean accountEnabled,
    @Nullable String userType,
    @Nullable Instant createdAt,
    Set<String> licenseSkus
) {
    @Override
    public String toString() {
        return "GraphUser[redacted]";
    }
}
