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
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.fees.Due;
import org.mlodzirazem.panel.services.panelapi.fees.FeeHistory;
import org.mlodzirazem.panel.services.panelapi.fees.MemberBalance;
import org.mlodzirazem.panel.services.panelapi.fees.Payment;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentService;
import org.mlodzirazem.panel.services.panelapi.fees.RecordPayment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.PaymentSource;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePayment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentAllocationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class DefaultPaymentService implements PaymentService {
    private final TFeePaymentRepository payments;
    private final TFeePaymentAllocationRepository allocations;
    private final TMemberRepository members;
    private final DuesSynchronizer synchronizer;
    private final FeeReader reader;
    private final FeeLock lock;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional
    public Payment record(long actorMemberId, RecordPayment request) {
        lock.acquire();

        // a bank transfer comes from the statement import or the matching queue, which guards manual matching itself
        if (request.bankTransactionId() == null) {
            OwnFeesGuard.reject(actorMemberId, request.memberId());
        }

        TMember member = members.findById(request.memberId())
                                .orElseThrow(() -> new NotFoundException("Member", request.memberId()));
        BigDecimal amount = Money.positive(request.amount(), "PAYMENT_AMOUNT_INVALID");

        if (request.paidOn() == null || request.paidOn().isAfter(LocalDate.now(clock))) {
            throw new RuleViolationException("PAYMENT_DATE_INVALID", "The payment date is missing or in the future");
        }

        if (request.source() == null
            || (request.bankTransactionId() != null && request.source() != PaymentSource.BANK)) {
            throw new RuleViolationException("PAYMENT_SOURCE_INVALID",
                "A source is required and only bank payments reference a bank transaction");
        }

        TFeePayment payment = new TFeePayment();
        payment.setMember(member);
        payment.setAmount(amount);
        payment.setPaidOn(request.paidOn());
        payment.setSource(request.source());
        payment.setBankTransactionId(request.bankTransactionId());
        payment.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        payment.setRecordedBy(members.getReferenceById(actorMemberId));
        payment.setCreatedBy(members.getReferenceById(actorMemberId));
        payments.saveAndFlush(payment);

        synchronizer.sync(synchronizer.context(), member, YearMonth.now(clock), new DuesSynchronizer.Tally());

        AuditPayload audit = AuditPayload.of("memberId", member.getId()).withId("paymentId", payment.getId());

        if (payment.getBankTransactionId() != null) {
            audit = audit.withId("bankTransactionId", payment.getBankTransactionId());
        }

        auditLog.record("fees :: payment-record", audit.withFields(List.of("amount", "paidOn", "source")));

        return paymentView(payment);
    }

    @Override
    @Transactional
    public Payment reverse(long actorMemberId, long paymentId, String reason) {
        lock.acquire();

        TFeePayment payment = payments.findById(paymentId)
                                      .orElseThrow(() -> new NotFoundException("Fee payment", paymentId));

        if (payment.getReversedAt() != null) {
            throw new ConflictException("PAYMENT_ALREADY_REVERSED", "The payment is already reversed");
        }

        if (reason == null || reason.isBlank()) {
            throw new RuleViolationException("PAYMENT_REASON_REQUIRED", "A reversal reason is required");
        }

        payment.setReversedAt(ZonedDateTime.now(clock));
        payment.setReversedBy(members.getReferenceById(actorMemberId));
        payment.setReversalReason(reason.strip());
        payments.saveAndFlush(payment);

        TMember member = payment.getMember();
        synchronizer.sync(synchronizer.context(), member, YearMonth.now(clock), new DuesSynchronizer.Tally());

        auditLog.record("fees :: payment-reverse", AuditPayload.of("memberId", member.getId())
                                                               .withId("paymentId", paymentId)
                                                               .withFields(List.of("reversalReason")));

        return paymentView(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberBalance balance(long memberId) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal owed = BigDecimal.ZERO.setScale(2);
        BigDecimal overdue = BigDecimal.ZERO.setScale(2);
        BigDecimal credit = BigDecimal.ZERO.setScale(2);

        for (Due due : reader.duesOf(memberId)) {
            if (due.status() == DueStatus.OPEN) {
                owed = owed.add(due.outstanding());

                if (due.month().atEndOfMonth().isBefore(today)) {
                    overdue = overdue.add(due.outstanding());
                }
            }
        }

        for (Payment payment : reader.paymentsOf(memberId)) {
            if (!payment.reversed()) {
                credit = credit.add(payment.amount().subtract(payment.allocated()));
            }
        }

        return new MemberBalance(memberId, owed, overdue, credit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Due> openDues(long memberId) {
        return reader.duesOf(memberId, DueStatus.OPEN);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> paymentsOf(long memberId) {
        return reader.paymentsOf(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public FeeHistory history(long memberId) {
        return new FeeHistory(reader.duesOf(memberId), reader.paymentsOf(memberId),
            reader.adjustmentEntities(memberId).stream().map(FeeReader::view).toList());
    }

    private Payment paymentView(TFeePayment payment) {
        BigDecimal allocated = reader.paymentsOf(payment.getMember().getId()).stream()
                                     .filter(p -> p.id() == payment.getId())
                                     .map(Payment::allocated)
                                     .findFirst()
                                     .orElse(BigDecimal.ZERO.setScale(2));

        return FeeReader.payment(payment, allocated);
    }
}
