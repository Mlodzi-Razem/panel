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

package org.mlodzirazem.panel.services.panelapi.persistence;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@PostgresTest
class SeedMigrationTest extends DomainTestSupport {
    @Test
    void seedsSeventeenDistrictsWithTheirBodies() {
        List<String> districts = jdbc.queryForList(
            "SELECT b.name FROM bodies b JOIN body_templates t ON t.id = b.body_template_id "
                + "WHERE t.kind = 'DISTRICT' ORDER BY b.name", String.class);

        assertThat(districts).hasSize(17)
                             .contains("Okręg Mazowiecki", "Okręg Śląski", "Okręg Zagranica",
                                 "Okręg Warmińsko-Mazurski", "Okręg Łódzki");

        for (String kind : List.of("DISTRICT_ASSEMBLY", "DISTRICT_BOARD", "DISTRICT_TREASURER")) {
            assertThat(count("SELECT COUNT(*) FROM bodies b JOIN body_templates t ON t.id = b.body_template_id "
                + "WHERE t.kind = '" + kind + "' AND b.parent_body_id IS NOT NULL")).isEqualTo(17);
        }

        assertThat(jdbc.queryForObject(
            "SELECT b.name FROM bodies b WHERE b.code = 'district-mazowieckie-board'", String.class))
            .isEqualTo("Zarząd Okręgu Mazowieckiego");
    }

    @Test
    void seedsNationalBodies() {
        assertThat(jdbc.queryForList("SELECT code FROM bodies WHERE parent_body_id IS NULL AND code NOT LIKE 'district-%'",
            String.class))
            .containsExactlyInAnyOrder("general-assembly", "national-board", "peer-court", "audit-commission",
                "electoral-commission", "press-spokesperson");
    }

    @Test
    void seedsBodyTemplatesWithKinds() {
        assertThat(jdbc.queryForList("SELECT kind::text FROM body_templates", String.class))
            .containsExactlyInAnyOrder("GENERAL_ASSEMBLY", "NATIONAL_BOARD", "PEER_COURT", "AUDIT_COMMISSION",
                "ELECTORAL_COMMISSION", "PRESS_SPOKESPERSON", "TEAM", "DISTRICT", "DISTRICT_ASSEMBLY",
                "DISTRICT_BOARD", "DISTRICT_TREASURER", "DISTRICT_COMMISSIONER", "LOCAL_CIRCLE");
        assertThat(jdbc.queryForObject("SELECT max_term_months FROM body_templates WHERE kind = 'DISTRICT_COMMISSIONER'",
            Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT max_extensions FROM body_templates WHERE kind = 'DISTRICT_COMMISSIONER'",
            Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT max_consecutive_terms FROM body_templates WHERE kind = 'DISTRICT_BOARD'",
            Integer.class)).isEqualTo(2);
    }

    @Test
    void seedsMemberTypesOfTheSpec() {
        assertThat(memberTypes("NATIONAL_BOARD")).containsExactlyInAnyOrder("CO_CHAIR", "VICE_CHAIR", "SECRETARY",
            "TREASURER");
        assertThat(memberTypes("PEER_COURT")).containsExactlyInAnyOrder("CHAIR", "VICE_CHAIR", "SECRETARY", "MEMBER");
        assertThat(memberTypes("DISTRICT_BOARD")).containsExactlyInAnyOrder("COORDINATOR", "VICE_COORDINATOR");
        assertThat(memberTypes("DISTRICT_TREASURER")).containsExactly("TREASURER");
        assertThat(memberTypes("DISTRICT_COMMISSIONER")).containsExactly("COMMISSIONER");
        assertThat(memberTypes("GENERAL_ASSEMBLY")).containsExactly("DELEGATE");
        assertThat(memberTypes("PRESS_SPOKESPERSON")).containsExactly("SPOKESPERSON");
        assertThat(memberTypes("TEAM")).containsExactlyInAnyOrder("LEADER", "MEMBER");
        assertThat(memberTypes("LOCAL_CIRCLE")).containsExactlyInAnyOrder("COORDINATOR", "MEMBER");
        assertThat(memberTypes("DISTRICT")).isEmpty();
        assertThat(memberTypes("DISTRICT_ASSEMBLY")).isEmpty();
    }

