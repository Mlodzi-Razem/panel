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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment;

/** Timeline events of a recruitment case; payloads keep field names, never values. */
public enum RecruitmentCaseEventType {
    CASE_CREATED,
    STATUS_CHANGED,
    REMINDER_SENT,
    ZK_NOTIFIED_PROBATION_START,
    DECLARATION_RECORDED,
    DECLARATION_LINK_CREATED,
    RECOMMENDATION_SENT,
    VOTE_STARTED,
    DECISION_RECORDED,
    CASE_CLOSED,
    NOTE_ADDED,
    CANDIDATE_DATA_CORRECTED,
    ARTIFACTS_PURGED,
    CASE_ANONYMIZED,
    DECLARATION_BLOCKED
}
