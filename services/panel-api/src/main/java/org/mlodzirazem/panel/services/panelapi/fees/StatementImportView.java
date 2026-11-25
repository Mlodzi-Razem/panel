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

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record StatementImportView(
    long id,
    String fileName,
    String format,
    String accountIban,
    String currency,
    @Nullable String statementId,
    @Nullable LocalDate from,
    @Nullable LocalDate to,
    int transactions,
    int newTransactions,
    int duplicates,
    int matched,
    int unmatched,
    int outgoing,
    long importedBy,
    @Nullable ZonedDateTime importedAt
) {
}
