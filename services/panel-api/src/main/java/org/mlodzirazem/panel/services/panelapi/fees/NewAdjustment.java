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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentReason;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * @param to             last month covered, inclusive; null means until revoked
 * @param discountAmount the fixed monthly amount for {@link AdjustmentKind#DISCOUNT} (required), null for the others
 * @param justification  free text, mandatory
 */
public record NewAdjustment(
    long memberId,
    AdjustmentKind kind,
    YearMonth from,
    @Nullable YearMonth to,
    @Nullable BigDecimal discountAmount,
    AdjustmentReason reasonCategory,
    String justification,
    @Nullable String resolutionReference
) {
}
