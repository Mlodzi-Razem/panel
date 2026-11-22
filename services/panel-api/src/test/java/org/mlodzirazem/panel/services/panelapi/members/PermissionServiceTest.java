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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.BODIES_MANAGE;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.CONTACT_PERSONAL_DATA_READ;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.FEES_MANAGE;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.FEES_READ;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.FORMAL_PERSONAL_DATA_MANAGE;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.FORMAL_PERSONAL_DATA_READ;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.RECRUITMENT_READ;

@SpringBootTest
@PostgresTest
class PermissionServiceTest extends DomainTestSupport {
    @Autowired
    PermissionService permissionService;

    @Autowired
    AffiliationService affiliationService;

    @Test
    void districtBoardMemberHasLocalPermissionsOverMembersOfThatDistrictOnly() {
        TMember coordinator = newMember("Cora", "Board", "mazowieckie");
        TMember inDistrict = newMember("Ida", "Local", "mazowieckie");
        TMember elsewhere = newMember("Ewa", "Far", "slaskie");
        grant(coordinator, board("mazowieckie"), "COORDINATOR");

        assertThat(permissionService.can(coordinator.getId(), FORMAL_PERSONAL_DATA_MANAGE, inDistrict.getId())).isTrue();
        assertThat(permissionService.can(coordinator.getId(), CONTACT_PERSONAL_DATA_READ, inDistrict.getId())).isTrue();
        assertThat(permissionService.can(coordinator.getId(), FORMAL_PERSONAL_DATA_MANAGE, elsewhere.getId())).isFalse();
        assertThat(permissionService.can(coordinator.getId(), BODIES_MANAGE, inDistrict.getId())).isFalse();
        assertThat(permissionService.districtsWith(coordinator.getId(), FORMAL_PERSONAL_DATA_MANAGE))
            .containsExactly(district("mazowieckie").getId());
    }

    @Test
    void viceCoordinatorAndCommissionerGetTheSameLocalPermissions() {
        TMember vice = newMember("Vera", "Vice", "lubelskie");
        TMember commissioner = newMember("Cam", "Commissioner", "lubuskie");
        TMember target = newMember("Tom", "Target", "lubelskie");
        TMember target2 = newMember("Tia", "Target", "lubuskie");

        grant(vice, board("lubelskie"), "VICE_COORDINATOR");
        TBody commissionerBody = commissionerBody("lubuskie");
        grant(commissioner, commissionerBody, "COMMISSIONER");

        assertThat(permissionService.can(vice.getId(), FORMAL_PERSONAL_DATA_MANAGE, target.getId())).isTrue();
        assertThat(permissionService.can(commissioner.getId(), FORMAL_PERSONAL_DATA_MANAGE, target2.getId())).isTrue();
        assertThat(permissionService.can(commissioner.getId(), FORMAL_PERSONAL_DATA_MANAGE, target.getId())).isFalse();
    }

    @Test
    void nationalBoardHasGlobalPermissionsInEveryDistrict() {
        TMember secretary = newMember("Sam", "Secretary", "pomorskie");
        TMember target = newMember("Tara", "Target", "opolskie");
        grant(secretary, body("national-board"), "SECRETARY");

        EffectivePermissions permissions = permissionService.of(secretary.getId());

        assertThat(permissions.canGlobally(BODIES_MANAGE)).isTrue();
        assertThat(permissions.canGlobally(FEES_READ)).isFalse();
        assertThat(permissionService.can(secretary.getId(), FORMAL_PERSONAL_DATA_MANAGE, target.getId())).isTrue();
        assertThat(permissionService.districtsWith(secretary.getId(), FORMAL_PERSONAL_DATA_MANAGE)).hasSize(17);
        assertThat(permissions.perDistrict()).hasSize(17);
        assertThat(permissions.global()).contains(BODIES_MANAGE, FORMAL_PERSONAL_DATA_READ);
    }

    @Test
    void nationalTreasurerAdditionallyManagesFees() {
        TMember treasurer = newMember("Tess", "Treasurer", "pomorskie");
        grant(treasurer, body("national-board"), "TREASURER");

        EffectivePermissions permissions = permissionService.of(treasurer.getId());

        assertThat(permissions.canGlobally(FEES_READ)).isTrue();
        assertThat(permissions.canGlobally(FEES_MANAGE)).isTrue();
        assertThat(permissions.canGlobally(BODIES_MANAGE)).isTrue();
    }

