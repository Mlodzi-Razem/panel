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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class SchemaFixesTest {
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("TRUNCATE suspensions, body_template_member_types, bodies, body_templates, "
            + "permission_group, audited_operations, member_emails, members CASCADE");
    }

    @Test
    void membersWithoutPrivateEmailsCanCoexist() {
        long first = insertMember("A", null, "One", "Form One", null, null, "{}", "{}");
        long second = insertMember("B", null, "Two", "Form Two", null, null, "{}", "{}");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void duplicateDomainEmailAcrossMembersIsRejected() {
        insertMember("A", null, "One", "Form One", null, "a@example.org", "{}", "{}");

        assertSqlState(UNIQUE_VIOLATION, () ->
            insertMember("B", null, "Two", "Form Two", null, "A@Example.ORG", "{}", "{}"));
    }

    @Test
    void domainEmailMatchingOtherMembersAliasIsRejected() {
        insertMember("A", null, "One", "Form One", null, null, array("alias@example.org"), "{}");

        assertSqlState(UNIQUE_VIOLATION, () ->
            insertMember("B", null, "Two", "Form Two", null, "ALIAS@example.org", "{}", "{}"));
    }

    @Test
    void duplicateAliasAcrossMembersIsRejected() {
        insertMember("A", null, "One", "Form One", null, null, array("alias@example.org"), "{}");

        assertSqlState(UNIQUE_VIOLATION, () ->
            insertMember("B", null, "Two", "Form Two", null, null, array("x@example.org", "Alias@example.org"), "{}"));
    }

    @Test
    void duplicatePrivateEmailAcrossMembersIsRejected() {
        insertMember("A", null, "One", "Form One", null, null, "{}", array("me@home.pl", "other@home.pl"));

        assertSqlState(UNIQUE_VIOLATION, () ->
            insertMember("B", null, "Two", "Form Two", null, null, "{}", array("third@home.pl", "ME@home.pl")));
    }

    @Test
    void privateEmailMatchingOtherMembersDomainEmailIsRejected() {
        insertMember("A", null, "One", "Form One", null, "a@example.org", "{}", "{}");

        assertSqlState(UNIQUE_VIOLATION, () ->
            insertMember("B", null, "Two", "Form Two", null, null, "{}", array("a@EXAMPLE.org")));
    }

    @Test
    void duplicatesWithinOneMembersArraysAreRejected() {
        assertSqlState(UNIQUE_VIOLATION, () ->
            insertMember("A", null, "One", "Form One", null, null, "{}", array("x@home.pl", "X@home.pl")));
        assertSqlState(UNIQUE_VIOLATION, () ->
            insertMember("A", null, "One", "Form One", null, "x@example.org", array("X@example.org"), "{}"));
    }

    @Test
    void updatingEmailsIsCheckedAndFreesOldValues() {
        long first = insertMember("A", null, "One", "Form One", null, null, "{}", array("old@home.pl"));
        insertMember("B", null, "Two", "Form Two", null, null, "{}", array("taken@home.pl"));

        assertSqlState(UNIQUE_VIOLATION, () ->
            jdbc.update("UPDATE members SET private_emails = ?::text[] WHERE id = ?", array("TAKEN@home.pl"), first));

        jdbc.update("UPDATE members SET private_emails = ?::text[] WHERE id = ?", array("new@home.pl"), first);
        insertMember("C", null, "Three", "Form Three", null, null, "{}", array("old@home.pl"));
    }

    @Test
    void trgmSearchIsNonNullForMemberWithoutPseudonymAndMiddleName() {
        long id = insertMember("Jan", null, "Kowalski", "Jan Adam Kowalski", null, "jan@example.org", "{}", "{}");

        String search = jdbc.queryForObject("SELECT trgm_search FROM members WHERE id = ?", String.class, id);

        assertThat(search).contains("Jan Kowalski", "Jan Adam Kowalski", "jan@example.org");
    }

    @Test
    void trgmSearchContainsMiddleNamePseudonymAndAliases() {
        long id = insertMember("Jan", "Adam", "Kowalski", "Jan Adam Kowalski", "Janek", null,
            array("alias@example.org"), array("jan@home.pl"));

        String search = jdbc.queryForObject("SELECT trgm_search FROM members WHERE id = ?", String.class, id);

        assertThat(search).contains("Jan Adam Kowalski", "Janek", "alias@example.org", "jan@home.pl");
    }

    @Test
    void bodiesWithNullEmailHaveTrgmSearch() {
        jdbc.update("INSERT INTO bodies (name) VALUES ('Zarząd')");
        jdbc.update("INSERT INTO bodies (name, email) VALUES ('Rada', 'rada@example.org')");

        assertThat(jdbc.queryForObject("SELECT trgm_search FROM bodies WHERE name = 'Zarząd'", String.class))
            .isEqualTo("Zarząd");
        assertThat(jdbc.queryForObject("SELECT trgm_search FROM bodies WHERE name = 'Rada'", String.class))
            .isEqualTo("Rada rada@example.org");
    }

    @Test
    void firstMemberIdIsAtLeastOne() {
        jdbc.execute("ALTER SEQUENCE seq_members_id RESTART");

        long id = insertMember("A", null, "One", "Form One", null, null, "{}", "{}");

        assertThat(id).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT seqmin FROM pg_sequence WHERE seqrelid = 'seq_members_id'::regclass", Long.class)).isEqualTo(1);
    }

    @Test
    void suspensionRequiresExistingMember() {
        long body = insertBody();

        assertSqlState(FOREIGN_KEY_VIOLATION, () -> jdbc.update(
            "INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start) "
                + "VALUES (999999, ?, CURRENT_DATE)", body));
    }

    @Test
    void suspensionIdIsGenerated() {
        long member = insertMember("A", null, "One", "Form One", null, null, "{}", "{}");
        long body = insertBody();

        jdbc.update("INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start) "
            + "VALUES (?, ?, CURRENT_DATE)", member, body);

        assertThat(jdbc.queryForObject("SELECT id FROM suspensions", Long.class)).isPositive();
    }

    @Test
    void memberTypeRequiresExistingPermissionGroup() {
        Long template = jdbc.queryForObject(
            "INSERT INTO body_templates (scope, name) VALUES ('LOCAL', 't') RETURNING id", Long.class);

        assertSqlState(FOREIGN_KEY_VIOLATION, () -> jdbc.update(
            "INSERT INTO body_template_member_types (name, is_default, body_template_id, permission_group_id) "
                + "VALUES ('n', TRUE, ?, 999999)", template));
    }

    @Test
    void auditedOperationTimestampIsTimestamptz() {
        assertThat(jdbc.queryForObject(
            "SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'audited_operations' AND column_name = 'operation_timestamp'",
            String.class)).isEqualTo("timestamp with time zone");
    }

    @Test
    void directoryUserIdMustBeUniqueButMayBeNull() {
        long first = insertMember("A", null, "One", "Form One", null, null, "{}", "{}");
        long second = insertMember("B", null, "Two", "Form Two", null, null, "{}", "{}");
        insertMember("C", null, "Three", "Form Three", null, null, "{}", "{}");

        jdbc.update("UPDATE members SET directory_user_id = 'sub-1' WHERE id = ?", first);

        assertSqlState(UNIQUE_VIOLATION, () ->
            jdbc.update("UPDATE members SET directory_user_id = 'sub-1' WHERE id = ?", second));
    }

    private long insertBody() {
        Long id = jdbc.queryForObject("INSERT INTO bodies (name) VALUES ('b') RETURNING id", Long.class);
        assertThat(id).isNotNull();
        return id;
    }

    private long insertMember(
        String first,
        String middle,
        String last,
        String formal,
        String pseudonym,
        String domainEmail,
        String aliases,
        String privateEmails
    ) {
        Long id = jdbc.queryForObject("""
                INSERT INTO members (status, preferred_name_first, preferred_name_middle, preferred_name_last,
                                     formal_name, pseudonym, domain_email, domain_email_aliases, private_emails,
                                     birth_date, address_country_code, address_postal_code, address_state,
                                     address_province, address_city, address_street, address_building,
                                     address_apartment)
                VALUES ('ACTIVE', ?, ?, ?, ?, ?, ?, ?::text[], ?::text[],
                        DATE '1990-01-01', 'POL', '00-001', 's', 'p', 'c', 'st', '1', '1')
                RETURNING id
                """,
            Long.class, first, middle, last, formal, pseudonym, domainEmail, aliases, privateEmails);
        assertThat(id).isNotNull();
        return id;
    }

    private static String array(String... values) {
        return List.of(values).stream()
            .map(v -> "\"" + v + "\"")
            .collect(Collectors.joining(",", "{", "}"));
    }

    private static void assertSqlState(String expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(DataAccessException.class)
            .satisfies(e -> {
                Throwable cause = ((DataAccessException) e).getMostSpecificCause();
                assertThat(cause).isInstanceOf(SQLException.class);
                assertThat(((SQLException) cause).getSQLState()).isEqualTo(expected);
            });
    }
}
