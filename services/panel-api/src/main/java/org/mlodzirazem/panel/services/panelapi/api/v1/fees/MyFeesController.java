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
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.fees.ArrearsService;
import org.mlodzirazem.panel.services.panelapi.fees.DuesService;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentInstructionsService;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own fees; the member id never comes from the request.
 */
@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyFeesController {
    private final CallerResolver callers;
    private final PaymentInstructionsService instructions;
    private final PaymentService payments;
    private final DuesService dues;
    private final ArrearsService arrears;
    private final FeeViews views;

    @GetMapping("/me/fees")
    public FeeResponses.MeFeesResponse mine() {
        Caller caller = callers.require();
        long id = caller.id();
        boolean internal = FeeAccess.isManager(caller);

        var how = instructions.of(id);
        var history = payments.history(id);
        var own = arrears.of(id);

        return new FeeResponses.MeFeesResponse(
            how.reference(),
            new FeeResponses.PayTo(how.accountIban(), how.recipientName(), how.title()),
            FeeViews.balance(payments.balance(id)),
            dues.nextDue(id)
                .map(n -> new FeeResponses.NextDueResponse(n.month().toString(), FeeViews.money(n.amount())))
                .orElse(null),
            payments.openDues(id).stream().map(FeeViews::due).toList(),
            new FeeResponses.HistoryResponse(
                history.dues().stream().map(FeeViews::due).toList(),
                history.payments().stream().map(p -> FeeViews.payment(p, internal)).toList(),
                views.adjustmentsOf(id, true)),
            new FeeResponses.SelfArrears(own.since(), own.months(), own.suspendsElectoralRights())
        );
    }
}
