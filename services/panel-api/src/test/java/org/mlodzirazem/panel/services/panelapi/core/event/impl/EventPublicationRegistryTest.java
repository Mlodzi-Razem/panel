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

package org.mlodzirazem.panel.services.panelapi.core.event.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.api.v1.ApiTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class EventPublicationRegistryTest extends ApiTestSupport {
    private static final String PROBE_TYPE = EventPublicationProbeEvent.class.getName();

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    EventPublicationProbeListener probe;

    @Autowired
    EventPublicationMaintenanceJob maintenance;

    @BeforeEach
    void resetRegistry() {
        jdbc.execute("TRUNCATE events.event_publication");
        probe.reset();
    }

    @Test
    void registryTableExistsAfterFlywayMigrations() {
        assertThat(jdbc.queryForObject("SELECT to_regclass('events.event_publication')::text", String.class))
            .isEqualTo("events.event_publication");
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.event_publication')::text", String.class))
            .isNull();
    }

    @Test
    void transactionalListenerEventIsStoredAndCompleted() {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
            publisher.publishEvent(new EventPublicationProbeEvent("probe")));

        await(() -> completedCount(PROBE_TYPE) == 1);

        assertThat(probe.handled()).isEqualTo(1);
        assertThat(count("event_type = ?", PROBE_TYPE)).isEqualTo(1);
        assertThat(count("completion_date IS NULL")).isZero();
    }

    @Test
    void cleanupDeletesOnlyCompletedPublicationsOlderThanRetention() {
        Instant now = Instant.now();
        insert(now.minus(Duration.ofDays(45)), now.minus(Duration.ofDays(45)));
        insert(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)));
        insert(now.minus(Duration.ofDays(45)), null);

        maintenance.cleanCompleted();

        assertThat(count("TRUE")).isEqualTo(2);
        assertThat(count("completion_date IS NOT NULL AND completion_date < now() - interval '30 days'")).isZero();
        assertThat(count("completion_date IS NULL")).isEqualTo(1);
    }

    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("event publication was not completed within 15 s");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for event completion");
            }
        }
    }

    private int completedCount(String eventType) {
        return count("event_type = ? AND completion_date IS NOT NULL", eventType);
    }

    private int count(String where, Object... args) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM events.event_publication WHERE " + where, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void insert(Instant published, Instant completed) {
        jdbc.update(
            "INSERT INTO events.event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date) "
                + "VALUES (gen_random_uuid(), 'test.listener', 'test.Event', '{}', ?, ?::timestamptz)",
            Timestamp.from(published),
            completed == null ? null : Timestamp.from(completed)
        );
    }
}
