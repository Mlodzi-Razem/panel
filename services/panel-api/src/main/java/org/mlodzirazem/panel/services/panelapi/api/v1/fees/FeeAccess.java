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

package org.mlodzirazem.panel.services.panelapi.api.v1.fees;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.fees.DistrictScope;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * The permission rules of the fees API, which the fee services leave to their caller.
 * <ul>
 * <li>FEES_READ: global, or local through the primary district of a member;</li>
 * <li>FEES_MANAGE: global only; every write and everything holding bank data of third parties;</li>
 * <li>reasons of adjustments: global FEES_MANAGE and the member themself.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class FeeAccess {
    private final CallerResolver callers;
    private final MemberService members;
    private final AffiliationService affiliations;

    record MemberAccess(Caller caller, TMember member) {
    }

    Caller reader() {
        Caller caller = callers.require();

        if (!caller.permissions().can(PermissionType.FEES_READ)) {
            throw new ForbiddenException("FEES_READ is required");
        }

        return caller;
    }

    Caller manager() {
        Caller caller = callers.require();

        if (!isManager(caller)) {
            throw new ForbiddenException("FEES_MANAGE is required");
        }

        return caller;
    }

    static boolean isManager(Caller caller) {
        return caller.permissions().canGlobally(PermissionType.FEES_MANAGE);
    }

    MemberAccess readerOf(long memberId) {
        Caller caller = reader();
        TMember member = members.get(memberId);

        if (!caller.permissions().canForMember(PermissionType.FEES_READ, affiliations.districtsOf(memberId))) {
            throw new ForbiddenException("FEES_READ does not cover this member");
        }

        return new MemberAccess(caller, member);
    }

    DistrictScope scope(Caller caller, @Nullable Long districtId) {
        var permissions = caller.permissions();

        if (permissions.canGlobally(PermissionType.FEES_READ)) {
            return districtId == null ? DistrictScope.all() : DistrictScope.of(List.of(districtId));
        }

        Set<Long> own = permissions.districtsWith(PermissionType.FEES_READ);

        if (own.isEmpty() || (districtId != null && !own.contains(districtId))) {
            throw new ForbiddenException("FEES_READ does not cover this district");
        }

        return DistrictScope.of(districtId == null ? own : Set.of(districtId));
    }
}
