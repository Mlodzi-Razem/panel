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

/**
 * Resolution kinds of the admission flow (statute jednolity 2026, art. 9 ust. 2 pkt 2).
 * Accepting a case requires both {@code ZK_APPROVAL} and {@code ZO_ADMISSION_RESOLUTION}.
 */
public enum RecruitmentDecisionKind {
    ZO_ASSESSMENT_POSITIVE,
    ZO_ASSESSMENT_NEGATIVE,
    PERIOD_EXTENSION,
    REJECTION,
    ZK_APPROVAL,
    ZO_ADMISSION_RESOLUTION
}
