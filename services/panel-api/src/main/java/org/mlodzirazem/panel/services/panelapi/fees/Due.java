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

package org.mlodzirazem.panel.services.panelapi.fees;

import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * @param amount the amount the due was generated with
 * @param paid   the part covered by payment allocations
 */
public record Due(long id, long memberId, YearMonth month, BigDecimal amount, BigDecimal paid, long tierId, DueStatus status) {
    public BigDecimal outstanding() {
        return status == DueStatus.CANCELLED ? BigDecimal.ZERO.setScale(2) : amount.subtract(paid);
    }
}
