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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.bankimport.Ibans;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("panel.fees")
public record FeesProperties(
    @Nullable String bankAccount,
    @DefaultValue("") String recipientName,
    @DefaultValue("") String titleTemplate
) {
    private static final String DEFAULT_RECIPIENT = "Stowarzyszenie Młodzi Razem";
    private static final String DEFAULT_TITLE = "{reference} SKŁADKA CZŁONKOWSKA STOWARZYSZENIE MŁODZI RAZEM";

    public FeesProperties {
        recipientName = recipientName.isBlank() ? DEFAULT_RECIPIENT : recipientName;
        titleTemplate = titleTemplate.isBlank() ? DEFAULT_TITLE : titleTemplate;

        if (bankAccount != null && bankAccount.isBlank()) {
            bankAccount = null;
        }

        if (bankAccount != null) {
            bankAccount = Ibans.tryNormalise(bankAccount)
                               .orElseThrow(() -> new IllegalArgumentException("panel.fees.bank-account is not a valid IBAN"));
        }

        if (!titleTemplate.contains("{reference}")) {
            throw new IllegalArgumentException("panel.fees.title-template must contain {reference}");
        }
    }
}
