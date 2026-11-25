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

package org.mlodzirazem.panel.services.panelapi.bankimport;

import static org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException.Code.MALFORMED;
import static org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException.Code.MIXED_CURRENCIES;
import static org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException.Code.MULTIPLE_ACCOUNTS;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class StatementAssembler {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private StatementAssembler() {
    }

    static StatementFile assemble(BankStatementFormat format, List<StatementPart> parts) {
        if (parts.isEmpty()) {
            throw new BankStatementParseException(MALFORMED, "The file contains no statements");
        }
        String iban = parts.getFirst().accountIban();
        String currency = null;
        for (StatementPart part : parts) {
            if (!part.accountIban().equals(iban)) {
                throw new BankStatementParseException(MULTIPLE_ACCOUNTS, "The file contains statements of several accounts");
            }
            if (part.currency() != null) {
                if (currency == null) {
                    currency = part.currency();
                } else if (!currency.equals(part.currency())) {
                    throw new BankStatementParseException(MIXED_CURRENCIES, "The file contains several currencies");
                }
            }
        }
        List<TransactionDraft> drafts = new ArrayList<>();
        parts.forEach(part -> drafts.addAll(part.drafts()));
        if (currency == null && !drafts.isEmpty()) {
            currency = drafts.getFirst().currency();
        }
        if (currency == null) {
            throw new BankStatementParseException(MALFORMED, "The statement currency could not be determined");
        }
        for (TransactionDraft draft : drafts) {
            if (!draft.currency().equals(currency)) {
                throw new BankStatementParseException(MIXED_CURRENCIES, "The file contains several currencies");
            }
        }

        BigDecimal opening = null;
        BigDecimal closing = null;
        LocalDate from = null;
        LocalDate to = null;
        for (StatementPart part : parts) {
            if (opening == null) {
                opening = part.openingBalance();
            }
            if (part.closingBalance() != null) {
                closing = part.closingBalance();
            }
            if (part.from() != null && (from == null || part.from().isBefore(from))) {
                from = part.from();
            }
            if (part.to() != null && (to == null || part.to().isAfter(to))) {
                to = part.to();
            }
        }
        String statementId = parts.getFirst().statementId();
        return new StatementFile(format, iban, currency, statementId, opening, closing, from, to, keyed(iban, drafts));
    }

    private static List<BankTransaction> keyed(String iban, List<TransactionDraft> drafts) {
        Map<String, Integer> occurrences = new HashMap<>();
        List<BankTransaction> result = new ArrayList<>(drafts.size());
        for (TransactionDraft draft : drafts) {
            String key;
            if (draft.keyReference() != null) {
                String base = draft.keyReference();
                int index = occurrences.merge("R:" + base, 1, Integer::sum);
                key = index == 1 ? base : base + "#" + index;
            } else {
                String fingerprint = String.join("\u001f",
                    iban,
                    draft.bookingDate().toString(),
                    draft.amount().stripTrailingZeros().toPlainString(),
                    normaliseTitle(draft.title()),
                    draft.counterpartyIban() == null ? "" : draft.counterpartyIban());
                int index = occurrences.merge("H:" + fingerprint, 1, Integer::sum) - 1;
                key = sha256(fingerprint + "\u001f" + index);
            }
            result.add(new BankTransaction(
                draft.bookingDate(), draft.valueDate(), draft.amount(), draft.currency(),
                draft.counterpartyName(), draft.counterpartyIban(), draft.title(), draft.bankReference(), key));
        }
        return result;
    }

    static String normaliseTitle(String title) {
        return WHITESPACE.matcher(title.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    static @Nullable String blankToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String stripped = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        return stripped.isEmpty() ? null : stripped;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
