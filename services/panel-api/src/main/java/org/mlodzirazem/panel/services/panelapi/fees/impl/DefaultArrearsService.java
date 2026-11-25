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
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.fees.Arrears;
import org.mlodzirazem.panel.services.panelapi.fees.ArrearsService;
import org.mlodzirazem.panel.services.panelapi.fees.DistrictScope;
import org.mlodzirazem.panel.services.panelapi.fees.MemberArrears;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.AdjustmentKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeAdjustmentRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeDue;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeDueRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentAllocationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.AffiliationKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class DefaultArrearsService implements ArrearsService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final TFeeDueRepository dues;
    private final TFeePaymentAllocationRepository allocations;
    private final TFeeAdjustmentRepository adjustments;
    private final TMemberRepository members;
    private final TMemberDistrictAffiliationRepository affiliations;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Arrears of(long memberId, LocalDate on) {
        List<TFeeDue> open = dues.findByMemberIdAndStatusOrderByDueMonthAsc(memberId, DueStatus.OPEN);

        if (open.isEmpty()) {
            return Arrears.none(on);
        }

        Map<Long, BigDecimal> paid = FeeReader.sums(allocations.sumByDueForMember(memberId));
        List<TFeeAdjustment> deferrals = adjustments.findByMemberIdAndKind(memberId, AdjustmentKind.DEFERRAL);

        LocalDate since = null;
        int overdue = 0;
        int justified = 0;
        BigDecimal overdueAmount = ZERO;
        BigDecimal justifiedAmount = ZERO;

        for (TFeeDue due : open) {
            YearMonth month = Months.of(due.getDueMonth());
            LocalDate dueDate = month.atEndOfMonth();

            if (!on.isAfter(dueDate)) {
                continue;
            }

            BigDecimal outstanding = due.getAmount().subtract(paid.getOrDefault(due.getId(), ZERO));
            LocalDate effective = dueDate;
            boolean deferred = false;

            for (TFeeAdjustment deferral : deferrals) {
                if (deferral.coversMonth(month)) {
                    deferred = true;
                    YearMonth last = deferral.lastCoveredMonth();
                    LocalDate end = last == null ? LocalDate.MAX : last.atEndOfMonth();
                    effective = end.isAfter(effective) ? end : effective;
                }
            }

            if (deferred && !on.isAfter(effective)) {
                justified++;
                justifiedAmount = justifiedAmount.add(outstanding);
            } else {
                overdue++;
                overdueAmount = overdueAmount.add(outstanding);
                since = since == null || effective.isBefore(since) ? effective : since;
            }
        }

        return Arrears.of(since, on, overdue, overdueAmount, justified, justifiedAmount);
    }

    @Override
    @Transactional(readOnly = true)
    public Arrears of(long memberId) {
        return of(memberId, LocalDate.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberArrears> longerThan(int months, DistrictScope scope, LocalDate on) {
        YearMonth latestMonth = YearMonth.from(on.minusMonths(months));
        List<Long> candidates = dues.findMemberIdsWithDuesUpTo(DueStatus.OPEN, latestMonth.atDay(1));

        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> districtByMember = new HashMap<>();
        affiliations.findActiveByMemberIdIn(candidates, on).stream()
                    .filter(a -> a.getKind() == AffiliationKind.PRIMARY)
                    .forEach(a -> districtByMember.put(a.getMember().getId(), a.getDistrict().getId()));

        Map<Long, TMember> candidateMembers = new HashMap<>();
        members.findAllById(candidates).forEach(m -> candidateMembers.put(m.getId(), m));

        return candidates.stream()
                         .filter(id -> candidateMembers.get(id) != null
                             && candidateMembers.get(id).getStatus() != MemberStatus.REMOVED)
                         .filter(id -> scope.includes(districtByMember.get(id)))
                         .map(id -> new MemberArrears(id, districtByMember.get(id), of(id, on)))
                         .filter(m -> m.arrears().longerThan(months))
                         .sorted(Comparator.comparing((MemberArrears m) -> m.arrears().since())
                                           .thenComparing(MemberArrears::memberId))
                         .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberArrears> longerThan(int months, DistrictScope scope) {
        return longerThan(months, scope, LocalDate.now(clock));
    }
}
