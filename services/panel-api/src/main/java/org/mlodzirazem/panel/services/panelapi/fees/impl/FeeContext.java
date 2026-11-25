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

package org.mlodzirazem.panel.services.panelapi.fees.impl;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

record FeeContext(@Nullable Long defaultTierId, Map<Long, List<RateStep>> rates) {
    record RateStep(YearMonth validFrom, BigDecimal amount) {
    }

    @Nullable BigDecimal rateOf(long tierId, YearMonth month) {
        BigDecimal found = null;

        for (RateStep step : rates.getOrDefault(tierId, List.of())) {
            if (!step.validFrom().isAfter(month)) {
                found = step.amount();
            }
        }

        return found;
    }
}
