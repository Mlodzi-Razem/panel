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
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentDetails;
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentService;
import org.mlodzirazem.panel.services.panelapi.fees.AdjustmentView;
import org.mlodzirazem.panel.services.panelapi.fees.DuesService;
import org.mlodzirazem.panel.services.panelapi.fees.NewAdjustment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustmentRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class DefaultAdjustmentService implements AdjustmentService {
    private static final YearMonth FOREVER = YearMonth.of(9999, 12);

    private final TFeeAdjustmentRepository adjustments;
    private final TMemberRepository members;
    private final FeeReader reader;
    private final FeeLock lock;
    private final DuesService duesService;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional
    public AdjustmentDetails grant(long actorMemberId, NewAdjustment request) {
        lock.acquire();

        TMember member = members.findById(request.memberId())
                                .orElseThrow(() -> new NotFoundException("Member", request.memberId()));

        if (request.from() == null || request.kind() == null || request.reasonCategory() == null) {
            throw new RuleViolationException("ADJUSTMENT_RANGE_INVALID", "Kind, reason category and start are required");
        }

        if (request.to() != null && request.to().isBefore(request.from())) {
            throw new RuleViolationException("ADJUSTMENT_RANGE_INVALID", "The end month precedes the start month");
        }

        if (request.justification() == null || request.justification().isBlank()) {
            throw new RuleViolationException("ADJUSTMENT_REASON_REQUIRED", "A justification is required");
        }

        BigDecimal discount = null;

        if (request.kind() == AdjustmentKind.DISCOUNT) {
            if (request.discountAmount() == null) {
                throw new RuleViolationException("ADJUSTMENT_AMOUNT_REQUIRED", "A discount needs the reduced amount");
            }

            discount = Money.positive(request.discountAmount(), "ADJUSTMENT_AMOUNT_INVALID");
        } else if (request.discountAmount() != null) {
            throw new RuleViolationException("ADJUSTMENT_AMOUNT_NOT_ALLOWED", "Only discounts carry an amount");
        }

        YearMonth end = request.to() == null ? FOREVER : request.to();

        for (TFeeAdjustment other : adjustments.findByMemberIdAndKind(member.getId(), request.kind())) {
            YearMonth otherEnd = effectiveEnd(other);

            if (!otherEnd.isBefore(Months.of(other.getFromMonth()))
                && !request.from().isAfter(otherEnd)
                && !end.isBefore(Months.of(other.getFromMonth()))) {
                throw new ConflictException("ADJUSTMENT_OVERLAP",
                    "An adjustment of the same kind already covers some of these months");
            }
        }

        TFeeAdjustment adjustment = new TFeeAdjustment();
        adjustment.setMember(member);
        adjustment.setKind(request.kind());
        adjustment.setFromMonth(Months.day(request.from()));
        adjustment.setToMonth(request.to() == null ? null : Months.day(request.to()));
        adjustment.setDiscountAmount(discount);
        adjustment.setReasonCategory(request.reasonCategory());
        adjustment.setJustification(request.justification().strip());
        adjustment.setResolutionReference(
            request.resolutionReference() == null || request.resolutionReference().isBlank()
                ? null : request.resolutionReference().strip());
        adjustment.setGrantedBy(members.getReferenceById(actorMemberId));
        adjustment.setCreatedBy(members.getReferenceById(actorMemberId));
        adjustments.saveAndFlush(adjustment);

        auditLog.record("fees :: adjustment-grant", AuditPayload.of("memberId", member.getId())
                                                                .withId("adjustmentId", adjustment.getId())
                                                                .withFields(List.of("fromMonth", "kind", "toMonth")));

        AdjustmentDetails result = FeeReader.details(adjustment);
        duesService.refresh(member.getId());

        return result;
    }

    @Override
    @Transactional
    public AdjustmentDetails revoke(long actorMemberId, long adjustmentId) {
        return revoke(actorMemberId, adjustmentId, YearMonth.now(clock));
    }

    @Override
    @Transactional
    public AdjustmentDetails revoke(long actorMemberId, long adjustmentId, YearMonth from) {
        lock.acquire();

        TFeeAdjustment adjustment = adjustments.findById(adjustmentId)
                                               .orElseThrow(() -> new NotFoundException("Fee adjustment", adjustmentId));

        if (adjustment.getRevokedAt() != null) {
            throw new ConflictException("ADJUSTMENT_ALREADY_REVOKED", "The adjustment is already revoked");
        }

        adjustment.setRevokedAt(ZonedDateTime.now(clock));
        adjustment.setRevokedBy(members.getReferenceById(actorMemberId));
        adjustment.setRevokedFromMonth(Months.day(from));
        adjustments.saveAndFlush(adjustment);

        long memberId = adjustment.getMember().getId();

        auditLog.record("fees :: adjustment-revoke", AuditPayload.of("memberId", memberId)
                                                                 .withId("adjustmentId", adjustmentId)
                                                                 .withFields(List.of("revokedFromMonth")));

        AdjustmentDetails result = FeeReader.details(adjustment);
        duesService.refresh(memberId);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdjustmentView> adjustmentsOf(long memberId) {
        return reader.adjustmentEntities(memberId).stream().map(FeeReader::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdjustmentDetails> detailsOf(long memberId) {
        return reader.adjustmentEntities(memberId).stream().map(FeeReader::details).toList();
    }

    private static YearMonth effectiveEnd(TFeeAdjustment adjustment) {
        YearMonth end = adjustment.getToMonth() == null ? FOREVER : Months.of(adjustment.getToMonth());

        if (adjustment.getRevokedFromMonth() != null) {
            YearMonth beforeRevocation = Months.of(adjustment.getRevokedFromMonth()).minusMonths(1);
            end = end.isAfter(beforeRevocation) ? beforeRevocation : end;
        }

        return end;
    }
}
