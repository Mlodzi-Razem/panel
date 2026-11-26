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
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentService;
import org.mlodzirazem.panel.services.panelapi.fees.FeeScheduleService;
import org.mlodzirazem.panel.services.panelapi.fees.FeeTierUpdate;
import org.mlodzirazem.panel.services.panelapi.fees.NewAdjustment;
import org.mlodzirazem.panel.services.panelapi.fees.NewFeeTier;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentService;
import org.mlodzirazem.panel.services.panelapi.fees.RecordPayment;
import org.mlodzirazem.panel.services.panelapi.fees.SenderIbans;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.PaymentSource;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Everything that changes the fee register; global FEES_MANAGE only.
 */
@RestController
@RequestMapping("/fees")
@RequiredArgsConstructor
@Transactional
public class FeeManagementController {
    private final FeeAccess access;
    private final FeeViews views;
    private final FeeScheduleService schedule;
    private final AdjustmentService adjustments;
    private final PaymentService payments;
    private final TFeePaymentRepository paymentRepository;
    private final SenderIbans senderIbans;

    public record TierRequest(@Nullable String code, @Nullable String name, @Nullable Boolean makeDefault) {
    }

    public record TierPatch(@Nullable String name, @Nullable Boolean active, @Nullable Boolean makeDefault) {
    }

    public record RateRequest(@Nullable String monthlyAmount, @Nullable String validFrom,
                              @Nullable String resolutionReference) {
    }

    public record AssignmentRequest(@Nullable Long tierId, @Nullable String validFrom) {
    }

    public record AdjustmentRequest(@Nullable String kind, @Nullable String from, @Nullable String to,
                                    @Nullable String discountAmount, @Nullable String reasonCategory,
                                    @Nullable String justification, @Nullable String resolutionReference) {
    }

    public record RevokeRequest(@Nullable String from) {
    }

    public record PaymentRequest(@Nullable String amount, @Nullable String paidOn, @Nullable String source,
                                 @Nullable String note) {
    }

    public record ReversalRequest(@Nullable String reason) {
    }

    public record IbanRequest(@Nullable String iban) {
    }

    @PostMapping("/tiers")
    @ResponseStatus(HttpStatus.CREATED)
    public FeeResponses.TierResponse createTier(@RequestBody TierRequest request) {
        Caller caller = access.manager();

        return views.tier(schedule.createTier(caller.id(), new NewFeeTier(
            Parse.required("code", request.code()), Parse.required("name", request.name()),
            Boolean.TRUE.equals(request.makeDefault()))));
    }

    @PatchMapping("/tiers/{tierId}")
    public FeeResponses.TierResponse updateTier(@PathVariable("tierId") long tierId, @RequestBody TierPatch request) {
        Caller caller = access.manager();

        return views.tier(schedule.updateTier(caller.id(), tierId,
            new FeeTierUpdate(request.name(), request.active(), request.makeDefault())));
    }

    @PostMapping("/tiers/{tierId}/rates")
    @ResponseStatus(HttpStatus.CREATED)
    public FeeResponses.RateResponse addRate(@PathVariable("tierId") long tierId, @RequestBody RateRequest request) {
        Caller caller = access.manager();

        return FeeViews.rate(schedule.addRate(caller.id(), tierId, Parse.month("validFrom", request.validFrom()),
            Parse.money("monthlyAmount", request.monthlyAmount()),
            Parse.required("resolutionReference", request.resolutionReference())));
    }

    @PostMapping("/members/{memberId}/tier")
    public FeeResponses.AssignmentResponse assignTier(
        @PathVariable("memberId") long memberId,
        @RequestBody AssignmentRequest request
    ) {
        Caller caller = access.manager();

        return FeeViews.assignment(schedule.assignTier(caller.id(), memberId,
            Parse.requiredId("tierId", request.tierId()), Parse.month("validFrom", request.validFrom())));
    }

    @PostMapping("/members/{memberId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public FeeResponses.AdjustmentResponse grant(
        @PathVariable("memberId") long memberId,
        @RequestBody AdjustmentRequest request
    ) {
        Caller caller = access.manager();
        String discount = request.discountAmount();

        return FeeViews.adjustment(adjustments.grant(caller.id(), new NewAdjustment(memberId,
            Parse.enumOf("kind", request.kind(), AdjustmentKind.class),
            Parse.month("from", request.from()),
            Parse.optionalMonth("to", request.to()),
            discount == null || discount.isBlank() ? null : Parse.money("discountAmount", discount),
            Parse.enumOf("reasonCategory", request.reasonCategory(), AdjustmentReason.class),
            Parse.required("justification", request.justification()),
            request.resolutionReference())));
    }

    @PostMapping("/adjustments/{adjustmentId}/revoke")
    public FeeResponses.AdjustmentResponse revoke(
        @PathVariable("adjustmentId") long adjustmentId,
        @RequestBody RevokeRequest request
    ) {
        Caller caller = access.manager();
        var from = Parse.optionalMonth("from", request.from());

        return FeeViews.adjustment(from == null
            ? adjustments.revoke(caller.id(), adjustmentId)
            : adjustments.revoke(caller.id(), adjustmentId, from));
    }

    /**
     * Payments made outside the statement import (cash, other); bank transfers come from statements.
     */
    @PostMapping("/members/{memberId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public FeeResponses.PaymentResponse record(
        @PathVariable("memberId") long memberId,
        @RequestBody PaymentRequest request
    ) {
        Caller caller = access.manager();
        PaymentSource source = Parse.enumOf("source", request.source(), PaymentSource.class);

        if (source == PaymentSource.BANK) {
            throw ApiException.badRequest("INVALID_VALUE", "Bank transfers are recorded by importing a statement",
                List.of("source"));
        }

        return FeeViews.payment(payments.record(caller.id(), new RecordPayment(memberId,
            Parse.money("amount", request.amount()), Parse.date("paidOn", request.paidOn()), source, null,
            request.note())), true);
    }

    @PostMapping("/payments/{paymentId}/reverse")
    public FeeResponses.PaymentResponse reverse(
        @PathVariable("paymentId") long paymentId,
        @RequestBody ReversalRequest request
    ) {
        Caller caller = access.manager();
        String reason = Parse.required("reason", request.reason());

        // reversing one part of a transfer would leave the transfer marked as matched
        if (paymentRepository.findById(paymentId).filter(p -> p.getBankTransactionId() != null).isPresent()) {
            throw new RuleViolationException("PAYMENT_FROM_BANK_TRANSACTION",
                "Reopen the bank transaction to reverse its payments");
        }

        return FeeViews.payment(payments.reverse(caller.id(), paymentId, reason), true);
    }

    @GetMapping("/members/{memberId}/ibans")
    public List<FeeResponses.IbanResponse> ibans(@PathVariable("memberId") long memberId) {
        access.manager();
        return senderIbans.of(memberId).stream().map(FeeViews::iban).toList();
    }

    @PostMapping("/members/{memberId}/ibans")
    @ResponseStatus(HttpStatus.CREATED)
    public FeeResponses.IbanResponse confirmIban(
        @PathVariable("memberId") long memberId,
        @RequestBody IbanRequest request
    ) {
        Caller caller = access.manager();

        return FeeViews.iban(senderIbans.confirm(caller.id(), Parse.required("iban", request.iban()), memberId));
    }

    /**
     * The IBAN travels in the body so that it does not end up in access logs.
     */
    @PostMapping("/ibans/forget")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgetIban(@RequestBody IbanRequest request) {
        Caller caller = access.manager();
        senderIbans.forget(caller.id(), Parse.required("iban", request.iban()));
    }
}
