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

import java.time.YearMonth;
import java.util.List;

/**
 * Exemptions, discounts and deferrals (ZK Treasurer, FEES_MANAGE enforced by the caller). Granting and revoking
 * recalculates the open dues of the member: exemptions and discounts may be retroactive and cancel or reduce open
 * dues (ending the arrears they caused); paid dues never change. Errors: NOT_FOUND, ADJUSTMENT_RANGE_INVALID,
 * ADJUSTMENT_AMOUNT_REQUIRED, ADJUSTMENT_AMOUNT_INVALID, ADJUSTMENT_AMOUNT_NOT_ALLOWED, ADJUSTMENT_REASON_REQUIRED,
 * ADJUSTMENT_OVERLAP (conflict, same kind overlapping), ADJUSTMENT_ALREADY_REVOKED (conflict), SELF_FEES_NOT_ALLOWED
 * (granting an adjustment to oneself).
 * <p>
 * Confidentiality: {@link AdjustmentView} is safe for every FEES_READ holder; {@link AdjustmentDetails} (reason
 * category and justification) only for FEES_MANAGE holders and the member themself. The audit log never holds reasons.
 */
public interface AdjustmentService {
    AdjustmentDetails grant(long actorMemberId, NewAdjustment adjustment);

    /**
     * Ends the adjustment from the current month on; earlier months keep their adjusted dues.
     */
    AdjustmentDetails revoke(long actorMemberId, long adjustmentId);

    /**
     * Ends the adjustment from the given month on (before its start it is revoked entirely).
     */
    AdjustmentDetails revoke(long actorMemberId, long adjustmentId, YearMonth from);

    /**
     * Without reasons, oldest first, including revoked ones.
     */
    List<AdjustmentView> adjustmentsOf(long memberId);

    /**
     * With reasons: FEES_MANAGE holders and the member themself only.
     */
    List<AdjustmentDetails> detailsOf(long memberId);
}
