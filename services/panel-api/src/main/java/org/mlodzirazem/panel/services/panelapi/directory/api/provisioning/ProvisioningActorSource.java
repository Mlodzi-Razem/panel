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

package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import java.util.Optional;

/**
 * Who is calling: lets provisioning check {@code DIRECTORY_USERS_MANAGE} / {@code DIRECTORY_GROUPS_MANAGE} without
 * the directory module depending on {@code members} (which already depends on the directory module). Implemented by
 * the {@code members} module. Additive to the published contract.
 */
public interface ProvisioningActorSource {
    /**
     * The member behind the current request, with what they may do globally; empty for system calls (jobs, listeners).
     */
    Optional<Actor> current();

    /**
     * @param memberId     {@code members.id} of the caller
     * @param manageUsers  holds {@code DIRECTORY_USERS_MANAGE} with GLOBAL scope
     * @param manageGroups holds {@code DIRECTORY_GROUPS_MANAGE} with GLOBAL scope
     */
    record Actor(long memberId, boolean manageUsers, boolean manageGroups) {
    }
}
