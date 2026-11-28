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

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Retention of rejected recruitment cases (owner decision G-5): the declaration artifacts (PDF, scan) are purged
 * {@code artifact-days} after the case was closed, the candidate's personal data is wiped and the case marked
 * anonymized {@code rejected-case-days} after it was closed. "After N days" means from day N on:
 * {@code closed_at + N <= today}. Accepted and open cases are never touched.
 */
public interface RecruitmentRetention {
    /** One pass of the daily job on the application clock. */
    RetentionReport run();

    /** One pass as of the given instant (the day is taken in the application clock's zone). */
    RetentionReport runAt(ZonedDateTime now);

    /** Day on which the artifacts of a case closed on {@code closedAt} are purged. */
    LocalDate artifactsDueOn(LocalDate closedAt);

    /** Day on which a case closed on {@code closedAt} is anonymized ("do usunięcia przez ZK"). */
    LocalDate caseDueOn(LocalDate closedAt);

    /**
     * Counters of a pass; nothing else leaves the job. A second pass without time passing reports all zeros.
     */
    record RetentionReport(int casesAnonymized, int casesWithArtifactsPurged, int artifactsPurged, int failures) {
        public boolean changedNothing() {
            return casesAnonymized == 0 && casesWithArtifactsPurged == 0 && artifactsPurged == 0 && failures == 0;
        }
    }
}
