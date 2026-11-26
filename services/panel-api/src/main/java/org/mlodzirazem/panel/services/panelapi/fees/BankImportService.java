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

import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.BankTransactionStatus;

import java.io.InputStream;
import java.util.List;

/**
 * Bank statement import and the unmatched queue (ZK Treasurer, FEES_MANAGE enforced by the caller). Errors:
 * STATEMENT_ALREADY_IMPORTED (conflict), FILE_TOO_LARGE, FILE_EMPTY, NOT_FOUND, TRANSACTION_NOT_UNMATCHED (conflict),
 * TRANSACTION_NOT_REOPENABLE (conflict), TRANSACTION_CURRENCY_UNSUPPORTED, SPLITS_REQUIRED, SPLIT_AMOUNT_INVALID,
 * SPLIT_DUPLICATE_MEMBER, SPLIT_SUM_MISMATCH, SELF_FEES_NOT_ALLOWED (matching a transfer to oneself by hand), IBAN_ALREADY_CONFIRMED (conflict), plus the parser errors
 * ({@link org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException}).
 */
public interface BankImportService {
    long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /**
     * Analyses the statement without saving anything.
     */
    StatementReport preview(long actorMemberId, InputStream statement, String fileName);

    /**
     * Saves the import and its new transactions: PLN credits identified by a payment reference in the title, else
     * by a confirmed sender IBAN, become BANK payments; other credits wait as UNMATCHED; debits are OUTGOING.
     * Transactions already imported earlier (same account and stable key) are skipped.
     */
    StatementReport apply(long actorMemberId, InputStream statement, String fileName);

    /**
     * Newest first.
     */
    PagedResult<StatementImportView> imports(int page, int size);

    /**
     * Newest booking first.
     */
    PagedResult<BankTransactionView> transactions(BankTransactionStatus status, int page, int size);

    BankTransactionView transaction(long transactionId);

    /**
     * Matches an UNMATCHED transfer to one or more members; the splits must add up to the transfer amount.
     * {@code rememberIban} confirms the sender IBAN for the member when there is exactly one split.
     */
    BankTransactionView assign(long actorMemberId, long transactionId, List<Split> splits, boolean rememberIban);

    BankTransactionView ignore(long actorMemberId, long transactionId, String note);

    /**
     * Reverses the payments of a MATCHED transfer (a reason is then required) or un-ignores an IGNORED one and puts it
     * back to UNMATCHED.
     */
    BankTransactionView reopen(long actorMemberId, long transactionId, String reason);
}
