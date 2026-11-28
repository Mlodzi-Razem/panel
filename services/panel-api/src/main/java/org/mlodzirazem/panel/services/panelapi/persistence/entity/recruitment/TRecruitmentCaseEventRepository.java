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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TRecruitmentCaseEventRepository extends JpaRepository<TRecruitmentCaseEvent, Long> {
    List<TRecruitmentCaseEvent> findAllByRecruitmentCaseIdOrderByCreatedAtAscIdAsc(long recruitmentCaseId);

    Optional<TRecruitmentCaseEvent> findFirstByRecruitmentCaseIdAndEventTypeOrderByCreatedAtDescIdDesc(
        long recruitmentCaseId, RecruitmentCaseEventType eventType);

    /**
     * Retention (M8): the reason of a candidate-data correction is free text typed by a case manager and may
     * carry personal data; the key is dropped and replaced by a marker, the event itself (type, time, actor and
     * changed field names) stays.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE recruitment_case_events SET payload = (payload - 'reason') || CAST('{\"reasonRedacted\": true}' AS jsonb)"
        + " WHERE case_id = :caseId AND event_type = CAST('CANDIDATE_DATA_CORRECTED' AS recruitment_case_event_type)"
        + " AND payload ->> 'reason' IS NOT NULL", nativeQuery = true)
    int redactCorrectionReasons(@Param("caseId") long caseId);
}
