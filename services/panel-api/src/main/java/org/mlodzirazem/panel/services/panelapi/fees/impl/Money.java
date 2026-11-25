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

import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;

import java.math.BigDecimal;

final class Money {
    private Money() {
    }

    static BigDecimal positive(BigDecimal amount, String code) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new RuleViolationException(code, "Amount must be positive with at most two decimal places");
        }

        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    static String text(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new RuleViolationException(code, "A value is required");
        }

        return value.strip();
    }
}
