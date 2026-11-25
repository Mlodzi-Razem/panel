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
import org.mlodzirazem.panel.services.panelapi.fees.ResolvedFee;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustmentRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeDue;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeDueRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePayment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentAllocation;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentAllocationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeTierRateRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeTierRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TMemberFeeTier;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TMemberFeeTierRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

@Component
@RequiredArgsConstructor
class DuesSynchronizer {
    private final TFeeTierRepository tiers;
    private final TFeeTierRateRepository rates;
    private final TFeeDueRepository dues;
    private final TMemberFeeTierRepository assignments;
    private final TFeeAdjustmentRepository adjustments;
    private final TFeePaymentRepository payments;
    private final TFeePaymentAllocationRepository allocations;

    static final class Tally {
        int members;
        int created;
        int updated;
        int cancelled;
    }

    private record Key(long paymentId, long dueId) {
    }

    private record Planned(TFeePayment payment, TFeeDue due, BigDecimal amount) {
    }

    FeeContext context() {
        Long defaultTier = tiers.findByDefaultTierTrue().map(t -> t.getId()).orElse(null);
        Map<Long, List<FeeContext.RateStep>> steps = new HashMap<>();

        rates.findAllByOrderByTierIdAscValidFromAsc().forEach(r -> steps
            .computeIfAbsent(r.getTier().getId(), k -> new ArrayList<>())
            .add(new FeeContext.RateStep(Months.of(r.getValidFrom()), r.getMonthlyAmount())));

        return new FeeContext(defaultTier, steps);
    }

    void sync(FeeContext context, TMember member, YearMonth upTo, Tally tally) {
        tally.members++;

        Map<YearMonth, TFeeDue> byMonth = new TreeMap<>();
        dues.findByMemberIdOrderByDueMonthAsc(member.getId()).forEach(d -> byMonth.put(Months.of(d.getDueMonth()), d));

        List<TMemberFeeTier> assigned = assignments.findByMemberIdOrderByValidFromAsc(member.getId());
        List<TFeeAdjustment> memberAdjustments = adjustments.findByMemberIdOrderByFromMonthAscIdAsc(member.getId());

        TreeSet<YearMonth> months = new TreeSet<>(byMonth.keySet());
        YearMonth first = FeeRules.firstDueMonth(member);

        if (first != null && member.getMembershipType() == MembershipType.ORDINARY) {
            for (YearMonth month = first; !month.isAfter(upTo); month = month.plusMonths(1)) {
                if (FeeRules.owesDueFor(member, month)) {
                    months.add(month);
                }
            }
        }

        for (YearMonth month : months) {
            TFeeDue due = byMonth.get(month);

            if (due != null && due.getStatus() == DueStatus.PAID) {
                continue;
            }

            Optional<ResolvedFee> price = FeePricing.price(context, assigned, memberAdjustments, month);

            if (price.isEmpty()) {
                continue;
            }

            ResolvedFee fee = price.get();

            if (due == null) {
                if (fee.exempt()) {
                    continue;
                }

                due = new TFeeDue();
                due.setMember(member);
                due.setDueMonth(Months.day(month));
                due.setAmount(fee.amount());
                due.setTier(tiers.getReferenceById(fee.tierId()));
                due.setStatus(DueStatus.OPEN);
                dues.save(due);
                byMonth.put(month, due);
                tally.created++;
            } else if (fee.exempt()) {
                if (due.getStatus() == DueStatus.OPEN) {
                    due.setStatus(DueStatus.CANCELLED);
                    tally.cancelled++;
                }
            } else {
                boolean changed = due.getAmount().compareTo(fee.amount()) != 0
                    || !due.getTier().getId().equals(fee.tierId())
                    || due.getStatus() == DueStatus.CANCELLED;

                if (changed) {
                    due.setAmount(fee.amount());
                    due.setTier(tiers.getReferenceById(fee.tierId()));
                    due.setStatus(DueStatus.OPEN);
                    tally.updated++;
                }
            }
        }

        allocate(member, byMonth.values().stream().filter(d -> d.getStatus() != DueStatus.CANCELLED).toList());
    }

    private void allocate(TMember member, List<TFeeDue> billable) {
        List<TFeePayment> effective = payments.findEffectiveByMemberId(member.getId());
        List<TFeePaymentAllocation> existing = allocations.findByMemberId(member.getId());

        List<TFeeDue> ordered = billable.stream().sorted(Comparator.comparing(TFeeDue::getDueMonth)).toList();
        BigDecimal[] need = new BigDecimal[ordered.size()];

        for (int i = 0; i < need.length; i++) {
            need[i] = ordered.get(i).getAmount();
        }

        Map<Key, Planned> planned = new LinkedHashMap<>();
        Map<Long, BigDecimal> covered = new HashMap<>();
        int index = 0;

        for (TFeePayment payment : effective) {
            BigDecimal left = payment.getAmount();

            while (left.signum() > 0 && index < need.length) {
                BigDecimal take = left.min(need[index]);
                TFeeDue due = ordered.get(index);

                planned.put(new Key(payment.getId(), due.getId()), new Planned(payment, due, take));
                covered.merge(due.getId(), take, BigDecimal::add);
                need[index] = need[index].subtract(take);
                left = left.subtract(take);

                if (need[index].signum() == 0) {
                    index++;
                }
            }
        }

        List<TFeePaymentAllocation> obsolete = new ArrayList<>();

        for (TFeePaymentAllocation allocation : existing) {
            Key key = new Key(allocation.getPayment().getId(), allocation.getDue().getId());
            Planned plan = planned.remove(key);

            if (plan == null) {
                obsolete.add(allocation);
            } else if (allocation.getAmount().compareTo(plan.amount()) != 0) {
                allocation.setAmount(plan.amount());
            }
        }

        allocations.deleteAll(obsolete);

        for (Planned plan : planned.values()) {
            TFeePaymentAllocation allocation = new TFeePaymentAllocation();
            allocation.setPayment(plan.payment());
            allocation.setDue(plan.due());
            allocation.setAmount(plan.amount());
            allocations.save(allocation);
        }

        for (TFeeDue due : ordered) {
            BigDecimal paid = covered.getOrDefault(due.getId(), BigDecimal.ZERO);
            due.setStatus(paid.compareTo(due.getAmount()) >= 0 ? DueStatus.PAID : DueStatus.OPEN);
        }
    }
}
