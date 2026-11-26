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

package org.mlodzirazem.panel.services.panelapi.api.v1.fees;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Names;
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentDetails;
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentService;
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentView;
import org.mlodzirazem.panel.services.panelapi.fees.BankTransactionView;
import org.mlodzirazem.panel.services.panelapi.fees.ConfirmedIban;
import org.mlodzirazem.panel.services.panelapi.fees.Due;
import org.mlodzirazem.panel.services.panelapi.fees.FeeRate;
import org.mlodzirazem.panel.services.panelapi.fees.FeeScheduleService;
import org.mlodzirazem.panel.services.panelapi.fees.FeeTier;
import org.mlodzirazem.panel.services.panelapi.fees.MemberBalance;
import org.mlodzirazem.panel.services.panelapi.fees.Payment;
import org.mlodzirazem.panel.services.panelapi.fees.StatementImportView;
import org.mlodzirazem.panel.services.panelapi.fees.StatementReport;
import org.mlodzirazem.panel.services.panelapi.fees.TierAssignment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class FeeViews {
    private final AdjustmentService adjustments;
    private final FeeScheduleService schedule;
    private final TMemberRepository members;
    private final Clock clock;

    static String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }

    static FeeResponses.Balance balance(MemberBalance b) {
        return new FeeResponses.Balance(money(b.owed()), money(b.overdue()), money(b.credit()));
    }

    static FeeResponses.DueResponse due(Due d) {
        return new FeeResponses.DueResponse(d.id(), d.month().toString(), money(d.amount()), money(d.paid()),
            money(d.outstanding()), d.status(), d.tierId());
    }

    /**
     * @param internal whether to include the bookkeeping of the fee managers (note, recorder, bank transaction,
     *                 reversal reason); notes are free text and may say why somebody paid less or late
     */
    static FeeResponses.PaymentResponse payment(Payment p, boolean internal) {
        return new FeeResponses.PaymentResponse(p.id(), p.memberId(), money(p.amount()), money(p.allocated()),
            p.paidOn(), p.source(), p.reversed(), internal ? p.bankTransactionId() : null,
            internal ? p.note() : null, internal ? p.recordedBy() : null, internal ? p.reversedAt() : null,
            internal ? p.reversalReason() : null);
    }

    static FeeResponses.AdjustmentResponse adjustment(AdjustmentView a) {
        return adjustment(a, null);
    }

    static FeeResponses.AdjustmentResponse adjustment(AdjustmentDetails details) {
        return adjustment(details.view(), details);
    }

    private static FeeResponses.AdjustmentResponse adjustment(AdjustmentView a, @Nullable AdjustmentDetails details) {
        return new FeeResponses.AdjustmentResponse(a.id(), a.memberId(), a.kind(), a.from().toString(),
            Objects.toString(a.to(), null), a.discountAmount() == null ? null : money(a.discountAmount()),
            a.resolutionReference(), Objects.toString(a.revokedFrom(), null), a.revoked(),
            details == null ? null : details.reasonCategory(), details == null ? null : details.justification());
    }

    /**
     * @param withReasons only for global FEES_MANAGE holders and the member themself
     */
    List<FeeResponses.AdjustmentResponse> adjustmentsOf(long memberId, boolean withReasons) {
        return withReasons
            ? adjustments.detailsOf(memberId).stream().map(FeeViews::adjustment).toList()
            : adjustments.adjustmentsOf(memberId).stream().map(FeeViews::adjustment).toList();
    }

    static FeeResponses.AssignmentResponse assignment(TierAssignment a) {
        return new FeeResponses.AssignmentResponse(a.id(), a.memberId(), a.tierId(), a.validFrom().toString());
    }

    static FeeResponses.RateResponse rate(FeeRate r) {
        return new FeeResponses.RateResponse(r.id(), r.tierId(), money(r.monthlyAmount()), r.validFrom().toString(),
            r.resolutionReference());
    }

    FeeResponses.TierResponse tier(FeeTier t) {
        YearMonth now = YearMonth.now(clock);
        FeeResponses.RateResponse current = schedule.rates(t.id())
                                                    .stream()
                                                    .filter(r -> !r.validFrom().isAfter(now))
                                                    .reduce((earlier, later) -> later)
                                                    .map(FeeViews::rate)
                                                    .orElse(null);

        return new FeeResponses.TierResponse(t.id(), t.code(), t.name(), t.isDefault(), t.active(), current);
    }

    static FeeResponses.IbanResponse iban(ConfirmedIban i) {
        return new FeeResponses.IbanResponse(i.id(), i.iban(), i.memberId());
    }

    Map<Long, TMember> membersById(Collection<Long> ids) {
        Map<Long, TMember> result = new HashMap<>();
        members.findAllById(ids).forEach(m -> result.put(m.getId(), m));
        return result;
    }

    private static FeeResponses.MemberRef ref(TMember m) {
        return new FeeResponses.MemberRef(m.getId(), Names.display(m), m.getMemberId());
    }

    FeeResponses.StatementReportResponse report(StatementReport r) {
        Map<Long, TMember> matched = membersById(
            r.transactions().stream().map(t -> t.memberId()).filter(Objects::nonNull).distinct().toList());
        var totals = r.totals();

        return new FeeResponses.StatementReportResponse(r.importId(), r.fileName(), r.format(), r.accountIban(),
            r.currency(), r.statementId(), r.from(), r.to(), r.alreadyImported(),
            new FeeResponses.TotalsResponse(totals.transactions(), totals.newTransactions(), totals.duplicates(),
                totals.matchedByReference(), totals.matchedByIban(), totals.unmatched(), totals.foreignCurrency(),
                totals.outgoing(), money(totals.matchedAmount()), money(totals.unmatchedAmount())),
            r.transactions()
             .stream()
             .map(t -> new FeeResponses.OutcomeRow(t.bookingDate(), money(t.amount()), t.currency(),
                 t.counterpartyName(), t.counterpartyIban(), t.title(), t.outcome().name(),
                 t.memberId() == null ? null : ref(matched.get(t.memberId()))))
             .toList());
    }

    static FeeResponses.StatementImportResponse statementImport(StatementImportView i) {
        return new FeeResponses.StatementImportResponse(i.id(), i.fileName(), i.format(), i.accountIban(),
            i.currency(), i.statementId(), i.from(), i.to(), i.transactions(), i.newTransactions(), i.duplicates(),
            i.matched(), i.unmatched(), i.outgoing(), i.importedBy(), i.importedAt());
    }

    List<FeeResponses.TransactionResponse> transactions(List<BankTransactionView> list) {
        Map<Long, TMember> payers = membersById(
            list.stream().flatMap(t -> t.payments().stream()).map(p -> p.memberId()).distinct().toList());

        return list.stream()
                   .map(t -> new FeeResponses.TransactionResponse(t.id(), t.importId(), t.accountIban(),
                       t.bookingDate(), t.valueDate(), money(t.amount()), t.currency(), t.counterpartyName(),
                       t.counterpartyIban(), t.title(), t.bankReference(), t.status(), t.matchMethod(),
                       t.ignoredNote(), t.decidedBy(), t.decidedAt(),
                       t.payments()
                        .stream()
                        .map(p -> new FeeResponses.TransactionPaymentResponse(p.paymentId(),
                            ref(payers.get(p.memberId())), money(p.amount()), p.reversed()))
                        .toList()))
                   .toList();
    }
}
