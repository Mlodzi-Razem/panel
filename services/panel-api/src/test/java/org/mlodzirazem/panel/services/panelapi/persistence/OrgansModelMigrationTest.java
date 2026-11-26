/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The statute 2026 model migration (decision D12) on a database that already holds data: the way it runs on
 * stage. It needs its own container, because the Spring tests always migrate an empty database to the latest
 * version in one go.
 */
class OrgansModelMigrationTest {
    private static final String BEFORE = "0.0.11.01";
    private static final String GUARD_MESSAGE = "cannot remove the 2024 organs and functions";

    private static PostgreSQLContainer container;
    private static DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void startDatabase() {
        container = new PostgreSQLContainer("postgres:18.1-alpine");
        container.start();
        dataSource = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(),
            container.getPassword());
    }

    @AfterAll
    static void stopDatabase() {
        container.stop();
    }

    @BeforeEach
    void emptyDatabaseAtTheLastStageVersion() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        flyway(BEFORE).migrate();
    }

    @Test
    void upgradesAStageLikeDatabaseWithEmptyTreasurerOrgans() {
        assertThat(count("SELECT COUNT(*) FROM bodies WHERE code LIKE 'district-%-treasurer'")).isEqualTo(17);

        flyway(null).migrate();

        assertThat(count("SELECT COUNT(*) FROM bodies WHERE code LIKE 'district-%-treasurer'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM bodies WHERE code = 'national-council'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM bodies WHERE code LIKE 'district-%-board'")).isEqualTo(17);
        assertThat(types("DISTRICT_BOARD")).containsExactlyInAnyOrder("COORDINATOR", "TREASURER", "MEMBER");
        assertThat(types("NATIONAL_BOARD")).containsExactlyInAnyOrder("CO_CHAIR", "SECRETARY", "TREASURER", "MEMBER");
    }

    @Test
    void keepsMandatesOfFunctionsThatSurvive() {
        long member = member();
        mandate(member, "national-it-proxy", "NATIONAL_IT_PROXY", "PROXY");
        mandate(member, "district-mazowieckie-board", "DISTRICT_BOARD", "COORDINATOR");
        mandate(member, "national-board", "NATIONAL_BOARD", "TREASURER");

        flyway(null).migrate();

        // The ZK IT proxy mandate (the one live on stage) is untouched, like every other surviving mandate.
        assertThat(count("SELECT COUNT(*) FROM body_memberships")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM body_memberships bm JOIN bodies b ON b.id = bm.body_id "
            + "WHERE b.code = 'national-it-proxy'")).isEqualTo(1);
    }

    @Test
    void abortsWhenADistrictTreasurerOrganHasAMandate() {
        mandate(member(), "district-slaskie-treasurer", "DISTRICT_TREASURER", "TREASURER");

        assertAbortedAndNothingChanged();
    }

    @Test
    void abortsWhenTheViceCoordinatorOfAZoIsInUse() {
        mandate(member(), "district-lubelskie-board", "DISTRICT_BOARD", "VICE_COORDINATOR");

        assertAbortedAndNothingChanged();
    }

    @Test
    void abortsWhenTheViceChairOfTheZkIsInUse() {
        mandate(member(), "national-board", "NATIONAL_BOARD", "VICE_CHAIR");

        assertAbortedAndNothingChanged();
    }

    @Test
    void abortsWhenOnlyAClosedHistoricalMandateIsLeft() {
        // History is never removed or rewritten: an ended mandate blocks the migration like an open one.
        mandate(member(), "national-board", "NATIONAL_BOARD", "VICE_CHAIR");
        jdbc.update("UPDATE body_memberships SET valid_to = DATE '2026-02-01'");

        assertAbortedAndNothingChanged();
        assertThat(count("SELECT COUNT(*) FROM body_memberships")).isEqualTo(1);
    }

    @Test
    void abortsWhenAnotherBodyHangsUnderADistrictTreasurerOrgan() {
        // Any reference counts, not only mandates: here a subordinate body (the guard walks every foreign key).
        jdbc.update("INSERT INTO bodies (body_template_id, parent_body_id, name, code) "
            + "SELECT t.id, p.id, 'Test circle', 'test-circle' FROM body_templates t, bodies p "
            + "WHERE t.kind = 'LOCAL_CIRCLE' AND p.code = 'district-opolskie-treasurer'");

        assertAbortedAndNothingChanged();
    }

    private void assertAbortedAndNothingChanged() {
        assertThatThrownBy(() -> flyway(null).migrate())
            .isInstanceOf(FlywayException.class)
            .hasMessageContaining(GUARD_MESSAGE);

        // One transaction: a refused migration leaves the 2024 model exactly as it was.
        assertThat(count("SELECT COUNT(*) FROM bodies WHERE code LIKE 'district-%-treasurer'")).isEqualTo(17);
        assertThat(count("SELECT COUNT(*) FROM bodies WHERE code = 'national-council'")).isZero();
        assertThat(types("NATIONAL_BOARD")).contains("VICE_CHAIR");
        assertThat(types("DISTRICT_BOARD")).contains("VICE_COORDINATOR");
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");

        if (target != null) {
            config.target(target);
        }

        return config.load();
    }

    private long member() {
        return jdbc.queryForObject("INSERT INTO members (status, preferred_name_first, preferred_name_last, "
            + "formal_name_first, formal_name_last, birth_date) VALUES ('ACTIVE', 'Test', 'Member', 'Test', 'Member', "
            + "DATE '2003-03-03') RETURNING id", Long.class);
    }

    private void mandate(long memberId, String bodyCode, String kind, String typeCode) {
        jdbc.update("INSERT INTO body_memberships (member_id, body_id, member_type_id, valid_from) "
            + "SELECT ?, b.id, mt.id, DATE '2026-01-01' FROM bodies b JOIN body_templates t "
            + "ON t.id = b.body_template_id JOIN body_template_member_types mt ON mt.body_template_id = t.id "
            + "WHERE b.code = ? AND t.kind = ?::body_kind AND mt.code = ?", memberId, bodyCode, kind, typeCode);
        assertThat(count("SELECT COUNT(*) FROM body_memberships")).isPositive();
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private List<String> types(String kind) {
        return jdbc.queryForList("SELECT mt.code FROM body_template_member_types mt JOIN body_templates t "
            + "ON t.id = mt.body_template_id WHERE t.kind = ?::body_kind", String.class, kind);
    }
}
