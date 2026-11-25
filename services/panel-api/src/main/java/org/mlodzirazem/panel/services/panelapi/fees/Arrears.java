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

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Arrears of a member on a date. Only dues that are overdue and not covered by a deferral count; those covered by a
 * deferral are reported separately as justified.
 *
 * @param since            when the arrears started (the due date of the oldest unjustified overdue due; for a deferred
 *                         due the end of the deferral), or null when there are none
 * @param months           completed months between {@code since} and {@code asOf}
 * @param overdueAmount    outstanding amount of the unjustified overdue dues
 * @param justifiedDues    overdue dues covered by a running deferral
 * @param justifiedAmount  their outstanding amount
 */
public record Arrears(
    @Nullable LocalDate since,
    LocalDate asOf,
    int months,
    int overdueDues,
    BigDecimal overdueAmount,
    int justifiedDues,
    BigDecimal justifiedAmount
) {
    public static Arrears none(LocalDate asOf) {
        return new Arrears(null, asOf, 0, 0, BigDecimal.ZERO.setScale(2), 0, BigDecimal.ZERO.setScale(2));
    }

    public static Arrears of(
        @Nullable LocalDate since, LocalDate asOf, int overdueDues, BigDecimal overdueAmount,
        int justifiedDues, BigDecimal justifiedAmount
    ) {
        int months = since == null ? 0 : (int) ChronoUnit.MONTHS.between(since, asOf);
        return new Arrears(since, asOf, months, overdueDues, overdueAmount, justifiedDues, justifiedAmount);
    }

    /**
     * True when the arrears have lasted longer than the given number of months.
     */
    public boolean longerThan(int monthsLimit) {
        return since != null && since.plusMonths(monthsLimit).isBefore(asOf);
    }

    /**
     * Art. 14: longer than {@link ArrearsService#ELECTORAL_RIGHTS_MONTHS} months.
     */
    public boolean suspendsElectoralRights() {
        return longerThan(ArrearsService.ELECTORAL_RIGHTS_MONTHS);
    }

    /**
     * Art. 17.1.4b: longer than {@link ArrearsService#EXCLUSION_MONTHS} months.
     */
    public boolean groundsForExclusion() {
        return longerThan(ArrearsService.EXCLUSION_MONTHS);
    }
}
