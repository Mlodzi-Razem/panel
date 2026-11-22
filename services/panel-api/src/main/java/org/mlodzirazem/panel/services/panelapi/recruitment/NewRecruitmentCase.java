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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentSource;

import java.util.Map;

/**
 * A new intake case opened by a district board (M2: back-office intake, before the declaration arrives).
 * The case always starts at {@code NEW}; candidate data stays in the recruitment module and never lands
 * in {@code members} before PRZYJĘTA (GDPR minimisation).
 *
 * @param districtBodyId the district the case belongs to; the caller must hold
 *     {@code RECRUITMENT_MANAGE} there (checked in the controller)
 * @param source how the candidate reached the association
 * @param candidateFields candidate data; first and last name are required
 */
public record NewRecruitmentCase(
    long districtBodyId,
    RecruitmentSource source,
    Map<RecruitmentCandidateField, @Nullable Object> candidateFields
) {
}
