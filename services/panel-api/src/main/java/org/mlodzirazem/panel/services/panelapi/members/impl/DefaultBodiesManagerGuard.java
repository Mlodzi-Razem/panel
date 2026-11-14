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
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.members.BodiesManagerGuard;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionScope;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.TPermissionGroup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
class DefaultBodiesManagerGuard implements BodiesManagerGuard {
    private static final long LOCK_KEY = 0x4D52_5041_4E45_4C01L;
    private static final long NO_ID = -1L;

    private final TBodyMembershipRepository memberships;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        memberships.lockTransactionally(LOCK_KEY);
    }

    @Override
    public boolean grantsGlobalManagement(
        TBodyTemplateMemberType memberType,
        LocalDate validFrom,
        @Nullable LocalDate validTo
    ) {
        TPermissionGroup group = memberType.getPermissionGroup();

        return validTo == null
            && !validFrom.isAfter(LocalDate.now(clock))
            && group.getScope() == PermissionScope.GLOBAL
            && group.getPermissions().contains(PermissionType.BODIES_MANAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean wouldLeaveNone(
        Collection<Long> membershipsLosingOpenEnd,
        @Nullable Long memberLosingPermissions,
        int successors
    ) {
        List<Long> excluded = new ArrayList<>(membershipsLosingOpenEnd);
        excluded.add(NO_ID);

        long before = count(List.of(NO_ID), NO_ID);
        long after = count(excluded, memberLosingPermissions == null ? NO_ID : memberLosingPermissions);

        return before > after && after + successors == 0;
    }

    private long count(Collection<Long> excludedMembershipIds, long excludedMemberId) {
        return memberships.countOpenEndedHolders(PermissionScope.GLOBAL, PermissionType.BODIES_MANAGE,
            MemberStatus.REMOVED, excludedMemberId, excludedMembershipIds, LocalDate.now(clock));
    }
}
