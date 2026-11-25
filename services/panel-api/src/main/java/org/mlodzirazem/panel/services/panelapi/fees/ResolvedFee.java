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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentKind;

import java.math.BigDecimal;

/**
 * @param rate       the tier rate valid for the month
 * @param amount     what the member is charged after adjustments; zero when exempt
 * @param adjustment the exemption or discount that applies, or null
 */
public record ResolvedFee(long tierId, BigDecimal rate, BigDecimal amount, @Nullable AdjustmentKind adjustment) {
    public boolean exempt() {
        return adjustment == AdjustmentKind.EXEMPTION;
    }
}
