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

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class Mt940Parser {
    private static final Pattern TAG = Pattern.compile("^:(\\d{2}[A-Z]?):(.*)$");
    private static final Pattern BALANCE = Pattern.compile("^([CD])(\\d{6})([A-Z]{3})(\\d+(?:,\\d*)?)$");
    private static final Pattern STATEMENT_LINE = Pattern.compile(
        "^(\\d{6})(\\d{4})?(RC|RD|C|D)(?:[A-Z](?=\\d))?(\\d+(?:,\\d*)?)[A-Z]([A-Z0-9]{3})(.*)$");
    private static final Pattern STRUCTURED = Pattern.compile("^\\s*(?:\\d{3})?([~<^])\\d{2}");
    private static final int FALLBACK_WINDOW_DAYS = 180;

    private Mt940Parser() {
    }

    private record Field(String tag, int line, List<String> lines) {
    }

    private static final class Pending {
        @Nullable LocalDate booking;
        @Nullable LocalDate value;
        BigDecimal amount = BigDecimal.ZERO;
        @Nullable String bankReference;
        @Nullable String supplementary;
        @Nullable String details;
        @Nullable String counterpartyName;
        @Nullable String counterpartyIban;
        @Nullable String title;
        boolean detailed;
    }

    private static final class Block {
        @Nullable String reference;
        @Nullable String account;
        @Nullable String sequence;
        @Nullable BigDecimal opening;
        @Nullable String openingCurrency;
        @Nullable LocalDate openingDate;
        boolean openingFinal;
        @Nullable BigDecimal closing;
        @Nullable String closingCurrency;
        @Nullable LocalDate closingDate;
        boolean closingFinal;
        final List<Pending> transactions = new ArrayList<>();
        @Nullable Pending current;
    }

    static StatementFile parse(byte[] data) {
        String text = StatementDecoder.decode(data).replace("\r\n", "\n").replace('\r', '\n');
        List<Field> fields = fields(text);
        List<Block> blocks = new ArrayList<>();
        Block block = null;
        for (Field field : fields) {
            String tag = field.tag();
            if (tag.equals("20") || (tag.equals("25") && (block == null || block.account != null))) {
                block = new Block();
                blocks.add(block);
            }
            if (block == null) {
                throw new BankStatementParseException(MALFORMED, "Unexpected field before the statement header at line " + field.line());
            }
            apply(block, field);
        }
        List<StatementPart> parts = new ArrayList<>();
        for (Block each : blocks) {
            parts.add(toPart(each));
        }
        return StatementAssembler.assemble(BankStatementFormat.MT940, parts);
    }

    private static List<Field> fields(String text) {
        String content = text;
        int blockStart = content.indexOf("{4:");
        if (blockStart >= 0) {
            content = content.substring(blockStart + 3);
        }
        List<Field> fields = new ArrayList<>();
        Field current = null;
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = TAG.matcher(line);
            if (matcher.matches()) {
                current = new Field(matcher.group(1), i + 1, new ArrayList<>());
                current.lines().add(matcher.group(2));
                fields.add(current);
            } else if (line.equals("-") || line.startsWith("-}") || line.startsWith("}")) {
                current = null;
            } else if (current != null && !line.isBlank()) {
                current.lines().add(line);
            }
        }
        return fields;
    }

    private static void apply(Block block, Field field) {
        String first = field.lines().getFirst().strip();
        switch (field.tag()) {
            case "20" -> block.reference = first;
            case "25" -> block.account = first;
            case "28C" -> block.sequence = first;
            case "60F", "60M" -> openingBalance(block, field, first);
            case "62F", "62M" -> closingBalance(block, field, first);
            case "61" -> statementLine(block, field);
            case "86" -> details(block, field);
            case "64", "65" -> block.current = null;
            default -> {
            }
        }
    }

    private static BankStatementParseException malformed(Field field, String what) {
        return new BankStatementParseException(MALFORMED, what + " at line " + field.line());
    }

    private record ParsedBalance(BigDecimal amount, String currency, LocalDate date) {
    }

    private static ParsedBalance balance(Field field, String value) {
        Matcher matcher = BALANCE.matcher(value);
        if (!matcher.matches()) {
            throw malformed(field, "Invalid balance field");
        }
        BigDecimal amount = amount(field, matcher.group(4));
        if (matcher.group(1).equals("D")) {
            amount = amount.negate();
        }
        return new ParsedBalance(amount, matcher.group(3), date(field, matcher.group(2)));
    }

    private static void openingBalance(Block block, Field field, String value) {
        ParsedBalance parsed = balance(field, value);
        boolean preferred = field.tag().equals("60F");
        if (block.opening == null || (preferred && !block.openingFinal)) {
            block.opening = parsed.amount();
            block.openingCurrency = parsed.currency();
            block.openingDate = parsed.date();
            block.openingFinal = preferred;
        }
    }

    private static void closingBalance(Block block, Field field, String value) {
        ParsedBalance parsed = balance(field, value);
        boolean preferred = field.tag().equals("62F");
        if (preferred || !block.closingFinal) {
            block.closing = parsed.amount();
            block.closingCurrency = parsed.currency();
            block.closingDate = parsed.date();
            block.closingFinal = preferred;
        }
        block.current = null;
    }

    private static BigDecimal amount(Field field, String value) {
        String normalised = value.replace(',', '.');
        if (normalised.endsWith(".")) {
            normalised = normalised + "0";
        }
        try {
            return new BigDecimal(normalised);
        } catch (NumberFormatException e) {
            throw malformed(field, "Invalid amount");
        }
    }

    private static LocalDate date(Field field, String yymmdd) {
        try {
            return LocalDate.of(2000 + Integer.parseInt(yymmdd.substring(0, 2)),
                Integer.parseInt(yymmdd.substring(2, 4)), Integer.parseInt(yymmdd.substring(4, 6)));
        } catch (DateTimeException e) {
            throw malformed(field, "Invalid date");
        }
    }

    private static LocalDate entryDate(Field field, LocalDate value, String mmdd) {
        int month = Integer.parseInt(mmdd.substring(0, 2));
        int day = Integer.parseInt(mmdd.substring(2, 4));
        LocalDate best = null;
        for (int delta = -1; delta <= 1; delta++) {
            LocalDate candidate;
            try {
                candidate = LocalDate.of(value.getYear() + delta, month, day);
            } catch (DateTimeException e) {
                continue;
            }
            if (best == null || Math.abs(ChronoUnit.DAYS.between(value, candidate))
                < Math.abs(ChronoUnit.DAYS.between(value, best))) {
                best = candidate;
            }
        }
        if (best == null) {
            throw malformed(field, "Invalid entry date");
        }
        return Math.abs(ChronoUnit.DAYS.between(value, best)) > FALLBACK_WINDOW_DAYS ? value : best;
    }

    private static void statementLine(Block block, Field field) {
        Matcher matcher = STATEMENT_LINE.matcher(field.lines().getFirst().strip());
        if (!matcher.matches()) {
            throw malformed(field, "Invalid statement line");
        }
        Pending pending = new Pending();
        LocalDate value = date(field, matcher.group(1));
        pending.value = value;
        pending.booking = matcher.group(2) == null ? value : entryDate(field, value, matcher.group(2));
        BigDecimal amount = amount(field, matcher.group(4)).abs();
        String mark = matcher.group(3);
        boolean positive = mark.equals("C") || mark.equals("RD");
        pending.amount = positive ? amount : amount.negate();
        String reference = matcher.group(6);
        int split = reference.indexOf("//");
        String bank = split >= 0 ? reference.substring(split + 2) : null;
        pending.bankReference = StatementAssembler.blankToNull(bank);
        if (field.lines().size() > 1) {
            pending.supplementary = StatementAssembler.blankToNull(String.join(" ", field.lines().subList(1, field.lines().size())));
        }
        block.transactions.add(pending);
        block.current = pending;
    }

    private static void details(Block block, Field field) {
        Pending pending = block.current;
        if (pending == null || pending.detailed) {
            return;
        }
        pending.detailed = true;
        String joined = String.join("", field.lines());
        Matcher structured = STRUCTURED.matcher(joined);
        if (!structured.find()) {
            pending.title = StatementAssembler.blankToNull(String.join(" ", field.lines()));
            return;
        }
        String separator = structured.group(1);
        Matcher subfields = Pattern.compile(Pattern.quote(separator) + "(\\d{2})").matcher(joined);
        List<int[]> bounds = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        while (subfields.find()) {
            codes.add(subfields.group(1));
            bounds.add(new int[]{subfields.start(), subfields.end()});
        }
        List<String> title = new ArrayList<>();
        List<String> name = new ArrayList<>();
        String typeText = null;
        String bankId = "";
        String accountNumber = "";
        String iban = null;
        for (int i = 0; i < codes.size(); i++) {
            int end = i + 1 < bounds.size() ? bounds.get(i + 1)[0] : joined.length();
            String content = joined.substring(bounds.get(i)[1], end).strip();
            int code = Integer.parseInt(codes.get(i));
            if (content.isEmpty()) {
                continue;
            }
            if ((code >= 20 && code <= 29) || (code >= 60 && code <= 63)) {
                title.add(content);
            } else if (code == 32 || code == 33) {
                name.add(content);
            } else if (code == 38) {
                iban = content;
            } else if (code == 30) {
                bankId = content;
            } else if (code == 31) {
                accountNumber = content;
            } else if (code == 0) {
                typeText = content;
            }
        }
        pending.title = StatementAssembler.blankToNull(title.isEmpty() ? typeText : String.join(" ", title));
        pending.counterpartyName = StatementAssembler.blankToNull(String.join(" ", name));
        String combined = bankId + accountNumber;
        String account = accountNumber;
        String resolved = Ibans.tryNormalise(iban)
            .or(() -> Ibans.tryNormalise(combined))
            .or(() -> Ibans.tryNormalise(account))
            .orElse(null);
        pending.counterpartyIban = resolved;
    }

    private static StatementPart toPart(Block block) {
        if (block.account == null) {
            throw new BankStatementParseException(MALFORMED, "The statement has no :25: account field");
        }
        String iban = null;
        String[] segments = block.account.split("/");
        for (int i = segments.length - 1; i >= 0 && iban == null; i--) {
            iban = Ibans.tryNormalise(segments[i]).orElse(null);
        }
        if (iban == null) {
            throw new BankStatementParseException(MALFORMED, "The statement has an invalid account number");
        }
        if (block.openingCurrency != null && block.closingCurrency != null
            && !block.openingCurrency.equals(block.closingCurrency)) {
            throw new BankStatementParseException(MIXED_CURRENCIES, "The statement balances use different currencies");
        }
        String currency = block.openingCurrency != null ? block.openingCurrency : block.closingCurrency;
        if (currency == null) {
            throw new BankStatementParseException(MALFORMED, "The statement has no balance fields to determine the currency");
        }
        List<TransactionDraft> drafts = new ArrayList<>();
        for (Pending pending : block.transactions) {
            String reference = pending.bankReference;
            String title = pending.title != null ? pending.title : pending.supplementary == null ? "" : pending.supplementary;
            drafts.add(new TransactionDraft(pending.booking, pending.value, pending.amount, currency,
                pending.counterpartyName, pending.counterpartyIban, title, reference, reference));
        }
        String id = block.reference == null ? block.sequence
            : block.sequence == null ? block.reference : block.reference + "/" + block.sequence;
        return new StatementPart(iban, currency, id, block.opening, block.closing, block.openingDate, block.closingDate, drafts);
    }
}
