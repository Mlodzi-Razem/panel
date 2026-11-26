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
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Names;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyService;
import org.mlodzirazem.panel.services.panelapi.fees.ArrearsService;
import org.mlodzirazem.panel.services.panelapi.fees.DistrictScope;
import org.mlodzirazem.panel.services.panelapi.fees.FeeOverviewService;
import org.mlodzirazem.panel.services.panelapi.fees.FeeScheduleService;
import org.mlodzirazem.panel.services.panelapi.fees.MemberArrears;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentReferences;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading side for FEES_READ holders; district roles see members of their district (primary affiliation) only.
 */
@RestController
@RequestMapping("/fees")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeesController {
    private static final int MAX_OVERVIEW_MONTHS = 36;

    private final FeeAccess access;
    private final FeeViews views;
    private final FeeOverviewService overview;
    private final ArrearsService arrears;
    private final PaymentService payments;
    private final PaymentReferences references;
    private final FeeScheduleService schedule;
    private final BodyService bodies;

    @GetMapping("/overview")
    public FeeResponses.OverviewResponse overview(
        @RequestParam(name = "months", defaultValue = "12") int months,
        @RequestParam(name = "districtId", required = false) @Nullable Long districtId
    ) {
        DistrictScope scope = access.scope(access.reader(), districtId);

        return new FeeResponses.OverviewResponse(districtId,
            overview.overview(Math.min(MAX_OVERVIEW_MONTHS, Math.max(1, months)), scope)
                    .stream()
                    .map(m -> new FeeResponses.MonthOverviewResponse(m.month().toString(), FeeViews.money(m.expected()),
                        FeeViews.money(m.collected()), FeeViews.money(m.outstanding()), m.members(),
                        m.membersPaid(), m.membersOutstanding()))
                    .toList());
    }

    /**
     * @param longerThan only arrears lasting longer than this many months (0 lists every overdue member)
     */
    @GetMapping("/arrears")
    public List<FeeResponses.ArrearsRow> arrears(
        @RequestParam(name = "longerThan", defaultValue = "0") int longerThan,
        @RequestParam(name = "districtId", required = false) @Nullable Long districtId
    ) {
        DistrictScope scope = access.scope(access.reader(), districtId);
        List<MemberArrears> found = arrears.longerThan(Math.min(1200, Math.max(0, longerThan)), scope);
        Map<Long, TMember> members = views.membersById(found.stream().map(MemberArrears::memberId).toList());
        Map<Long, String> districtNames = new HashMap<>();

        for (TBody district : bodies.districts()) {
            districtNames.put(district.getId(), district.getName());
        }

        return found.stream().map(row -> {
            TMember member = members.get(row.memberId());
            var a = row.arrears();

            return new FeeResponses.ArrearsRow(member.getId(), member.getMemberId(), Names.display(member),
                row.districtId(), row.districtId() == null ? null : districtNames.get(row.districtId()), a.since(),
                a.months(), FeeViews.money(a.overdueAmount()), a.overdueDues(), a.suspendsElectoralRights(),
                a.groundsForExclusion());
        }).toList();
    }

    @GetMapping("/members/{memberId}")
    public FeeResponses.FeeCardResponse card(@PathVariable("memberId") long memberId) {
        Caller caller = access.readerOf(memberId).caller();
        boolean manager = FeeAccess.isManager(caller);
        boolean reasons = manager || caller.id() == memberId;

        var history = payments.history(memberId);
        var a = arrears.of(memberId);

        return new FeeResponses.FeeCardResponse(
            memberId,
            references.referenceOf(memberId),
            FeeViews.balance(payments.balance(memberId)),
            new FeeResponses.CardArrears(a.since(), a.months(), FeeViews.money(a.overdueAmount()), a.overdueDues(),
                a.suspendsElectoralRights(), a.groundsForExclusion()),
            schedule.assignments(memberId).stream().map(FeeViews::assignment).toList(),
            history.dues().stream().map(FeeViews::due).toList(),
            history.payments().stream().map(p -> FeeViews.payment(p, manager)).toList(),
            views.adjustmentsOf(memberId, reasons),
            reasons
        );
    }

    @GetMapping("/tiers")
    public List<FeeResponses.TierResponse> tiers(
        @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive
    ) {
        access.reader();
        return schedule.tiers(includeInactive).stream().map(views::tier).toList();
    }

    @GetMapping("/tiers/{tierId}/rates")
    public List<FeeResponses.RateResponse> rates(@PathVariable("tierId") long tierId) {
        access.reader();
        return schedule.rates(tierId).stream().map(FeeViews::rate).toList();
    }
}
