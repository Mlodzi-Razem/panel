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
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.members.EffectivePermissions;
import org.mlodzirazem.panel.services.panelapi.members.PermissionGrant;
import org.mlodzirazem.panel.services.panelapi.members.PermissionService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.TPermissionGroup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
class DefaultPermissionService implements PermissionService {
    private final TMemberRepository members;
    private final TBodyMembershipRepository bodyMemberships;
    private final TBodyRepository bodies;
    private final AffiliationService affiliations;
    private final CurrentMemberResolver currentMemberResolver;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public EffectivePermissions of(long memberId) {
        Optional<TMember> member = members.findById(memberId);

        if (member.isEmpty() || member.get().getStatus() == MemberStatus.REMOVED) {
            return EffectivePermissions.none();
        }

        Set<PermissionGrant> grants = new HashSet<>();

        for (TBodyMembership membership : bodyMemberships.findActiveByMemberId(memberId, LocalDate.now(clock))) {
            TBody body = membership.getBody();
            TPermissionGroup group = membership.getMemberType().getPermissionGroup();

            for (PermissionType permission : group.getPermissions()) {
                switch (group.getScope()) {
                    case GLOBAL -> grants.add(new PermissionGrant(permission, group.getScope(), null, body.getId()));
                    case BODY -> grants.add(new PermissionGrant(permission, group.getScope(), body.getId(), body.getId()));
                    case LOCAL -> {
                        Long districtId = districtOf(body);

                        if (districtId != null) {
                            grants.add(new PermissionGrant(permission, group.getScope(), districtId, body.getId()));
                        }
                    }
                }
            }
        }

        if (grants.isEmpty()) {
            return EffectivePermissions.none();
        }

        List<Long> districtIds = bodies.findAllByKind(BodyKind.DISTRICT).stream().map(TBody::getId).toList();

        return new EffectivePermissions(grants, districtIds);
    }

    @Override
    @Transactional
    public Optional<EffectivePermissions> of(UserAuthInfo userAuthInfo) {
        return currentMemberResolver.resolve(userAuthInfo).map(member -> of(member.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean can(long actorMemberId, PermissionType permission, long targetMemberId) {
        EffectivePermissions permissions = of(actorMemberId);

        if (permissions.canGlobally(permission)) {
            return true;
        }

        return permissions.canForMember(permission, affiliations.districtsOf(targetMemberId));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> districtsWith(long actorMemberId, PermissionType permission) {
        return of(actorMemberId).districtsWith(permission);
    }

    private static @Nullable Long districtOf(TBody body) {
        if (body.isDistrict()) {
            return body.getId();
        }

        TBody parent = body.getParentBody();
        return parent == null ? null : parent.getId();
    }
}
