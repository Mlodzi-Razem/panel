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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.members.SuspensionRegistry;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The register's append-only rules live in the database (G-10, V0.0.28.02). */
@SpringBootTest
@PostgresTest
class SuspensionsSchemaTest extends DomainTestSupport {
    @Autowired
    SuspensionRegistry registry;

    private long insert(TMember member, String extraColumns, String extraValues) {
        return jdbc.queryForObject(
            "INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start, decision_date"
                + extraColumns + ") VALUES (?, ?, ?, CURRENT_DATE - 30" + extraValues + ") RETURNING id",
            Long.class, member.getId(), body("national-board").getId(), LocalDate.now().minusDays(10));
    }

    @Test
    void legacyInsertWithoutNewColumnsMeansOrganFull() {
        long id = insert(newMember("Ola", "Legacy", "mazowieckie"), "", "");

        assertThat(jdbc.queryForObject("SELECT kind::text || '/' || scope::text || '/' || status::text FROM suspensions "
            + "WHERE id = ?", String.class, id)).isEqualTo("BY_ORGAN/FULL/ACTIVE");
    }

    @Test
    void identityOfAnEntryNeverChanges() {
        long id = insert(newMember("Ida", "Fixed", "mazowieckie"), "", "");

        assertThatThrownBy(() -> jdbc.update("UPDATE suspensions SET suspension_start = suspension_start - 1 WHERE id = ?", id))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE suspensions SET scope = 'PASSIVE_ELECTORAL' WHERE id = ?", id))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE suspensions SET decision_signature = 'x' WHERE id = ?", id))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void liftIsWriteOnce() {
        long id = insert(newMember("Iga", "Lift", "mazowieckie"), "", "");
        jdbc.update("UPDATE suspensions SET lifted_at = CURRENT_DATE, lifted_reason = 'r', lift_decision_signature = 's' "
            + "WHERE id = ?", id);

        assertThatThrownBy(() -> jdbc.update("UPDATE suspensions SET lifted_reason = 'other' WHERE id = ?", id))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void voidedEntryIsNeverTouchedAgainAndNeverDeleted() {
        long id = insert(newMember("Ula", "Void", "mazowieckie"), "", "");
        jdbc.update("UPDATE suspensions SET status = 'VOIDED', void_reason = 'omyłka', voided_at = now() WHERE id = ?", id);

        assertThatThrownBy(() -> jdbc.update("UPDATE suspensions SET appeal_ended_on = CURRENT_DATE WHERE id = ?", id))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM suspensions WHERE id = ?", id))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void checksRejectInconsistentRows() {
        TMember member = newMember("Zofia", "Checks", "mazowieckie");

        assertThatThrownBy(() -> insert(member, ", kind, scope", ", 'VOLUNTARY', 'PASSIVE_ELECTORAL'"))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert(member, ", scope, scope_description", ", 'PARTIAL_OTHER', NULL"))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert(member, ", status, void_reason, voided_at", ", 'VOIDED', NULL, now()"))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void registryCountsOnlyActiveFullOrPassiveRows() {
        TMember member = newMember("Rita", "Registry", "mazowieckie");
        insert(member, ", scope, scope_description", ", 'PARTIAL_OTHER', 'inne uprawnienie'");
        assertThat(registry.competencesSuspended(member.getId(), LocalDate.now())).isFalse();

        insert(member, ", scope", ", 'FULL'");
        assertThat(registry.competencesSuspended(member.getId(), LocalDate.now())).isTrue();
    }

    @Test
    void decisionDateIsRequiredForAResolutionAndForbiddenForAVoluntaryStatement() {
        TMember member = newMember("Dora", "Decision", "mazowieckie");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO suspensions (suspended_user_id, suspending_body_id, "
            + "suspension_start) VALUES (?, ?, ?)", member.getId(), body("national-board").getId(), LocalDate.now()))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert(member, ", kind, decision_date", ", 'VOLUNTARY', CURRENT_DATE - 30"))
            .isInstanceOf(DataAccessException.class);
        // the resolution cannot come after the start (start = today - 10 in the helper)
        assertThatThrownBy(() -> jdbc.update("INSERT INTO suspensions (suspended_user_id, suspending_body_id, "
            + "suspension_start, decision_date) VALUES (?, ?, CURRENT_DATE - 10, CURRENT_DATE - 1)",
            member.getId(), body("national-board").getId()))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void decisionDateIsWriteOnce() {
        long id = insert(newMember("Eva", "Decided", "mazowieckie"), "", "");

        assertThatThrownBy(() -> jdbc.update("UPDATE suspensions SET decision_date = CURRENT_DATE - 40 WHERE id = ?", id))
            .isInstanceOf(DataAccessException.class);
    }
}
