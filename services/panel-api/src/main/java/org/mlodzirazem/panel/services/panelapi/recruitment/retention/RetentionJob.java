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

package org.mlodzirazem.panel.services.panelapi.recruitment.retention;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily trigger of the retention of rejected recruitment cases. Counters only in the log. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "panel.recruitment.retention", name = "enabled", havingValue = "true",
    matchIfMissing = true)
class RetentionJob {
    private static final Logger LOG = LoggerFactory.getLogger(RetentionJob.class);

    private final RecruitmentRetention retention;

    @Scheduled(cron = "${panel.recruitment.retention.cron:0 30 3 * * *}", zone = "Europe/Warsaw")
    void daily() {
        try {
            RecruitmentRetention.RetentionReport report = retention.run();

            if (!report.changedNothing()) {
                LOG.info("Recruitment retention: anonymized={}, casesWithArtifactsPurged={}, artifactsPurged={},"
                        + " failures={}", report.casesAnonymized(), report.casesWithArtifactsPurged(),
                    report.artifactsPurged(), report.failures());
            }
        } catch (RuntimeException failure) {
            // the exception text may quote a database row: class name only
            LOG.error("Recruitment retention pass failed: {}", failure.getClass().getName());
        }
    }
}