    @Test
    void supportingDistrictBoardSeesContactDataOnly() {
        TMember coordinator = newMember("Cora", "Board", "mazowieckie");
        TMember visitor = newMember("Vic", "Visitor", "slaskie");
        affiliationService.addSupportingDistrict(visitor.getId(), district("mazowieckie").getId(), false);
        grant(coordinator, board("mazowieckie"), "COORDINATOR");

        assertThat(permissionService.can(coordinator.getId(), CONTACT_PERSONAL_DATA_READ, visitor.getId())).isTrue();
        assertThat(permissionService.can(coordinator.getId(), PermissionType.CONTACT_PERSONAL_DATA_MANAGE, visitor.getId()))
            .isFalse();
        assertThat(permissionService.can(coordinator.getId(), FORMAL_PERSONAL_DATA_READ, visitor.getId())).isFalse();
        assertThat(permissionService.can(coordinator.getId(), FORMAL_PERSONAL_DATA_MANAGE, visitor.getId())).isFalse();
    }

    @Test
    void districtTreasurerReadsContactDataFeesAndRecruitment() {
        TMember treasurer = newMember("Tina", "Cash", "podlaskie");
        TMember target = newMember("Tad", "Target", "podlaskie");
        grant(treasurer, body("district-podlaskie-treasurer"), "TREASURER");

        EffectivePermissions permissions = permissionService.of(treasurer.getId());

        assertThat(permissions.inDistrict(district("podlaskie").getId()))
            .containsExactlyInAnyOrder(CONTACT_PERSONAL_DATA_READ, FEES_READ, RECRUITMENT_READ);
        assertThat(permissionService.can(treasurer.getId(), CONTACT_PERSONAL_DATA_READ, target.getId())).isTrue();
        assertThat(permissionService.can(treasurer.getId(), FORMAL_PERSONAL_DATA_READ, target.getId())).isFalse();
        assertThat(permissions.inDistrict(district("slaskie").getId())).isEmpty();
    }

    @Test
    void functionsWithoutPermissionsGiveNothing() {
        TMember judge = newMember("Jude", "Judge", "opolskie");
        grant(judge, body("peer-court"), "CHAIR");

        assertThat(permissionService.of(judge.getId()).isEmpty()).isTrue();
    }

    @Test
    void endedFutureAndRemovedMembershipsDoNotCount() {
        TMember ended = newMember("Enid", "Ended", "opolskie");
        TMember future = newMember("Fay", "Future", "opolskie");
        TMember removed = newMember("Rob", "Removed", "opolskie");
        LocalDate today = LocalDate.now();
        grant(ended, board("opolskie"), "COORDINATOR", today.minusYears(2), today.minusDays(1));
        grant(future, board("opolskie"), "COORDINATOR", today.plusDays(5), null);
        grant(removed, board("opolskie"), "COORDINATOR");
        memberService.update(removed.getId(), Map.of(MemberField.STATUS, MemberStatus.REMOVED));

        assertThat(permissionService.of(ended.getId()).isEmpty()).isTrue();
        assertThat(permissionService.of(future.getId()).isEmpty()).isTrue();
        assertThat(permissionService.of(removed.getId()).isEmpty()).isTrue();
    }

    @Test
    void permissionsAreTheUnionOverMemberships() {
        TMember both = newMember("Bo", "Both", "opolskie");
        grant(both, board("opolskie"), "COORDINATOR");
        grant(both, body("district-lodzkie-treasurer"), "TREASURER");

        EffectivePermissions permissions = permissionService.of(both.getId());

        assertThat(permissions.districtsWith(FORMAL_PERSONAL_DATA_MANAGE)).containsExactly(district("opolskie").getId());
        assertThat(permissions.districtsWith(FEES_READ)).containsExactly(district("lodzkie").getId());
        assertThat(permissions.perDistrict().keySet())
            .containsExactlyInAnyOrder(district("opolskie").getId(), district("lodzkie").getId());
    }

    private TBody commissionerBody(String districtSlug) {
        TBody created = new TBody();
        created.setBodyTemplate(template(BodyKind.DISTRICT_COMMISSIONER));
        created.setParentBody(district(districtSlug));
        created.setName("Commissioner " + districtSlug);
        created.setCode("test-commissioner-" + districtSlug);
        return bodyRepository.saveAndFlush(created);
    }
}
