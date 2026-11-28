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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Housekeeping of the Modulith event publication registry (table {@code events.event_publication}).
 * <ul>
 *     <li>daily: deletes completed publications older than {@code panel.modulith.events.completed-retention-days};</li>
 *     <li>every 15 min: resubmits publications not completed for 10 min; logs ERROR when some reached
 *     {@code panel.modulith.events.alarm-attempts} failed attempts.</li>
 * </ul>
 * Logs carry counts and exception types only, never event payloads.
 */
@Slf4j
@Component
class EventPublicationMaintenanceJob {
    private static final Duration RESUBMIT_OLDER_THAN = Duration.ofMinutes(10);

    private final CompletedEventPublications completed;
    private final IncompleteEventPublications incomplete;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration completedRetention;
    private final int alarmAttempts;

    EventPublicationMaintenanceJob(
        CompletedEventPublications completed,
        IncompleteEventPublications incomplete,
        JdbcTemplate jdbc,
        Clock clock,
        @Value("${panel.modulith.events.completed-retention-days:30}") int completedRetentionDays,
        @Value("${panel.modulith.events.alarm-attempts:5}") int alarmAttempts
    ) {
        this.completed = completed;
        this.incomplete = incomplete;
        this.jdbc = jdbc;
        this.clock = clock;
        this.completedRetention = Duration.ofDays(completedRetentionDays);
        this.alarmAttempts = alarmAttempts;
    }

    @Scheduled(cron = "${panel.modulith.events.cleanup-cron:0 40 3 * * *}", zone = "Europe/Warsaw")
    void cleanCompleted() {
        try {
            Instant cutoff = clock.instant().minus(completedRetention);
            Long candidates = jdbc.queryForObject(
                "SELECT count(*) FROM events.event_publication WHERE completion_date < ?",
                Long.class,
                Timestamp.from(cutoff)
            );
            completed.deletePublicationsOlderThan(completedRetention);
            log.info("event publications cleanup: completed older than {} days, candidates={}",
                completedRetention.toDays(), candidates);
        } catch (RuntimeException e) {
            log.error("event publications cleanup failed: {}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(
        initialDelayString = "${panel.modulith.events.resubmit-interval:PT15M}",
        fixedDelayString = "${panel.modulith.events.resubmit-interval:PT15M}"
    )
    void resubmitIncomplete() {
        try {
            incomplete.resubmitIncompletePublicationsOlderThan(RESUBMIT_OLDER_THAN);
            Long stuck = jdbc.queryForObject(
                "SELECT count(*) FROM events.event_publication WHERE completion_date IS NULL AND completion_attempts >= ?",
                Long.class,
                alarmAttempts
            );
            if (stuck != null && stuck > 0) {
                log.error("event publications still incomplete after {} attempts: count={}", alarmAttempts, stuck);
            }
        } catch (RuntimeException e) {
            log.error("event publications resubmission failed: {}", e.getClass().getSimpleName());
        }
    }
}
