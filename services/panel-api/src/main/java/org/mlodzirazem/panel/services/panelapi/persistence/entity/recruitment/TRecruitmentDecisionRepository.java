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
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TRecruitmentDecisionRepository extends JpaRepository<TRecruitmentDecision, Long> {
    /** Decisions are unique per case and kind (UNIQUE (case_id, decision_kind) at the schema level). */
    Optional<TRecruitmentDecision> findByRecruitmentCaseIdAndDecisionKind(Long caseId,
                                                                           RecruitmentDecisionKind decisionKind);

    List<TRecruitmentDecision> findAllByRecruitmentCaseId(Long caseId);
}
