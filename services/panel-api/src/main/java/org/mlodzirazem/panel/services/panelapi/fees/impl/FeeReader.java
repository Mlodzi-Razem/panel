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

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentDetails;
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentView;
import org.mlodzirazem.panel.services.panelapi.fees.Due;
import org.mlodzirazem.panel.services.panelapi.fees.Payment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustmentRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeDue;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeDueRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePayment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentAllocationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class FeeReader {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final TFeeDueRepository dues;
    private final TFeePaymentRepository payments;
    private final TFeePaymentAllocationRepository allocations;
    private final TFeeAdjustmentRepository adjustments;

    List<Due> duesOf(long memberId) {
        Map<Long, BigDecimal> paid = sums(allocations.sumByDueForMember(memberId));

        return dues.findByMemberIdOrderByDueMonthAsc(memberId).stream().map(d -> due(d, paid)).toList();
    }

    List<Due> duesOf(long memberId, DueStatus status) {
        return duesOf(memberId).stream().filter(d -> d.status() == status).toList();
    }

    List<Payment> paymentsOf(long memberId) {
        Map<Long, BigDecimal> allocated = sums(allocations.sumByPaymentForMember(memberId));

        return payments.findByMemberIdOrderByPaidOnAscIdAsc(memberId)
                       .stream()
                       .sorted(Comparator.comparing(TFeePayment::getPaidOn).thenComparing(TFeePayment::getId).reversed())
                       .map(p -> payment(p, allocated.getOrDefault(p.getId(), ZERO)))
                       .toList();
    }

    List<TFeeAdjustment> adjustmentEntities(long memberId) {
        return adjustments.findByMemberIdOrderByFromMonthAscIdAsc(memberId);
    }

    static Due due(TFeeDue due, Map<Long, BigDecimal> paid) {
        return new Due(due.getId(), due.getMember().getId(), Months.of(due.getDueMonth()), due.getAmount(),
            paid.getOrDefault(due.getId(), ZERO), due.getTier().getId(), due.getStatus());
    }

    static Payment payment(TFeePayment p, BigDecimal allocated) {
        return new Payment(p.getId(), p.getMember().getId(), p.getAmount(), allocated, p.getPaidOn(), p.getSource(),
            p.getBankTransactionId(), p.getNote(), p.getRecordedBy().getId(), p.getReversedAt(),
            p.getReversedBy() == null ? null : p.getReversedBy().getId(), p.getReversalReason());
    }

    static AdjustmentView view(TFeeAdjustment a) {
        return new AdjustmentView(a.getId(), a.getMember().getId(), a.getKind(), Months.of(a.getFromMonth()),
            a.getToMonth() == null ? null : Months.of(a.getToMonth()), a.getDiscountAmount(),
            a.getResolutionReference(), a.getGrantedBy().getId(), a.getCreatedAt(),
            a.getRevokedFromMonth() == null ? null : Months.of(a.getRevokedFromMonth()));
    }

    static AdjustmentDetails details(TFeeAdjustment a) {
        return new AdjustmentDetails(view(a), a.getReasonCategory(), a.getJustification());
    }

    static Map<Long, BigDecimal> sums(List<Object[]> rows) {
        Map<Long, BigDecimal> result = new HashMap<>();

        for (Object[] row : rows) {
            result.put((Long) row[0], ((BigDecimal) row[1]).setScale(2));
        }

        return result;
    }
}
