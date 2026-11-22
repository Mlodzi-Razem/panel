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

package org.mlodzirazem.panel.services.panelapi.recruitment;

import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;

/**
 * The admission of a candidate as a member (ZK_VOTING → ACCEPTED, M6a): number allocation, the
 * {@code members} row with its primary district affiliation and the status flip happen in ONE
 * transaction. History is only appended: nothing of the case or of earlier members is rewritten.
 */
public interface RecruitmentAdmissionService {
    /**
     * Requires the case in ZK_VOTING with both collegial decisions recorded. The membership date is the
     * later of the two decision dates (derived server-side).
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.ConflictException {@code CASE_NOT_OPEN},
     *         {@code NOT_ZK_VOTING}, {@code BOTH_DECISIONS_REQUIRED}, or {@code MEMBER_ALREADY_EXISTS} when a
     *         member with the same formal identifier is already registered (generic, nothing is written)
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException {@code RECRUITMENT_APPROVE_REQUIRED}
     */
    Admission admit(long caseId, long actorMemberId);

    record Admission(TRecruitmentCase recruitmentCase, TMember member) {
    }
}
