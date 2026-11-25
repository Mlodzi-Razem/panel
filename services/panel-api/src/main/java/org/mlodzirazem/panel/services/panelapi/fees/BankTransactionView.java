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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.BankMatchMethod;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.BankTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record BankTransactionView(
    long id,
    long importId,
    String accountIban,
    LocalDate bookingDate,
    @Nullable LocalDate valueDate,
    BigDecimal amount,
    String currency,
    @Nullable String counterpartyName,
    @Nullable String counterpartyIban,
    String title,
    @Nullable String bankReference,
    BankTransactionStatus status,
    @Nullable BankMatchMethod matchMethod,
    @Nullable String ignoredNote,
    @Nullable Long decidedBy,
    @Nullable ZonedDateTime decidedAt,
    List<TransactionPayment> payments
) {
}
