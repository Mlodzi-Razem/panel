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

import java.util.List;

/**
 * Payments and allocation (ZK Treasurer, FEES_MANAGE enforced by the caller). A payment is allocated to the oldest
 * open dues first; the rest is credit applied to dues generated later. Errors: NOT_FOUND,
 * PAYMENT_AMOUNT_INVALID, PAYMENT_DATE_INVALID, PAYMENT_SOURCE_INVALID, PAYMENT_REASON_REQUIRED,
 * PAYMENT_ALREADY_REVERSED (conflict), SELF_FEES_NOT_ALLOWED (a payment for oneself without a bank transaction).
 */
public interface PaymentService {
    Payment record(long actorMemberId, RecordPayment payment);

    /**
     * Reverses the payment and re-allocates the remaining payments of the member.
     */
    Payment reverse(long actorMemberId, long paymentId, String reason);

    MemberBalance balance(long memberId);

    /**
     * Open dues (not fully paid), oldest first.
     */
    List<Due> openDues(long memberId);

    /**
     * Payments newest first, including reversed ones.
     */
    List<Payment> paymentsOf(long memberId);

    FeeHistory history(long memberId);
}
