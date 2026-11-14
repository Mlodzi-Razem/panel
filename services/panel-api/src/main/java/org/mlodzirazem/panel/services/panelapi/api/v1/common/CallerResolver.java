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

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.members.PermissionService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CallerResolver {
    private final CurrentMemberResolver currentMemberResolver;
    private final PermissionService permissionService;

    public Optional<Caller> find() {
        return currentMemberResolver.current().map(this::caller);
    }

    public Caller require() {
        return find().orElseThrow(ApiException::notLinked);
    }

    public Caller requireBodiesManager() {
        Caller caller = require();

        if (!caller.canManageBodies()) {
            throw new ForbiddenException("BODIES_MANAGE is required");
        }

        return caller;
    }

    private Caller caller(TMember member) {
        return new Caller(member, permissionService.of(member.getId()));
    }
}
