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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.mail.MailDeliveryStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.mail.TMailDelivery;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.mail.TMailDeliveryRepository;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class MailDeliveryPersistenceTest {
    private static final String CHECK_VIOLATION = "23514";

    @Autowired
    TMailDeliveryRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("DELETE FROM mail_deliveries");
    }

    private TMailDelivery row(boolean external, MailDeliveryStatus status, ZonedDateTime at) {
        TMailDelivery row = new TMailDelivery();
        row.setPurpose("test-purpose");
        row.setReference("case:1");
        row.setRecipientDomain(external ? "example.org" : "mlodzirazem.org");
        row.setExternal(external);
        row.setStatus(status);
        row.setErrorCode(status == MailDeliveryStatus.FAILED ? "ErrorAccessDenied" : null);
        row.setCreatedAt(at);
        return repository.saveAndFlush(row);
    }

    @Test
    void roundTripsANamesOnlyRow() {
        TMailDelivery saved = row(true, MailDeliveryStatus.FAILED, ZonedDateTime.now(ZoneOffset.UTC));

        TMailDelivery loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getPurpose()).isEqualTo("test-purpose");
        assertThat(loaded.getReference()).isEqualTo("case:1");
        assertThat(loaded.getRecipientDomain()).isEqualTo("example.org");
        assertThat(loaded.isExternal()).isTrue();
        assertThat(loaded.getStatus()).isEqualTo(MailDeliveryStatus.FAILED);
        assertThat(loaded.getErrorCode()).isEqualTo("ErrorAccessDenied");
        assertThat(jdbc.queryForObject(
            "SELECT status FROM mail_deliveries WHERE id = ?", String.class, saved.getId())).isEqualTo("FAILED");
    }

    @Test
    void countsOnlyExternalSentRowsInTheWindow() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        row(true, MailDeliveryStatus.SENT, now.minusHours(1));
        row(true, MailDeliveryStatus.SENT, now.minusHours(23));
        row(true, MailDeliveryStatus.SENT, now.minusHours(25));
        row(true, MailDeliveryStatus.FAILED, now.minusHours(1));
        row(true, MailDeliveryStatus.REFUSED_LIMIT, now.minusHours(1));
        row(false, MailDeliveryStatus.SENT, now.minusHours(1));

        assertThat(repository.countByExternalTrueAndStatusAndCreatedAtGreaterThanEqual(
            MailDeliveryStatus.SENT, now.minusHours(24))).isEqualTo(2);
    }

    @Test
    void theDatabaseRejectsAnUnknownStatus() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO mail_deliveries (purpose, recipient_domain, external, status) VALUES ('p', 'example.org', TRUE, 'QUEUED')"))
            .isInstanceOf(DataAccessException.class)
            .rootCause().isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLATION));
    }

    @Test
    void createdAtDefaultsToNow() {
        jdbc.update("INSERT INTO mail_deliveries (purpose, recipient_domain, external, status) "
            + "VALUES ('p', 'example.org', FALSE, 'SENT')");

        assertThat(jdbc.queryForObject(
            "SELECT created_at > now() - interval '1 minute' FROM mail_deliveries", Boolean.class)).isTrue();
    }
}
