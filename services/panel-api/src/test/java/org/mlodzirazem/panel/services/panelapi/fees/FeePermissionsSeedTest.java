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

package org.mlodzirazem.panel.services.panelapi.fees;

import org.mlodzirazem.panel.services.panelapi.fees.*;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.*;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.mlodzirazem.panel.services.panelapi.members.PermissionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.CONTACT_PERSONAL_DATA_READ;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.FEES_MANAGE;
import static org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType.FEES_READ;

@SpringBootTest
@PostgresTest
class FeePermissionsSeedTest extends FeesTestSupport {
    @Autowired
    PermissionService permissions;

    @Test
    void feesPermissionsFollowTheSpec() {
        TMember target = newMember("Tar", "Get", "mazowieckie");
        TMember elsewhere = newMember("Else", "Where", "slaskie");

        TMember zkTreasurer = newMember("Zk", "Treasurer", "slaskie");
        grant(zkTreasurer, body("national-board"), "TREASURER");
        TMember zkSecretary = newMember("Zk", "Secretary", "slaskie");
        grant(zkSecretary, body("national-board"), "SECRETARY");
        TMember kkr = newMember("Kkr", "Member", "slaskie");
        grant(kkr, body("audit-commission"), "MEMBER");
        TMember kkrChair = newMember("Kkr", "Chair", "slaskie");
        grant(kkrChair, body("audit-commission"), "CHAIR");
        TMember zo = newMember("Zo", "Coordinator", "slaskie");
        grant(zo, board("mazowieckie"), "COORDINATOR");
        TMember districtTreasurer = newMember("Dis", "Treasurer", "slaskie");
        grant(districtTreasurer, board("mazowieckie"), "TREASURER");
        TMember kkw = newMember("Kkw", "Member", "slaskie");
        grant(kkw, body("electoral-commission"), "MEMBER");

        assertThat(permissions.can(zkTreasurer.getId(), FEES_READ, target.getId())).isTrue();
        assertThat(permissions.can(zkTreasurer.getId(), FEES_MANAGE, target.getId())).isTrue();
        assertThat(permissions.can(zkSecretary.getId(), FEES_READ, elsewhere.getId())).isTrue();
        assertThat(permissions.can(zkSecretary.getId(), FEES_MANAGE, target.getId())).isFalse();
        for (TMember audit : List.of(kkr, kkrChair)) {
            assertThat(permissions.can(audit.getId(), FEES_READ, target.getId())).isTrue();
            assertThat(permissions.can(audit.getId(), FEES_READ, elsewhere.getId())).isTrue();
            assertThat(permissions.can(audit.getId(), FEES_MANAGE, target.getId())).isFalse();
            assertThat(permissions.can(audit.getId(), CONTACT_PERSONAL_DATA_READ, target.getId())).isFalse();
        }
        assertThat(permissions.can(zo.getId(), FEES_READ, target.getId())).isTrue();
        assertThat(permissions.can(zo.getId(), FEES_READ, elsewhere.getId())).isFalse();
        assertThat(permissions.can(zo.getId(), FEES_MANAGE, target.getId())).isFalse();
        assertThat(permissions.can(districtTreasurer.getId(), FEES_READ, target.getId())).isTrue();
        assertThat(permissions.can(districtTreasurer.getId(), FEES_READ, elsewhere.getId())).isFalse();
        assertThat(permissions.can(kkw.getId(), FEES_READ, target.getId())).isFalse();
    }

    @Test
    void theSeedIsIdempotent() throws Exception {
        int groups = jdbc.queryForObject("SELECT COUNT(*) FROM permission_group", Integer.class);
        int grants = jdbc.queryForObject("SELECT COUNT(*) FROM permission_group_permissions", Integer.class);
        String sql = StreamUtils.copyToString(
            new ClassPathResource("db/migration/0.0.12/V0.0.12.01__fees-permissions.sql").getInputStream(),
            StandardCharsets.UTF_8);

        jdbc.execute(sql);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permission_group", Integer.class)).isEqualTo(groups);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permission_group_permissions", Integer.class)).isEqualTo(grants);
    }
}
