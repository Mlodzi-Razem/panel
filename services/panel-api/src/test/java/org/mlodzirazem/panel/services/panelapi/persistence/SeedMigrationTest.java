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

        for (String kind : List.of("DISTRICT_ASSEMBLY", "DISTRICT_BOARD")) {
            assertThat(count("SELECT COUNT(*) FROM bodies b JOIN body_templates t ON t.id = b.body_template_id "
                + "WHERE t.kind = '" + kind + "' AND b.parent_body_id IS NOT NULL")).isEqualTo(17);
        }

        assertThat(jdbc.queryForObject(
            "SELECT b.name FROM bodies b WHERE b.code = 'district-mazowieckie-board'", String.class))
            .isEqualTo("Zarząd Okręgu Mazowieckiego");
    }

    @Test
    void districtTreasurersAreFunctionsOfTheDistrictBoardNotOrgansOfTheirOwn() {
        // Statute 2026, art. 43 ust. 3 + decision D12: the 2024 model's 17 separate treasurer organs are gone.
        assertThat(count("SELECT COUNT(*) FROM bodies WHERE code LIKE 'district-%-treasurer'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM body_templates WHERE name = 'Osoba Skarbnicza Okręgu'")).isZero();
        assertThat(jdbc.queryForList("SELECT enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
            + "WHERE t.typname = 'body_kind'", String.class)).doesNotContain("DISTRICT_TREASURER");
        assertThat(count("SELECT COUNT(*) FROM body_templates WHERE kind = 'DISTRICT_BOARD'")).isEqualTo(1);
    }

    @Test
    void seedsNationalBodies() {
        assertThat(jdbc.queryForList("SELECT code FROM bodies WHERE parent_body_id IS NULL AND code NOT LIKE 'district-%'",
            String.class))
            .containsExactlyInAnyOrder("general-assembly", "national-board", "peer-court", "audit-commission",
                "electoral-commission", "press-spokesperson", "national-it-proxy", "national-council");
    }

    @Test
    void seedsBodyTemplatesWithKinds() {
        assertThat(jdbc.queryForList("SELECT kind::text FROM body_templates", String.class))
            .containsExactlyInAnyOrder("GENERAL_ASSEMBLY", "NATIONAL_BOARD", "NATIONAL_COUNCIL", "PEER_COURT",
                "AUDIT_COMMISSION", "ELECTORAL_COMMISSION", "PRESS_SPOKESPERSON", "NATIONAL_IT_PROXY", "TEAM",
                "DISTRICT", "DISTRICT_ASSEMBLY", "DISTRICT_BOARD", "DISTRICT_COMMISSIONER", "LOCAL_CIRCLE");
        // Art. 44 ust. 3: at most 186 days, once. Six calendar months (181-184 days) without extensions keep the
        // limit enforced inside the statute; the 2024 "3 months, 2 extensions" (about 275 days) is gone (D12).
        assertThat(jdbc.queryForObject("SELECT max_term_months FROM body_templates WHERE kind = 'DISTRICT_COMMISSIONER'",
            Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT max_extensions FROM body_templates WHERE kind = 'DISTRICT_COMMISSIONER'",
            Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT max_consecutive_terms FROM body_templates WHERE kind = 'DISTRICT_BOARD'",
            Integer.class)).isEqualTo(2);
    }

    @Test
    void seedsMemberTypesOfTheSpec() {
        // Art. 22 ust. 1: two co-chairs, a secretary, a treasurer and three members; no vice-chair (D12).
        assertThat(memberTypes("NATIONAL_BOARD")).containsExactlyInAnyOrder("CO_CHAIR", "SECRETARY", "TREASURER",
            "MEMBER");
        // Art. 24: the seats that follow from another organ and the five-person Presidium.
        assertThat(memberTypes("NATIONAL_COUNCIL")).containsExactlyInAnyOrder("ZO_REPRESENTATIVE", "ZK_MEMBER",
            "AFFILIATE_REPRESENTATIVE", "PRESIDIUM_CO_CHAIR", "PRESIDIUM_MEMBER");
        assertThat(memberTypes("PEER_COURT")).containsExactlyInAnyOrder("CHAIR", "VICE_CHAIR", "SECRETARY", "MEMBER");
        // Art. 43 ust. 2-3: coordinator, treasurer (both chosen by the ZO from its members) and plain members.
        assertThat(memberTypes("DISTRICT_BOARD")).containsExactlyInAnyOrder("COORDINATOR", "TREASURER", "MEMBER");
        assertThat(memberTypes("DISTRICT_COMMISSIONER")).containsExactly("COMMISSIONER");
        assertThat(memberTypes("GENERAL_ASSEMBLY")).containsExactly("DELEGATE");
        assertThat(memberTypes("PRESS_SPOKESPERSON")).containsExactly("SPOKESPERSON");
        assertThat(memberTypes("NATIONAL_IT_PROXY")).containsExactly("PROXY");
        assertThat(memberTypes("TEAM")).containsExactlyInAnyOrder("LEADER", "MEMBER");
        assertThat(memberTypes("LOCAL_CIRCLE")).containsExactlyInAnyOrder("COORDINATOR", "MEMBER");
        assertThat(memberTypes("DISTRICT")).isEmpty();
        assertThat(memberTypes("DISTRICT_ASSEMBLY")).isEmpty();
    }

    @Test
    void seedsPermissionGroupsPerSpec() {
        assertThat(permissions("District board")).containsExactlyInAnyOrder("CONTACT_PERSONAL_DATA_READ",
            "CONTACT_PERSONAL_DATA_MANAGE", "FORMAL_PERSONAL_DATA_READ", "FORMAL_PERSONAL_DATA_MANAGE",
            "RECRUITMENT_READ", "RECRUITMENT_MANAGE", "FEES_READ", "SUSPENSIONS_READ", "SUSPENSIONS_MANAGE");
        // Art. 43 ust. 3: the treasurer is a ZO member, so never less than "District board" (owner decision).
        // G-10: suspensions are the board's competence, not the treasurer's (the treasurer does not suspend).
        assertThat(permissions("District treasurer"))
            .containsAll(permissions("District board").stream().filter(p -> !p.startsWith("SUSPENSIONS_")).toList())
            .contains("FEES_READ")
            .doesNotContain("FEES_MANAGE", "BODIES_MANAGE", "RECRUITMENT_APPROVE", "SUSPENSIONS_READ",
                "SUSPENSIONS_MANAGE");
        assertThat(jdbc.queryForList("SELECT t.kind::text || ':' || mt.code FROM body_template_member_types mt "
            + "JOIN body_templates t ON t.id = mt.body_template_id "
            + "JOIN permission_group g ON g.id = mt.permission_group_id WHERE g.name = 'District treasurer'",
            String.class)).containsExactly("DISTRICT_BOARD:TREASURER");
        assertThat(permissions("National board")).containsExactlyInAnyOrder("CONTACT_PERSONAL_DATA_READ",
            "CONTACT_PERSONAL_DATA_MANAGE", "FORMAL_PERSONAL_DATA_READ", "FORMAL_PERSONAL_DATA_MANAGE",
            "BODIES_MANAGE", "DIRECTORY_GROUPS_MANAGE", "DIRECTORY_USERS_MANAGE",
            "RECRUITMENT_READ", "RECRUITMENT_APPROVE", "FEES_READ", "SUSPENSIONS_READ", "SUSPENSIONS_MANAGE");
        assertThat(permissions("Audit commission")).containsExactly("FEES_READ");
        assertThat(permissions("National board treasurer")).hasSize(9).contains("FEES_READ", "FEES_MANAGE");
        assertThat(permissions("National IT proxy")).containsExactlyInAnyOrder("CONTACT_PERSONAL_DATA_READ",
            "CONTACT_PERSONAL_DATA_MANAGE", "FORMAL_PERSONAL_DATA_READ", "FORMAL_PERSONAL_DATA_MANAGE",
            "BODIES_MANAGE", "DIRECTORY_GROUPS_MANAGE", "DIRECTORY_USERS_MANAGE", "FEES_READ", "FEES_MANAGE",
            "RECRUITMENT_READ");
        assertThat(permissions("No permissions")).isEmpty();
    }

    @Test
    void mapsMemberTypesToTheRightGroupScopes() {
        assertThat(groupOf("NATIONAL_BOARD", "SECRETARY")).isEqualTo("GLOBAL/National board");
        assertThat(groupOf("NATIONAL_BOARD", "TREASURER")).isEqualTo("GLOBAL/National board treasurer");
        assertThat(groupOf("NATIONAL_IT_PROXY", "PROXY")).isEqualTo("GLOBAL/National IT proxy");
        assertThat(groupOf("DISTRICT_BOARD", "COORDINATOR")).isEqualTo("LOCAL/District board");
        assertThat(groupOf("DISTRICT_COMMISSIONER", "COMMISSIONER")).isEqualTo("LOCAL/District board");
        assertThat(groupOf("DISTRICT_BOARD", "TREASURER")).isEqualTo("LOCAL/District treasurer");
        assertThat(groupOf("DISTRICT_BOARD", "MEMBER")).isEqualTo("LOCAL/District board");
        assertThat(groupOf("NATIONAL_BOARD", "MEMBER")).isEqualTo("GLOBAL/National board");
        for (String code : List.of("ZO_REPRESENTATIVE", "ZK_MEMBER", "AFFILIATE_REPRESENTATIVE", "PRESIDIUM_CO_CHAIR",
            "PRESIDIUM_MEMBER")) {
            assertThat(groupOf("NATIONAL_COUNCIL", code)).isEqualTo("BODY/No permissions");
        }
        assertThat(groupOf("ELECTORAL_COMMISSION", "CHAIR")).isEqualTo("BODY/No permissions");
        assertThat(groupOf("AUDIT_COMMISSION", "CHAIR")).isEqualTo("GLOBAL/Audit commission");
        assertThat(groupOf("AUDIT_COMMISSION", "MEMBER")).isEqualTo("GLOBAL/Audit commission");
        assertThat(groupOf("LOCAL_CIRCLE", "COORDINATOR")).isEqualTo("BODY/No permissions");
    }

    @Test
    void defaultFunctionTypeIsThePlainMemberOfTheBoards() {
        assertThat(jdbc.queryForList("SELECT mt.code FROM body_template_member_types mt JOIN body_templates t "
            + "ON t.id = mt.body_template_id WHERE t.kind IN ('NATIONAL_BOARD', 'DISTRICT_BOARD') AND mt.is_default",
            String.class)).containsExactlyInAnyOrder("MEMBER", "MEMBER");
    }

    @Test
    void nationalCouncilIsSeededOnceAndNoneOfItsFunctionsHoldsAPermission() {
        assertThat(count("SELECT COUNT(*) FROM bodies WHERE code = 'national-council'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT t.scope::text FROM bodies b JOIN body_templates t "
            + "ON t.id = b.body_template_id WHERE b.code = 'national-council'", String.class)).isEqualTo("GLOBAL");
        assertThat(jdbc.queryForObject("SELECT seeded FROM bodies WHERE code = 'national-council'", Boolean.class))
            .isTrue();
        // Owner decision: in the panel the RK is informational only. Every function it has, present or future,
        // sits in the BODY group "No permissions", and that group grants nothing.
        assertThat(jdbc.queryForList("SELECT DISTINCT g.scope::text || '/' || g.name FROM body_template_member_types mt "
            + "JOIN body_templates t ON t.id = mt.body_template_id "
            + "JOIN permission_group g ON g.id = mt.permission_group_id WHERE t.kind = 'NATIONAL_COUNCIL'",
            String.class)).containsExactly("BODY/No permissions");
        assertThat(count("SELECT COUNT(*) FROM body_template_member_types mt JOIN body_templates t "
            + "ON t.id = mt.body_template_id JOIN permission_group_permissions p "
            + "ON p.permission_group_id = mt.permission_group_id WHERE t.kind = 'NATIONAL_COUNCIL'")).isZero();
    }

    @Test
    void seedsIncompatibilitiesOfArticle21() {
        // Art. 18 ust. 1 pkt 6: ZK, KSK, KKR, KKW, ZO — 10 pairs. The RK is outside that group (pkt 7 is a rule
        // inside the RK), so the new organ adds no pair. G-2: only the three KSK/KKR/KKW x ZO pairs keep the
        // single-member exemption; ZK x ZO has none.
        assertThat(count("SELECT COUNT(*) FROM body_template_incompatibilities")).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM body_template_incompatibilities i JOIN body_templates a "
            + "ON a.id IN (i.body_template_id, i.other_body_template_id) WHERE a.kind = 'NATIONAL_COUNCIL'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM body_template_incompatibilities i JOIN body_templates a "
            + "ON a.id = i.body_template_id JOIN body_templates b ON b.id = i.other_body_template_id "
            + "WHERE i.single_member_exempt_template_id IS NOT NULL AND 'NATIONAL_BOARD' IN (a.kind::text, b.kind::text)"))
            .isZero();
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
