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

package org.mlodzirazem.panel.services.panelapi.api.v1.common;

import org.mlodzirazem.panel.services.panelapi.members.EffectivePermissions;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;

public record Caller(TMember member, EffectivePermissions permissions) {
    public long id() {
        return member.getId();
    }

    public boolean canReadMembers() {
        return permissions.canAny(PermissionType.CONTACT_PERSONAL_DATA_READ, PermissionType.FORMAL_PERSONAL_DATA_READ);
    }

    public boolean canManageBodies() {
        return permissions.canGlobally(PermissionType.BODIES_MANAGE);
    }
}
