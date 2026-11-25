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

package org.mlodzirazem.panel.services.panelapi.fees;

import java.time.LocalDate;
import java.util.List;

/**
 * Arrears per the spec: a due is overdue after the last day of its month; the arrears start at the due date of the
 * oldest overdue open due not covered by a deferral (a due covered by a deferral counts from the end of the
 * deferral; an open-ended deferral covers it until revoked).
 */
public interface ArrearsService {
    /**
     * Art. 14: arrears longer than this many months suspend electoral rights.
     */
    int ELECTORAL_RIGHTS_MONTHS = 3;

    /**
     * Art. 17.1.4b: arrears longer than this many months are grounds for exclusion.
     */
    int EXCLUSION_MONTHS = 4;

    Arrears of(long memberId, LocalDate on);

    Arrears of(long memberId);

    /**
     * Not removed members whose arrears are longer than the given number of months, scoped by primary district,
     * oldest arrears first.
     */
    List<MemberArrears> longerThan(int months, DistrictScope scope, LocalDate on);

    List<MemberArrears> longerThan(int months, DistrictScope scope);
}
