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

package org.mlodzirazem.panel.services.panelapi.api.v1.districts;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@Transactional
public class DistrictsController {
    private final CallerResolver callers;
    private final BodyService bodies;

    public record DistrictResponse(long id, String name, String code, boolean canAct, List<PermissionType> permissions) {
    }

    @GetMapping("/districts")
    public List<DistrictResponse> districts() {
        Caller caller = callers.require();

        return bodies.districts()
                     .stream()
                     .sorted(Comparator.comparing(TBody::getName))
                     .map(d -> {
                         Set<PermissionType> permissions = caller.permissions().inDistrict(d.getId());

                         return new DistrictResponse(d.getId(), d.getName(), d.getCode(), !permissions.isEmpty(),
                             permissions.stream().sorted().toList());
                     })
                     .toList();
    }
}
