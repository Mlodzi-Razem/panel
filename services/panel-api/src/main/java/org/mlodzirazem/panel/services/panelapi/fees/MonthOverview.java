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

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * @param expected      sum of the non-cancelled dues of the month
 * @param collected     the part of them covered by payment allocations
 * @param members       members with a non-cancelled due in the month
 * @param membersPaid   of them, those whose due is fully paid
 */
public record MonthOverview(
    YearMonth month,
    BigDecimal expected,
    BigDecimal collected,
    BigDecimal outstanding,
    int members,
    int membersPaid,
    int membersOutstanding
) {
}
