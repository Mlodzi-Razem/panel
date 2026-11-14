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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionScope;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EffectivePermissionsTest {
    private static final List<Long> DISTRICTS = List.of(1L, 2L, 3L);

    @Test
    void noneGrantsNothing() {
        EffectivePermissions none = EffectivePermissions.none();

        assertThat(none.isEmpty()).isTrue();
        assertThat(none.can(PermissionType.BODIES_MANAGE)).isFalse();
        assertThat(none.districtsWith(PermissionType.BODIES_MANAGE)).isEmpty();
    }

    @Test
    void globalGrantsApplyToEveryDistrict() {
        EffectivePermissions permissions = new EffectivePermissions(
            List.of(new PermissionGrant(PermissionType.FORMAL_PERSONAL_DATA_READ, PermissionScope.GLOBAL, null, 10)),
            DISTRICTS);

        assertThat(permissions.districtsWith(PermissionType.FORMAL_PERSONAL_DATA_READ)).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(permissions.canInDistrict(PermissionType.FORMAL_PERSONAL_DATA_READ, 2)).isTrue();
        assertThat(permissions.canForMember(PermissionType.FORMAL_PERSONAL_DATA_READ, new MemberDistricts(3L, Set.of()))).isTrue();
        assertThat(permissions.canForMember(PermissionType.FORMAL_PERSONAL_DATA_READ, new MemberDistricts(null, Set.of()))).isTrue();
    }

    @Test
    void localGrantsApplyToThePrimaryDistrictAndOnlyContactReadToSupportingOnes() {
        EffectivePermissions permissions = new EffectivePermissions(List.of(
            new PermissionGrant(PermissionType.CONTACT_PERSONAL_DATA_READ, PermissionScope.LOCAL, 1L, 10),
            new PermissionGrant(PermissionType.CONTACT_PERSONAL_DATA_MANAGE, PermissionScope.LOCAL, 1L, 10)),
            DISTRICTS);
        MemberDistricts primary = new MemberDistricts(1L, Set.of());
        MemberDistricts supporting = new MemberDistricts(2L, Set.of(1L));
        MemberDistricts other = new MemberDistricts(2L, Set.of());

        assertThat(permissions.canForMember(PermissionType.CONTACT_PERSONAL_DATA_READ, primary)).isTrue();
        assertThat(permissions.canForMember(PermissionType.CONTACT_PERSONAL_DATA_MANAGE, primary)).isTrue();
        assertThat(permissions.canForMember(PermissionType.CONTACT_PERSONAL_DATA_READ, supporting)).isTrue();
        assertThat(permissions.canForMember(PermissionType.CONTACT_PERSONAL_DATA_MANAGE, supporting)).isFalse();
        assertThat(permissions.canForMember(PermissionType.CONTACT_PERSONAL_DATA_READ, other)).isFalse();
        assertThat(permissions.inDistrict(1L)).containsExactlyInAnyOrder(PermissionType.CONTACT_PERSONAL_DATA_READ,
            PermissionType.CONTACT_PERSONAL_DATA_MANAGE);
        assertThat(permissions.inDistrict(2L)).isEmpty();
    }

    @Test
    void bodyGrantsApplyToTheBodyOnly() {
        EffectivePermissions permissions = new EffectivePermissions(
            List.of(new PermissionGrant(PermissionType.BODIES_MANAGE, PermissionScope.BODY, 7L, 7)), DISTRICTS);

        assertThat(permissions.canOnBody(PermissionType.BODIES_MANAGE, 7)).isTrue();
        assertThat(permissions.canOnBody(PermissionType.BODIES_MANAGE, 8)).isFalse();
        assertThat(permissions.districtsWith(PermissionType.BODIES_MANAGE)).isEmpty();
        assertThat(permissions.canForMember(PermissionType.BODIES_MANAGE, new MemberDistricts(1L, Set.of()))).isFalse();
        assertThat(permissions.onBody(7)).containsExactly(PermissionType.BODIES_MANAGE);
    }
}
