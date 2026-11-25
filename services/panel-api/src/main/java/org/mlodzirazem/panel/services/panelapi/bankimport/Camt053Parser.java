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
import static org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException.Code.UNKNOWN_FORMAT;
import static org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException.Code.UNSUPPORTED_VERSION;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.jspecify.annotations.Nullable;

final class Camt053Parser {
    private static final Pattern VERSION = Pattern.compile("camt\\.053\\.001\\.(\\d+)");
    private static final int MIN_VERSION = 2;
    private static final int MAX_VERSION = 13;
    private static final XMLInputFactory FACTORY = createFactory();

    private Camt053Parser() {
    }

    static StatementFile parse(byte[] data) {
        try {
            XMLStreamReader reader = FACTORY.createXMLStreamReader(new ByteArrayInputStream(data));
            try {
                return new Run(reader).run();
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            int line = e.getLocation() == null ? -1 : e.getLocation().getLineNumber();
            throw new BankStatementParseException(MALFORMED, "The XML is not well-formed near line " + line);
        }
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        trySet(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        trySet(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static void trySet(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static final class TxState {
        @Nullable String acctSvcrRef;
        @Nullable String endToEndId;
        @Nullable String txAmount;
        @Nullable String txCurrency;
        @Nullable String debtorName;
        @Nullable String debtorIban;
        @Nullable String creditorName;
        @Nullable String creditorIban;
        final List<String> unstructured = new ArrayList<>();
        final List<String> structuredRefs = new ArrayList<>();
        @Nullable String additionalTxInfo;
    }

    private static final class EntryState {
        @Nullable String amount;
        @Nullable String currency;
        @Nullable String direction;
        @Nullable String bookingDate;
        @Nullable String valueDate;
        @Nullable String acctSvcrRef;
        @Nullable String additionalInfo;
        @Nullable String status;
        final List<TxState> txs = new ArrayList<>();
    }

    private static final class BalState {
        @Nullable String type;
        @Nullable String amount;
        @Nullable String currency;
        @Nullable String direction;
        @Nullable String date;
    }

    private static final class StmtState {
        @Nullable String id;
        @Nullable String iban;
        @Nullable String otherAccount;
        @Nullable String currency;
        @Nullable String from;
        @Nullable String to;
        @Nullable BigDecimal opening;
        @Nullable String openingCurrency;
        @Nullable LocalDate openingDate;
        @Nullable BigDecimal closing;
        @Nullable String closingCurrency;
        @Nullable LocalDate closingDate;
        boolean openingFinal;
        final List<TransactionDraft> drafts = new ArrayList<>();
    }

    private static final class Run {
        private final XMLStreamReader reader;
        private final List<String> path = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private final List<StatementPart> parts = new ArrayList<>();
        private int stmtIdx = -1;
        private int ntryIdx = -1;
        private int txIdx = -1;
        private int balIdx = -1;
        private @Nullable String amtCurrency;
        private @Nullable StmtState stmt;
        private @Nullable EntryState entry;
        private @Nullable TxState tx;
        private @Nullable BalState bal;
        private int entryCount;

        Run(XMLStreamReader reader) {
            this.reader = reader;
        }

        StatementFile run() throws XMLStreamException {
            boolean rootSeen = false;
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.DTD ->
                        throw new BankStatementParseException(MALFORMED, "Document type declarations are not allowed");
                    case XMLStreamConstants.START_ELEMENT -> {
                        rootSeen = true;
                        start();
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text.append(reader.getText());
                    case XMLStreamConstants.END_ELEMENT -> end();
                    default -> {
                    }
                }
            }
            if (!rootSeen) {
                throw new BankStatementParseException(UNKNOWN_FORMAT, "The file is not an XML document");
            }
            return StatementAssembler.assemble(BankStatementFormat.CAMT_053, parts);
        }

        private void start() {
            String name = reader.getLocalName();
            path.add(name);
            text.setLength(0);
            int depth = path.size();
            if (depth == 1) {
                checkRoot(name);
                return;
            }
            if (depth == 2 && !"BkToCstmrStmt".equals(name)) {
                throw new BankStatementParseException(UNKNOWN_FORMAT, "The XML document is not a camt.053 statement");
            }
            switch (name) {
                case "Stmt" -> {
                    if (stmtIdx < 0 && depth == 3) {
                        stmt = new StmtState();
                        stmtIdx = depth - 1;
                    }
                }
                case "Ntry" -> {
                    if (stmtIdx >= 0 && ntryIdx < 0 && depth == stmtIdx + 2) {
                        entry = new EntryState();
                        ntryIdx = depth - 1;
                        entryCount++;
                    }
                }
                case "TxDtls" -> {
                    if (ntryIdx >= 0 && txIdx < 0 && depth == ntryIdx + 3) {
                        tx = new TxState();
                        txIdx = depth - 1;
                    }
                }
                case "Bal" -> {
                    if (stmtIdx >= 0 && ntryIdx < 0 && balIdx < 0 && depth == stmtIdx + 2) {
                        bal = new BalState();
                        balIdx = depth - 1;
                    }
                }
                case "Amt" -> amtCurrency = reader.getAttributeValue(null, "Ccy");
                default -> {
                }
            }
        }

        private void checkRoot(String name) {
            String namespace = reader.getNamespaceURI();
            if (!"Document".equals(name)) {
                throw new BankStatementParseException(UNKNOWN_FORMAT, "The XML document is not an ISO 20022 message");
            }
            if (namespace == null || !namespace.contains("camt.")) {
                return;
            }
            Matcher matcher = VERSION.matcher(namespace);
            if (!matcher.find()) {
                throw new BankStatementParseException(UNKNOWN_FORMAT, "The XML document is not a camt.053 statement");
            }
            int version = Integer.parseInt(matcher.group(1));
            if (version < MIN_VERSION || version > MAX_VERSION) {
                throw new BankStatementParseException(UNSUPPORTED_VERSION, "Unsupported camt.053 version " + version);
            }
        }

        private void end() {
            String value = text.toString().strip();
            text.setLength(0);
            int depth = path.size();
            if (!value.isEmpty() && stmtIdx >= 0) {
                leaf(value);
            }
            int index = depth - 1;
            if (index == txIdx) {
                entry.txs.add(tx);
                tx = null;
                txIdx = -1;
            } else if (index == ntryIdx) {
                finishEntry();
                entry = null;
                ntryIdx = -1;
            } else if (index == balIdx) {
                finishBalance();
                bal = null;
                balIdx = -1;
            } else if (index == stmtIdx) {
                finishStatement();
                stmt = null;
                stmtIdx = -1;
            }
            path.remove(index);
        }

        private String relative(int from) {
            StringBuilder builder = new StringBuilder();
            for (int i = from + 1; i < path.size(); i++) {
                String segment = path.get(i);
                if ("Pty".equals(segment)) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append('/');
                }
                builder.append(segment);
            }
            return builder.toString();
        }

        private void leaf(String value) {
            if (txIdx >= 0) {
                txLeaf(relative(txIdx), value);
            } else if (ntryIdx >= 0) {
                entryLeaf(relative(ntryIdx), value);
            } else if (balIdx >= 0) {
                balanceLeaf(relative(balIdx), value);
            } else {
                statementLeaf(relative(stmtIdx), value);
            }
        }

        private void statementLeaf(String rel, String value) {
            switch (rel) {
                case "Id" -> stmt.id = value;
                case "Acct/Id/IBAN" -> stmt.iban = value;
                case "Acct/Id/Othr/Id" -> stmt.otherAccount = value;
                case "Acct/Ccy" -> stmt.currency = value;
                case "FrToDt/FrDtTm" -> stmt.from = value;
                case "FrToDt/ToDtTm" -> stmt.to = value;
                default -> {
                }
            }
        }

        private void balanceLeaf(String rel, String value) {
            switch (rel) {
                case "Tp/CdOrPrtry/Cd" -> bal.type = value;
                case "Amt" -> {
                    bal.amount = value;
                    bal.currency = amtCurrency;
                }
                case "CdtDbtInd" -> bal.direction = value;
                case "Dt/Dt", "Dt/DtTm" -> bal.date = value;
                default -> {
                }
            }
        }

        private void entryLeaf(String rel, String value) {
            switch (rel) {
                case "Amt" -> {
                    entry.amount = value;
                    entry.currency = amtCurrency;
                }
                case "CdtDbtInd" -> entry.direction = value;
                case "BookgDt/Dt", "BookgDt/DtTm" -> entry.bookingDate = value;
                case "ValDt/Dt", "ValDt/DtTm" -> entry.valueDate = value;
                case "AcctSvcrRef" -> entry.acctSvcrRef = value;
                case "AddtlNtryInf" -> entry.additionalInfo = value;
                case "Sts", "Sts/Cd" -> entry.status = value;
                default -> {
                }
            }
        }

        private void txLeaf(String rel, String value) {
            switch (rel) {
                case "Refs/AcctSvcrRef" -> tx.acctSvcrRef = value;
                case "Refs/EndToEndId" -> tx.endToEndId = value;
                case "AmtDtls/TxAmt/Amt" -> {
                    tx.txAmount = value;
                    tx.txCurrency = amtCurrency;
                }
                case "RltdPties/Dbtr/Nm" -> tx.debtorName = value;
                case "RltdPties/DbtrAcct/Id/IBAN" -> tx.debtorIban = value;
                case "RltdPties/Cdtr/Nm" -> tx.creditorName = value;
                case "RltdPties/CdtrAcct/Id/IBAN" -> tx.creditorIban = value;
                case "RmtInf/Ustrd" -> tx.unstructured.add(value);
                case "RmtInf/Strd/CdtrRefInf/Ref" -> tx.structuredRefs.add(value);
                case "AddtlTxInf" -> tx.additionalTxInfo = value;
                default -> {
                }
            }
        }

        private BankStatementParseException malformed(String what) {
            return new BankStatementParseException(MALFORMED, what + " (entry " + entryCount + ")");
        }

        private LocalDate date(String value, String what) {
            try {
                return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
            } catch (DateTimeParseException e) {
                throw new BankStatementParseException(MALFORMED, "Invalid " + what + " date in statement " + (parts.size() + 1));
            }
        }

        private BigDecimal decimal(String value, String what) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                throw malformed("Invalid " + what);
            }
        }

        private boolean credit(@Nullable String direction) {
            if ("CRDT".equals(direction)) {
                return true;
            }
            if ("DBIT".equals(direction)) {
                return false;
            }
            throw malformed("Missing or invalid credit/debit indicator");
        }

        private void finishBalance() {
            if (bal.type == null || bal.amount == null || bal.direction == null) {
                return;
            }
            boolean opening = "OPBD".equals(bal.type) || "PRCD".equals(bal.type);
            boolean closing = "CLBD".equals(bal.type);
            if (!opening && !closing) {
                return;
            }
            BigDecimal amount = decimal(bal.amount, "balance amount").abs();
            if (!credit(bal.direction)) {
                amount = amount.negate();
            }
            LocalDate date = bal.date == null ? null : date(bal.date, "balance");
            if (opening) {
                boolean preferred = "OPBD".equals(bal.type);
                if (stmt.opening == null || (preferred && !stmt.openingFinal)) {
                    stmt.opening = amount;
                    stmt.openingCurrency = bal.currency;
                    stmt.openingDate = date;
                    stmt.openingFinal = preferred;
                }
            } else {
                stmt.closing = amount;
                stmt.closingCurrency = bal.currency;
                stmt.closingDate = date;
            }
        }

        private void finishStatement() {
            String account = stmt.iban != null ? stmt.iban : stmt.otherAccount;
            if (account == null) {
                throw new BankStatementParseException(MALFORMED, "Statement " + (parts.size() + 1) + " has no account");
            }
            String iban = Ibans.tryNormalise(account).orElseThrow(() ->
                new BankStatementParseException(MALFORMED, "Statement " + (parts.size() + 1) + " has an invalid account IBAN"));
            String currency = stmt.currency != null ? stmt.currency : stmt.openingCurrency;
            if (currency == null) {
                currency = stmt.closingCurrency;
            }
            if (stmt.openingCurrency != null && stmt.closingCurrency != null
                && !stmt.openingCurrency.equals(stmt.closingCurrency)) {
                throw new BankStatementParseException(MIXED_CURRENCIES, "The statement balances use different currencies");
            }
            LocalDate from = stmt.from != null ? date(stmt.from, "period start") : stmt.openingDate;
            LocalDate to = stmt.to != null ? date(stmt.to, "period end") : stmt.closingDate;
            parts.add(new StatementPart(iban, currency, stmt.id, stmt.opening, stmt.closing, from, to, stmt.drafts));
        }

        private void finishEntry() {
            if ("PDNG".equals(entry.status) || "INFO".equals(entry.status)) {
                return;
            }
            if (entry.amount == null || entry.currency == null) {
                throw malformed("Missing amount");
            }
            boolean credit = credit(entry.direction);
            BigDecimal entryAmount = signed(decimal(entry.amount, "amount"), credit);
            String currency = entry.currency;
            String bookingText = entry.bookingDate != null ? entry.bookingDate : entry.valueDate;
            if (bookingText == null) {
                throw malformed("Missing booking date");
            }
            LocalDate booking = date(bookingText, "booking");
            LocalDate value = entry.valueDate == null ? null : date(entry.valueDate, "value");
            String entryRef = StatementAssembler.blankToNull(entry.acctSvcrRef);

            List<TxState> txs = entry.txs;
            if (txs.size() > 1 && splittable(txs, currency, entryAmount, credit)) {
                for (int i = 0; i < txs.size(); i++) {
                    TxState state = txs.get(i);
                    BigDecimal amount = signed(decimal(state.txAmount, "amount"), credit);
                    String own = StatementAssembler.blankToNull(state.acctSvcrRef);
                    String reference = own != null ? own : entryRef;
                    String keyReference = own != null ? own : entryRef == null ? null : entryRef + "#tx" + (i + 1);
                    stmt.drafts.add(draft(booking, value, amount, currency, state, credit, reference, keyReference));
                }
                return;
            }
            if (txs.size() == 1) {
                TxState state = txs.getFirst();
                String own = StatementAssembler.blankToNull(state.acctSvcrRef);
                String reference = own != null ? own : entryRef;
                stmt.drafts.add(draft(booking, value, entryAmount, currency, state, credit, reference, reference));
                return;
            }
            List<String> titles = new ArrayList<>();
            for (TxState state : txs) {
                String title = title(state, null);
                if (!title.isEmpty()) {
                    titles.add(title);
                }
            }
            String title = titles.isEmpty() ? StatementAssembler.blankToNull(entry.additionalInfo) : String.join(" ", titles);
            stmt.drafts.add(new TransactionDraft(booking, value, entryAmount, currency, null, null,
                title == null ? "" : title, entryRef, entryRef));
        }

        private boolean splittable(List<TxState> txs, String currency, BigDecimal entryAmount, boolean credit) {
            BigDecimal sum = BigDecimal.ZERO;
            for (TxState state : txs) {
                if (state.txAmount == null || !currency.equals(state.txCurrency)) {
                    return false;
                }
                sum = sum.add(signed(decimal(state.txAmount, "amount"), credit));
            }
            return sum.compareTo(entryAmount) == 0;
        }

        private static BigDecimal signed(BigDecimal amount, boolean credit) {
            return credit ? amount.abs() : amount.abs().negate();
        }

        private TransactionDraft draft(LocalDate booking, @Nullable LocalDate value, BigDecimal amount, String currency,
                                       TxState state, boolean credit, @Nullable String reference,
                                       @Nullable String keyReference) {
            String name = StatementAssembler.blankToNull(credit ? state.debtorName : state.creditorName);
            String iban = Ibans.tryNormalise(credit ? state.debtorIban : state.creditorIban).orElse(null);
            String effectiveReference = reference;
            String effectiveKey = keyReference;
            if (effectiveReference == null) {
                String endToEnd = StatementAssembler.blankToNull(state.endToEndId);
                if (endToEnd != null && !"NOTPROVIDED".equalsIgnoreCase(endToEnd)) {
                    effectiveReference = endToEnd;
                    effectiveKey = endToEnd;
                }
            }
            return new TransactionDraft(booking, value, amount, currency, name, iban,
                title(state, entry.additionalInfo), effectiveReference, effectiveKey);
        }

        private String title(TxState state, @Nullable String fallback) {
            List<String> pieces = new ArrayList<>();
            for (String part : state.unstructured) {
                String cleaned = StatementAssembler.blankToNull(part);
                if (cleaned != null) {
                    pieces.add(cleaned);
                }
            }
            for (String part : state.structuredRefs) {
                String cleaned = StatementAssembler.blankToNull(part);
                if (cleaned != null) {
                    pieces.add(cleaned);
                }
            }
            if (pieces.isEmpty()) {
                String additional = StatementAssembler.blankToNull(state.additionalTxInfo);
                if (additional == null) {
                    additional = StatementAssembler.blankToNull(fallback);
                }
                return additional == null ? "" : additional;
            }
            return String.join(" ", pieces);
        }
    }
}
