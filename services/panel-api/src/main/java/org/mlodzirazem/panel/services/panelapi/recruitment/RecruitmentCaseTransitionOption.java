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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;

import java.util.List;

/**
 * What {@link RecruitmentCaseService#availableTransitions(long)} reports about one candidate target
 * status: whether the case may move there right now, the rejection reasons the intake would accept
 * for {@code REJECTED}, and — when the move is blocked — the exact error code the transition would
 * throw, so the front end shows the same truth the API enforces and keeps no local copy of the map.
 *
 * @param target the candidate status of {@code RecruitmentRules#allowedTargets}
 * @param allowed whether {@code POST /recruitment-cases/{id}/transitions} would accept the move now
 * @param rejectionReasons the reasons valid from the current status; present for {@code REJECTED} only
 * @param blockedReason {@code CASE_NOT_OPEN} for closed cases, {@code TRANSITION_NOT_ALLOWED} for the
 *     post-declaration cluster; {@code null} when the move is allowed
 */
public record RecruitmentCaseTransitionOption(
    RecruitmentCaseStatus target,
    boolean allowed,
    @Nullable List<RecruitmentRejectionReason> rejectionReasons,
    @Nullable String blockedReason
) {
}
