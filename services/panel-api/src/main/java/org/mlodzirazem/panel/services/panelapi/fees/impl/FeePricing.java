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

import org.mlodzirazem.panel.services.panelapi.fees.ResolvedFee;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TMemberFeeTier;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

final class FeePricing {
    private FeePricing() {
    }

    static Optional<ResolvedFee> price(
        FeeContext context,
        List<TMemberFeeTier> assignmentsByValidFrom,
        List<TFeeAdjustment> adjustments,
        YearMonth month
    ) {
        Long tierId = context.defaultTierId();

        for (TMemberFeeTier assignment : assignmentsByValidFrom) {
            if (!Months.of(assignment.getValidFrom()).isAfter(month)) {
                tierId = assignment.getTier().getId();
            }
        }

        if (tierId == null) {
            return Optional.empty();
        }

        BigDecimal rate = context.rateOf(tierId, month);

        if (rate == null) {
            return Optional.empty();
        }

        BigDecimal amount = rate;
        AdjustmentKind applied = null;

        for (TFeeAdjustment adjustment : adjustments) {
            if (adjustment.getKind() == AdjustmentKind.EXEMPTION && adjustment.coversMonth(month)) {
                return Optional.of(new ResolvedFee(tierId, rate, BigDecimal.ZERO.setScale(2), AdjustmentKind.EXEMPTION));
            }
        }

        for (TFeeAdjustment adjustment : adjustments) {
            BigDecimal discount = adjustment.getDiscountAmount();

            if (adjustment.getKind() == AdjustmentKind.DISCOUNT && discount != null && adjustment.coversMonth(month)) {
                amount = amount.min(discount);
                applied = AdjustmentKind.DISCOUNT;
            }
        }

        return Optional.of(new ResolvedFee(tierId, rate, amount, applied));
    }
}
