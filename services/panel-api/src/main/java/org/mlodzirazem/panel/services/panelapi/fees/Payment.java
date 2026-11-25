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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.PaymentSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * @param allocated the part allocated to dues; the rest of a non-reversed payment is credit
 */
public record Payment(
    long id,
    long memberId,
    BigDecimal amount,
    BigDecimal allocated,
    LocalDate paidOn,
    PaymentSource source,
    @Nullable Long bankTransactionId,
    @Nullable String note,
    long recordedBy,
    @Nullable ZonedDateTime reversedAt,
    @Nullable Long reversedBy,
    @Nullable String reversalReason
) {
    public boolean reversed() {
        return reversedAt != null;
    }
}
