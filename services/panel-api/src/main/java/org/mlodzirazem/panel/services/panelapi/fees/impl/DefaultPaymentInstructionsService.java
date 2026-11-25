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
import org.mlodzirazem.panel.services.panelapi.fees.PaymentInstructions;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentInstructionsService;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentReferences;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class DefaultPaymentInstructionsService implements PaymentInstructionsService {
    private final PaymentReferences references;
    private final FeesProperties properties;

    @Override
    @Transactional(readOnly = true)
    public PaymentInstructions of(long memberId) {
        String reference = references.referenceOf(memberId);

        return new PaymentInstructions(reference, properties.bankAccount(), properties.recipientName(),
            properties.titleTemplate().replace("{reference}", reference));
    }
}
