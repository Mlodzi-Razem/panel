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

package org.mlodzirazem.panel.services.panelapi.members;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionScope;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;

/**
 * A permission granted by an active body membership. {@code targetId} is null for GLOBAL, the district body id
 * for LOCAL and the body id for BODY; {@code viaBodyId} is the body the member holds the function in.
 */
public record PermissionGrant(
    PermissionType permission,
    PermissionScope scope,
    @Nullable Long targetId,
    long viaBodyId
) {
}