    @Test
    void seedsPermissionGroupsPerSpec() {
        assertThat(permissions("District board")).containsExactlyInAnyOrder("CONTACT_PERSONAL_DATA_READ",
            "CONTACT_PERSONAL_DATA_MANAGE", "FORMAL_PERSONAL_DATA_READ", "FORMAL_PERSONAL_DATA_MANAGE",
            "RECRUITMENT_READ", "RECRUITMENT_MANAGE");
        assertThat(permissions("District treasurer")).containsExactlyInAnyOrder("CONTACT_PERSONAL_DATA_READ",
            "FEES_READ", "RECRUITMENT_READ");
        assertThat(permissions("National board")).containsExactlyInAnyOrder("CONTACT_PERSONAL_DATA_READ",
            "CONTACT_PERSONAL_DATA_MANAGE", "FORMAL_PERSONAL_DATA_READ", "FORMAL_PERSONAL_DATA_MANAGE",
            "BODIES_MANAGE", "DIRECTORY_GROUPS_MANAGE", "DIRECTORY_USERS_MANAGE",
            "RECRUITMENT_READ", "RECRUITMENT_APPROVE");
        assertThat(permissions("National board treasurer")).hasSize(9).contains("FEES_READ", "FEES_MANAGE");
        assertThat(permissions("No permissions")).isEmpty();
    }

    @Test
    void mapsMemberTypesToTheRightGroupScopes() {
        assertThat(groupOf("NATIONAL_BOARD", "SECRETARY")).isEqualTo("GLOBAL/National board");
        assertThat(groupOf("NATIONAL_BOARD", "TREASURER")).isEqualTo("GLOBAL/National board treasurer");
        assertThat(groupOf("DISTRICT_BOARD", "COORDINATOR")).isEqualTo("LOCAL/District board");
        assertThat(groupOf("DISTRICT_COMMISSIONER", "COMMISSIONER")).isEqualTo("LOCAL/District board");
        assertThat(groupOf("DISTRICT_TREASURER", "TREASURER")).isEqualTo("LOCAL/District treasurer");
        assertThat(groupOf("ELECTORAL_COMMISSION", "CHAIR")).isEqualTo("BODY/No permissions");
        assertThat(groupOf("LOCAL_CIRCLE", "COORDINATOR")).isEqualTo("BODY/No permissions");
    }

    @Test
    void seedsIncompatibilitiesOfArticle21() {
        assertThat(count("SELECT COUNT(*) FROM body_template_incompatibilities")).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM body_template_incompatibilities WHERE single_member_exempt_template_id IS NOT NULL"))
            .isEqualTo(3);
    }

    @Test
    void formalNameIsGeneratedAndSearchable() {
        var member = newMember("Anna", "Nowak", "mazowieckie");

        assertThat(jdbc.queryForObject("SELECT formal_name FROM members WHERE id = ?", String.class, member.getId()))
            .isEqualTo("Anna Nowak");
        assertThat(jdbc.queryForObject("SELECT trgm_search FROM members WHERE id = ?", String.class, member.getId()))
            .contains("Anna Nowak");
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private List<String> memberTypes(String kind) {
        return jdbc.queryForList("SELECT mt.code FROM body_template_member_types mt "
            + "JOIN body_templates t ON t.id = mt.body_template_id WHERE t.kind = ?::body_kind", String.class, kind);
    }

    private List<String> permissions(String group) {
        return jdbc.queryForList("SELECT p.permission_type::text FROM permission_group_permissions p "
            + "JOIN permission_group g ON g.id = p.permission_group_id WHERE g.name = ?", String.class, group);
    }

    private String groupOf(String kind, String code) {
        return jdbc.queryForObject("SELECT g.scope::text || '/' || g.name FROM body_template_member_types mt "
            + "JOIN body_templates t ON t.id = mt.body_template_id "
            + "JOIN permission_group g ON g.id = mt.permission_group_id "
            + "WHERE t.kind = ?::body_kind AND mt.code = ?", String.class, kind, code);
    }
}
