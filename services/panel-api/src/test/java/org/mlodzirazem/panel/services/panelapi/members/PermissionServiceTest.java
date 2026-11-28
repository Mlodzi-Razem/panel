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
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipService;
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
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.SUSPENSIONS_MANAGE;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.SUSPENSIONS_READ;
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

    @Autowired
    BodyMembershipService bodyMembershipService;

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
    void plainBoardMemberAndCommissionerGetTheSameLocalPermissions() {
        TMember vice = newMember("Vera", "Vice", "lubelskie");
        TMember commissioner = newMember("Cam", "Commissioner", "lubuskie");
        TMember target = newMember("Tom", "Target", "lubelskie");
        TMember target2 = newMember("Tia", "Target", "lubuskie");

        grant(vice, board("lubelskie"), "MEMBER");
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
        assertThat(permissions.canGlobally(FEES_READ)).isTrue();
        assertThat(permissions.canGlobally(FEES_MANAGE)).isFalse();
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
    void nationalItProxyHoldsTheFullScopeButNeverApprovesAdmissions() {
        TMember proxy = newMember("Ida", "Proxy", "pomorskie");
        TMember target = newMember("Tara", "Target", "opolskie");
        grant(proxy, body("national-it-proxy"), "PROXY");

        EffectivePermissions permissions = permissionService.of(proxy.getId());

        assertThat(permissions.global()).containsExactlyInAnyOrder(CONTACT_PERSONAL_DATA_READ,
            PermissionType.CONTACT_PERSONAL_DATA_MANAGE, FORMAL_PERSONAL_DATA_READ, FORMAL_PERSONAL_DATA_MANAGE,
            BODIES_MANAGE, PermissionType.DIRECTORY_GROUPS_MANAGE, PermissionType.DIRECTORY_USERS_MANAGE, FEES_READ,
            FEES_MANAGE, RECRUITMENT_READ);
        assertThat(permissions.canGlobally(PermissionType.RECRUITMENT_APPROVE)).isFalse();
        assertThat(permissionService.can(proxy.getId(), FORMAL_PERSONAL_DATA_MANAGE, target.getId())).isTrue();
        assertThat(permissions.perDistrict()).hasSize(17);
    }

    @Test
    void voidedMandatesGiveNoPermissionsButStayInTheTable() {
        TMember coordinator = newMember("Vic", "Void", "mazowieckie");
        TMember target = newMember("Ida", "Target", "mazowieckie");
        grant(coordinator, board("mazowieckie"), "COORDINATOR");

        assertThat(permissionService.can(coordinator.getId(), FORMAL_PERSONAL_DATA_MANAGE, target.getId())).isTrue();

        long membershipId = jdbc.queryForObject(
            "SELECT id FROM body_memberships WHERE member_id = ?", Long.class, coordinator.getId());
        bodyMembershipService.voidMembership(membershipId, "wpis omylkowy", null);

        assertThat(permissionService.can(coordinator.getId(), FORMAL_PERSONAL_DATA_MANAGE, target.getId())).isFalse();
        assertThat(permissionService.of(coordinator.getId()).canGlobally(BODIES_MANAGE)).isFalse();
        // The row is the voided history — permissions lost it, the auditor must still find it.
        assertThat(jdbc.queryForObject(
            "SELECT status::text FROM body_memberships WHERE id = ?", String.class, membershipId))
            .isEqualTo("VOIDED");
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
    void districtTreasurerHasAtLeastEverythingOfAPlainBoardMemberInOwnDistrictOnly() {
        // Art. 43 ust. 3: the treasurer is a member of the ZO (owner decision: never fewer rights than one).
        TMember treasurer = newMember("Tina", "Cash", "podlaskie");
        TMember plainMember = newMember("Mia", "Member", "podlaskie");
        TMember target = newMember("Tad", "Target", "podlaskie");
        TMember elsewhere = newMember("Else", "Where", "slaskie");
        grant(treasurer, board("podlaskie"), "TREASURER");
        grant(plainMember, board("podlaskie"), "MEMBER");

        EffectivePermissions permissions = permissionService.of(treasurer.getId());
        Long ownDistrict = district("podlaskie").getId();

        // G-10: suspensions belong to the board, not to the treasurer (the treasurer does not suspend)
        assertThat(permissions.inDistrict(ownDistrict))
            .containsAll(permissionService.of(plainMember.getId()).inDistrict(ownDistrict).stream()
                                          .filter(p -> p != SUSPENSIONS_READ && p != SUSPENSIONS_MANAGE)
                                          .toList())
            .contains(FEES_READ)
            .doesNotContain(FEES_MANAGE, BODIES_MANAGE, SUSPENSIONS_READ, SUSPENSIONS_MANAGE);
        assertThat(permissionService.can(treasurer.getId(), FORMAL_PERSONAL_DATA_MANAGE, target.getId())).isTrue();
        assertThat(permissions.inDistrict(district("slaskie").getId())).isEmpty();
        assertThat(permissions.canGlobally(FEES_READ)).isFalse();
        assertThat(permissionService.can(treasurer.getId(), FEES_READ, elsewhere.getId())).isFalse();
        assertThat(permissionService.can(treasurer.getId(), CONTACT_PERSONAL_DATA_READ, elsewhere.getId())).isFalse();
    }

    @Test
    void soleBoardMemberHoldingCoordinatorAndTreasurerGetsTheUnionOfBothGroups() {
        // Art. 43 ust. 4: in a one-person ZO the member is the coordinator and the treasurer.
        TMember sole = newMember("Solveig", "Solo", "lubuskie");
        TMember target = newMember("Tad", "Target", "lubuskie");
        grant(sole, board("lubuskie"), "COORDINATOR");
        grant(sole, board("lubuskie"), "TREASURER");

        assertThat(permissionService.of(sole.getId()).inDistrict(district("lubuskie").getId()))
            .contains(FORMAL_PERSONAL_DATA_MANAGE, FEES_READ);
        assertThat(permissionService.can(sole.getId(), FEES_READ, target.getId())).isTrue();
        assertThat(permissionService.can(sole.getId(), FORMAL_PERSONAL_DATA_MANAGE, target.getId())).isTrue();
    }

    @Test
    void nationalBoardMemberHasTheNationalBoardPermissionsAndCannotManageFees() {
        TMember member = newMember("Zdzisław", "Krajowy", "opolskie");
        grant(member, body("national-board"), "MEMBER");

        EffectivePermissions permissions = permissionService.of(member.getId());

        assertThat(permissions.canGlobally(BODIES_MANAGE)).isTrue();
        assertThat(permissions.canGlobally(PermissionType.RECRUITMENT_APPROVE)).isTrue();
        assertThat(permissions.canGlobally(FEES_READ)).isTrue();
        assertThat(permissions.canGlobally(FEES_MANAGE)).isFalse();
    }

    @Test
    void nationalCouncilSeatsGiveNoPermissions() {
        // Owner decision: in the panel the RK is informational only, so none of its seats (nor the Presidium)
        // carries a permission.
        TMember delegate = newMember("Dela", "Delegat", "opolskie");
        TMember presidium = newMember("Pola", "Prezydium", "opolskie");
        grant(delegate, body("national-council"), "ZO_REPRESENTATIVE");
        grant(presidium, body("national-council"), "PRESIDIUM_MEMBER");

        assertThat(permissionService.of(delegate.getId()).isEmpty()).isTrue();
        assertThat(permissionService.of(presidium.getId()).isEmpty()).isTrue();
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
        grantDistrictReader(both, "lodzkie");

        EffectivePermissions permissions = permissionService.of(both.getId());

        assertThat(permissions.districtsWith(FORMAL_PERSONAL_DATA_MANAGE)).containsExactly(district("opolskie").getId());
        assertThat(permissions.districtsWith(FEES_READ)).containsExactlyInAnyOrder(district("opolskie").getId(),
            district("lodzkie").getId());
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
