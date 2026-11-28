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

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Retention periods of rejected recruitment cases; all counted in days from {@code closed_at}. */
@Getter
@Setter
@ConfigurationProperties("panel.recruitment.retention")
public class RetentionProperties {
    /** Switches the daily job off (the periods stay available to the views). */
    private boolean enabled = true;

    /** Cron of the daily job, in Europe/Warsaw (Spring six-field syntax). */
    private String cron = "0 30 3 * * *";

    /** Personal data of a rejected case is wiped from this day on (owner decision G-5: 365). */
    private int rejectedCaseDays = 365;

    /** Declaration artifacts of a rejected case are purged from this day on (owner decision G-5: 90). */
    private int artifactDays = 90;

    /** Cases per transaction batch lookup; each case still has its own transaction. */
    private int batchSize = 100;

    @PostConstruct
    void validate() {
        if (artifactDays < 1 || rejectedCaseDays < 1 || batchSize < 1) {
            throw new IllegalStateException("panel.recruitment.retention: periods and batch size must be positive");
        }

        if (artifactDays > rejectedCaseDays) {
            throw new IllegalStateException(
                "panel.recruitment.retention: artifacts must not outlive the case (artifact-days <= rejected-case-days)");
        }
    }
}
