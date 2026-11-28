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

package org.mlodzirazem.panel.services.panelapi.members.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningActorSource;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.members.EffectivePermissions;
import org.mlodzirazem.panel.services.panelapi.members.PermissionService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Tells directory provisioning who is calling and whether that member holds {@code DIRECTORY_USERS_MANAGE} or
 * {@code DIRECTORY_GROUPS_MANAGE} globally. Lives here because {@code members} already depends on the directory
 * module, not the other way round.
 */
@Component
@RequiredArgsConstructor
class ProvisioningActorAdapter implements ProvisioningActorSource {
    private final CurrentMemberResolver currentMember;
    private final PermissionService permissions;

    @Override
    public Optional<Actor> current() {
        return currentMember.current().map(member -> {
            EffectivePermissions effective = permissions.of(member.getId());
            return new Actor(member.getId(),
                effective.canGlobally(PermissionType.DIRECTORY_USERS_MANAGE),
                effective.canGlobally(PermissionType.DIRECTORY_GROUPS_MANAGE));
        });
    }
}
