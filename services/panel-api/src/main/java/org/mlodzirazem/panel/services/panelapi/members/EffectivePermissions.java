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

import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionScope;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class EffectivePermissions {
    private final Set<PermissionGrant> grants;
    private final Set<Long> allDistrictIds;

    public EffectivePermissions(Collection<PermissionGrant> grants, Collection<Long> allDistrictIds) {
        this.grants = Set.copyOf(grants);
        this.allDistrictIds = Set.copyOf(allDistrictIds);
    }

    public static EffectivePermissions none() {
        return new EffectivePermissions(Set.of(), Set.of());
    }

    public Set<PermissionGrant> grants() {
        return grants;
    }

    public boolean isEmpty() {
        return grants.isEmpty();
    }

    public boolean can(PermissionType permission) {
        return grants.stream().anyMatch(g -> g.permission() == permission);
    }

    public boolean canAny(PermissionType... permissions) {
        for (PermissionType permission : permissions) {
            if (can(permission)) {
                return true;
            }
        }

        return false;
    }

    public boolean canGlobally(PermissionType permission) {
        return grants.stream().anyMatch(g -> g.permission() == permission && g.scope() == PermissionScope.GLOBAL);
    }

    public boolean canInDistrict(PermissionType permission, long districtId) {
        return grants.stream().anyMatch(g -> g.permission() == permission && appliesToDistrict(g, districtId));
    }

    public boolean canOnBody(PermissionType permission, long bodyId) {
        return grants.stream().anyMatch(g -> g.permission() == permission
            && (g.scope() == PermissionScope.GLOBAL
            || (g.scope() == PermissionScope.BODY && g.targetId() != null && g.targetId() == bodyId)));
    }

    /**
     * Whether the permission applies to a member with the given affiliations: GLOBAL always, LOCAL through the
     * member's primary district, and for boards of a supporting district only contact data read.
     */
    public boolean canForMember(PermissionType permission, MemberDistricts districts) {
        for (PermissionGrant grant : grants) {
            if (grant.permission() != permission) {
                continue;
            }

            if (grant.scope() == PermissionScope.GLOBAL) {
                return true;
            }

            if (grant.scope() == PermissionScope.LOCAL && grant.targetId() != null) {
                if (grant.targetId().equals(districts.primaryDistrictId())) {
                    return true;
                }

                if (permission == PermissionType.CONTACT_PERSONAL_DATA_READ
                    && districts.supportingDistrictIds().contains(grant.targetId())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * District ids where the permission applies (all districts for a GLOBAL grant). For
     * CONTACT_PERSONAL_DATA_READ the members visible there are those with the district as primary or supporting.
     */
    public Set<Long> districtsWith(PermissionType permission) {
        Set<Long> result = new HashSet<>();

        for (PermissionGrant grant : grants) {
            if (grant.permission() != permission) {
                continue;
            }

            if (grant.scope() == PermissionScope.GLOBAL) {
                return Set.copyOf(allDistrictIds);
            }

            if (grant.scope() == PermissionScope.LOCAL && grant.targetId() != null) {
                result.add(grant.targetId());
            }
        }

        return result;
    }

    public Set<PermissionType> global() {
        Set<PermissionType> result = EnumSet.noneOf(PermissionType.class);

        for (PermissionGrant grant : grants) {
            if (grant.scope() == PermissionScope.GLOBAL) {
                result.add(grant.permission());
            }
        }

        return result;
    }

    public Set<PermissionType> inDistrict(long districtId) {
        Set<PermissionType> result = EnumSet.noneOf(PermissionType.class);

        for (PermissionGrant grant : grants) {
            if (appliesToDistrict(grant, districtId)) {
                result.add(grant.permission());
            }
        }

        return result;
    }

    public Map<Long, Set<PermissionType>> perDistrict() {
        Map<Long, Set<PermissionType>> result = new HashMap<>();

        for (Long districtId : allDistrictIds) {
            Set<PermissionType> permissions = inDistrict(districtId);

            if (!permissions.isEmpty()) {
                result.put(districtId, permissions);
            }
        }

        return result;
    }

    public Set<PermissionType> onBody(long bodyId) {
        Set<PermissionType> result = EnumSet.noneOf(PermissionType.class);

        for (PermissionGrant grant : grants) {
            if (grant.scope() == PermissionScope.GLOBAL
                || (grant.scope() == PermissionScope.BODY && grant.targetId() != null && grant.targetId() == bodyId)) {
                result.add(grant.permission());
            }
        }

        return result;
    }

    private static boolean appliesToDistrict(PermissionGrant grant, long districtId) {
        return grant.scope() == PermissionScope.GLOBAL
            || (grant.scope() == PermissionScope.LOCAL && grant.targetId() != null && grant.targetId() == districtId);
    }
}
