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

import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentKind;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Tiers, their rate history and member tier assignments. Permission checks (FEES_MANAGE for writes) belong to the
 * caller. Errors: NOT_FOUND, FEE_TIER_CODE_TAKEN (conflict), FEE_TIER_INVALID, FEE_TIER_DEFAULT_REQUIRED,
 * FEE_TIER_INACTIVE, FEE_TIER_DEFAULT_LOCKED (the default cannot change once dues exist), RATE_AMOUNT_INVALID,
 * RATE_REFERENCE_REQUIRED, RATE_EXISTS (conflict), RATE_MONTH_HAS_DUES, RATE_MONTH_IN_PAST,
 * ASSIGNMENT_MONTH_IN_PAST, SELF_FEES_NOT_ALLOWED. Rates and assignments start in the current month at the earliest.
 */
public interface FeeScheduleService {
    List<FeeTier> tiers(boolean includeInactive);

    FeeTier createTier(long actorMemberId, NewFeeTier tier);

    /**
     * Changing which tier is the default regenerates dues of members without an explicit assignment.
     */
    FeeTier updateTier(long actorMemberId, long tierId, FeeTierUpdate update);

    /**
     * Adds a rate to the history of the tier. The rate may only start in a month for which the tier has no dues yet
     * (RATE_MONTH_HAS_DUES otherwise), so generated dues are never rewritten. Missing dues are generated afterwards.
     */
    FeeRate addRate(long actorMemberId, long tierId, YearMonth validFrom, BigDecimal monthlyAmount, String resolutionReference);

    /**
     * Full rate history of the tier, oldest first.
     */
    List<FeeRate> rates(long tierId);

    /**
     * Assigns the member to an active tier from the given month on (replacing an assignment with the same month) and
     * recalculates the member open dues.
     */
    TierAssignment assignTier(long actorMemberId, long memberId, long tierId, YearMonth validFrom);

    List<TierAssignment> assignments(long memberId);

    /**
     * Tier and amount for the member and month after adjustments (see {@link AdjustmentKind}); empty when no tier or
     * no rate applies yet.
     */
    Optional<ResolvedFee> resolve(long memberId, YearMonth month);
}
