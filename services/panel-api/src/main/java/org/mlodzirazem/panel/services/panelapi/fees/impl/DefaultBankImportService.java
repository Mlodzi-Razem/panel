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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParser;
import org.mlodzirazem.panel.services.panelapi.bankimport.BankTransaction;
import org.mlodzirazem.panel.services.panelapi.bankimport.StatementFile;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.fees.BankImportService;
import org.mlodzirazem.panel.services.panelapi.fees.BankTransactionView;
import org.mlodzirazem.panel.services.panelapi.fees.ImportOutcome;
import org.mlodzirazem.panel.services.panelapi.fees.PagedResult;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentReferences;
import org.mlodzirazem.panel.services.panelapi.fees.PaymentService;
import org.mlodzirazem.panel.services.panelapi.fees.RecordPayment;
import org.mlodzirazem.panel.services.panelapi.fees.SenderIbans;
import org.mlodzirazem.panel.services.panelapi.fees.Split;
import org.mlodzirazem.panel.services.panelapi.fees.StatementImportView;
import org.mlodzirazem.panel.services.panelapi.fees.StatementReport;
import org.mlodzirazem.panel.services.panelapi.fees.StatementTotals;
import org.mlodzirazem.panel.services.panelapi.fees.TransactionOutcome;
import org.mlodzirazem.panel.services.panelapi.fees.TransactionPayment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.BankMatchMethod;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.BankTransactionStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.PaymentSource;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TBankStatementImport;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TBankStatementImportRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TBankTransaction;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TBankTransactionRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePayment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeePaymentRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DefaultBankImportService implements BankImportService {
    private static final String PLN = "PLN";

    private final TBankStatementImportRepository imports;
    private final TBankTransactionRepository transactions;
    private final TFeePaymentRepository paymentEntities;
    private final TMemberRepository members;
    private final PaymentService payments;
    private final PaymentReferences references;
    private final SenderIbans senderIbans;
    private final FeeLock lock;
    private final AuditLog auditLog;
    private final Clock clock;

    private record Analysed(
        StatementFile file, String sha256, String fileName, boolean alreadyImported,
        List<Row> rows
    ) {
    }

    private record Row(BankTransaction transaction, ImportOutcome outcome, @Nullable Long memberId) {
    }

    @Override
    @Transactional(readOnly = true)
    public StatementReport preview(long actorMemberId, InputStream statement, String fileName) {
        return report(null, analyse(statement, fileName));
    }

    @Override
    @Transactional
    public StatementReport apply(long actorMemberId, InputStream statement, String fileName) {
        lock.acquire();

        Analysed analysed = analyse(statement, fileName);

        if (analysed.alreadyImported()) {
            throw new ConflictException("STATEMENT_ALREADY_IMPORTED", "This statement file was already imported");
        }

        StatementReport draft = report(null, analysed);
        StatementTotals totals = draft.totals();
        StatementFile file = analysed.file();

        TBankStatementImport entity = new TBankStatementImport();
        entity.setFileName(fileName(analysed.fileName()));
        entity.setFileSha256(analysed.sha256());
        entity.setFormat(file.format().name());
        entity.setAccountIban(file.accountIban());
        entity.setCurrency(file.currency());
        entity.setStatementId(file.statementId());
        entity.setPeriodFrom(file.from());
        entity.setPeriodTo(file.to());
        entity.setTransactionsTotal(totals.transactions());
        entity.setTransactionsNew(totals.newTransactions());
        entity.setTransactionsDuplicate(totals.duplicates());
        entity.setTransactionsMatched(totals.matched());
        entity.setTransactionsUnmatched(totals.unmatched() + totals.foreignCurrency());
        entity.setTransactionsOutgoing(totals.outgoing());
        entity.setImportedBy(members.getReferenceById(actorMemberId));
        entity.setCreatedBy(members.getReferenceById(actorMemberId));
        imports.saveAndFlush(entity);

        LocalDate today = LocalDate.now(clock);
        ZonedDateTime now = ZonedDateTime.now(clock);

        for (Row row : analysed.rows()) {
            if (row.outcome() == ImportOutcome.DUPLICATE) {
                continue;
            }

            BankTransaction t = row.transaction();
            TBankTransaction tx = new TBankTransaction();
            tx.setStatementImport(entity);
            tx.setAccountIban(file.accountIban());
            tx.setStableKey(t.stableKey());
            tx.setBookingDate(t.bookingDate());
            tx.setValueDate(t.valueDate());
            tx.setAmount(t.amount().setScale(2, java.math.RoundingMode.HALF_UP));
            tx.setCurrency(t.currency());
            tx.setCounterpartyName(t.counterpartyName());
            tx.setCounterpartyIban(t.counterpartyIban());
            tx.setTitle(t.title() == null ? "" : t.title());
            tx.setBankReference(t.bankReference());
            tx.setCreatedBy(members.getReferenceById(actorMemberId));

            switch (row.outcome()) {
                case NEW_MATCHED_REFERENCE -> {
                    tx.setStatus(BankTransactionStatus.MATCHED);
                    tx.setMatchMethod(BankMatchMethod.REFERENCE);
                }
                case NEW_MATCHED_IBAN -> {
                    tx.setStatus(BankTransactionStatus.MATCHED);
                    tx.setMatchMethod(BankMatchMethod.IBAN);
                }
                case OUTGOING -> tx.setStatus(BankTransactionStatus.OUTGOING);
                default -> tx.setStatus(BankTransactionStatus.UNMATCHED);
            }

            if (tx.getStatus() == BankTransactionStatus.MATCHED) {
                tx.setDecidedAt(now);
            }

            transactions.saveAndFlush(tx);

            if (tx.getStatus() == BankTransactionStatus.MATCHED) {
                LocalDate paidOn = t.bookingDate().isAfter(today) ? today : t.bookingDate();
                payments.record(actorMemberId, new RecordPayment(row.memberId(), tx.getAmount(), paidOn,
                    PaymentSource.BANK, tx.getId(), null));
            }
        }

        auditLog.record("fees :: statement-import", new AuditPayload(java.util.Map.of(
            "importId", entity.getId(),
            "transactions", (long) totals.transactions(),
            "imported", (long) totals.newTransactions(),
            "duplicates", (long) totals.duplicates(),
            "matched", (long) totals.matched(),
            "unmatched", (long) (totals.unmatched() + totals.foreignCurrency()),
            "outgoing", (long) totals.outgoing()), List.of(), null));

        return report(entity.getId(), analysed);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<StatementImportView> imports(int page, int size) {
        Page<TBankStatementImport> result = imports.findAllByOrderByIdDesc(PageRequest.of(page, size));

        return new PagedResult<>(result.getContent().stream().map(DefaultBankImportService::view).toList(),
            result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<BankTransactionView> transactions(BankTransactionStatus status, int page, int size) {
        Page<TBankTransaction> result = transactions.findByStatusOrderByBookingDateDescIdDesc(status,
            PageRequest.of(page, size));

        return new PagedResult<>(views(result.getContent()), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public BankTransactionView transaction(long transactionId) {
        return views(List.of(find(transactionId))).getFirst();
    }

    @Override
    @Transactional
    public BankTransactionView assign(long actorMemberId, long transactionId, List<Split> splits, boolean rememberIban) {
        lock.acquire();

        TBankTransaction tx = find(transactionId);

        if (tx.getStatus() != BankTransactionStatus.UNMATCHED) {
            throw new ConflictException("TRANSACTION_NOT_UNMATCHED", "The transaction is not waiting for matching");
        }

        if (tx.getAmount().signum() <= 0 || !PLN.equals(tx.getCurrency())) {
            throw new RuleViolationException("TRANSACTION_CURRENCY_UNSUPPORTED",
                "Only PLN credits can be matched to fee payments");
        }

        if (splits == null || splits.isEmpty()) {
            throw new RuleViolationException("SPLITS_REQUIRED", "At least one split is required");
        }

        Set<Long> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;

        for (Split split : splits) {
            BigDecimal amount = split.amount();

            if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new RuleViolationException("SPLIT_AMOUNT_INVALID",
                    "Split amounts must be positive with at most two decimal places");
            }

            if (!seen.add(split.memberId())) {
                throw new RuleViolationException("SPLIT_DUPLICATE_MEMBER", "A member may appear in one split only");
            }

            sum = sum.add(amount);
        }

        if (sum.compareTo(tx.getAmount()) != 0) {
            throw new RuleViolationException("SPLIT_SUM_MISMATCH", "The splits must add up to the transaction amount");
        }

        OwnFeesGuard.reject(actorMemberId, seen);

        LocalDate today = LocalDate.now(clock);
        LocalDate paidOn = tx.getBookingDate().isAfter(today) ? today : tx.getBookingDate();

        for (Split split : splits) {
            payments.record(actorMemberId, new RecordPayment(split.memberId(), split.amount().setScale(2),
                paidOn, PaymentSource.BANK, tx.getId(), null));
        }

        if (rememberIban && splits.size() == 1 && tx.getCounterpartyIban() != null) {
            senderIbans.confirm(actorMemberId, tx.getCounterpartyIban(), splits.getFirst().memberId());
        }

        tx.setStatus(BankTransactionStatus.MATCHED);
        tx.setMatchMethod(BankMatchMethod.MANUAL);
        tx.setDecidedBy(members.getReferenceById(actorMemberId));
        tx.setDecidedAt(ZonedDateTime.now(clock));
        transactions.saveAndFlush(tx);

        auditLog.record("fees :: transaction-assign", AuditPayload.of("bankTransactionId", tx.getId())
                                                                  .withId("splits", splits.size()));

        return views(List.of(tx)).getFirst();
    }

    @Override
    @Transactional
    public BankTransactionView ignore(long actorMemberId, long transactionId, String note) {
        lock.acquire();

        TBankTransaction tx = find(transactionId);

        if (tx.getStatus() != BankTransactionStatus.UNMATCHED) {
            throw new ConflictException("TRANSACTION_NOT_UNMATCHED", "The transaction is not waiting for matching");
        }

        tx.setStatus(BankTransactionStatus.IGNORED);
        tx.setIgnoredNote(note == null || note.isBlank() ? null : note.strip());
        tx.setDecidedBy(members.getReferenceById(actorMemberId));
        tx.setDecidedAt(ZonedDateTime.now(clock));
        transactions.saveAndFlush(tx);

        auditLog.record("fees :: transaction-ignore", AuditPayload.of("bankTransactionId", tx.getId()));

        return views(List.of(tx)).getFirst();
    }

    @Override
    @Transactional
    public BankTransactionView reopen(long actorMemberId, long transactionId, String reason) {
        lock.acquire();

        TBankTransaction tx = find(transactionId);

        if (tx.getStatus() != BankTransactionStatus.MATCHED && tx.getStatus() != BankTransactionStatus.IGNORED) {
            throw new ConflictException("TRANSACTION_NOT_REOPENABLE", "The transaction cannot be reopened");
        }

        if (tx.getStatus() == BankTransactionStatus.MATCHED) {
            for (TFeePayment payment : paymentEntities.findByBankTransactionIdInOrderByIdAsc(List.of(tx.getId()))) {
                if (payment.getReversedAt() == null) {
                    payments.reverse(actorMemberId, payment.getId(), reason);
                }
            }
        }

        tx.setStatus(BankTransactionStatus.UNMATCHED);
        tx.setMatchMethod(null);
        tx.setIgnoredNote(null);
        tx.setDecidedBy(null);
        tx.setDecidedAt(null);
        transactions.saveAndFlush(tx);

        auditLog.record("fees :: transaction-reopen", AuditPayload.of("bankTransactionId", tx.getId()));

        return views(List.of(tx)).getFirst();
    }

    private Analysed analyse(InputStream statement, String fileName) {
        byte[] bytes = read(statement);
        String sha = sha256(bytes);
        StatementFile file = new BankStatementParser().parse(new ByteArrayInputStream(bytes), fileName);
        boolean already = imports.existsByAccountIbanAndFileSha256(file.accountIban(), sha);

        Set<String> existing = new HashSet<>();

        if (!file.transactions().isEmpty()) {
            existing.addAll(transactions.findExistingKeys(file.accountIban(),
                file.transactions().stream().map(BankTransaction::stableKey).collect(Collectors.toList())));
        }

        List<Row> rows = new ArrayList<>();

        for (BankTransaction t : file.transactions()) {
            if (existing.contains(t.stableKey())) {
                rows.add(new Row(t, ImportOutcome.DUPLICATE, null));
            } else if (t.amount().signum() <= 0) {
                rows.add(new Row(t, ImportOutcome.OUTGOING, null));
            } else if (!PLN.equals(t.currency())) {
                rows.add(new Row(t, ImportOutcome.FOREIGN_CURRENCY, null));
            } else {
                rows.add(match(t));
            }
        }

        return new Analysed(file, sha, fileName, already, rows);
    }

    private Row match(BankTransaction t) {
        Optional<Long> byReference = references.parse(t.title() == null ? "" : t.title());

        if (byReference.isPresent()) {
            return new Row(t, ImportOutcome.NEW_MATCHED_REFERENCE, byReference.get());
        }

        if (t.counterpartyIban() != null) {
            Optional<Long> byIban = senderIbans.memberOf(t.counterpartyIban());

            // a title naming somebody else (paying for another member, several numbers) is the treasurer's call
            if (byIban.isPresent()
                && references.membersNamed(t.title() == null ? "" : t.title())
                             .stream()
                             .allMatch(byIban.get()::equals)) {
                return new Row(t, ImportOutcome.NEW_MATCHED_IBAN, byIban.get());
            }
        }

        return new Row(t, ImportOutcome.NEW_UNMATCHED, null);
    }

    private static StatementReport report(@Nullable Long importId, Analysed a) {
        int duplicates = 0;
        int byReference = 0;
        int byIban = 0;
        int unmatched = 0;
        int foreign = 0;
        int outgoing = 0;
        BigDecimal matchedAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal unmatchedAmount = BigDecimal.ZERO.setScale(2);
        List<TransactionOutcome> outcomes = new ArrayList<>();

        for (Row row : a.rows()) {
            BankTransaction t = row.transaction();
            BigDecimal amount = t.amount().setScale(2, java.math.RoundingMode.HALF_UP);

            switch (row.outcome()) {
                case DUPLICATE -> duplicates++;
                case NEW_MATCHED_REFERENCE -> {
                    byReference++;
                    matchedAmount = matchedAmount.add(amount);
                }
                case NEW_MATCHED_IBAN -> {
                    byIban++;
                    matchedAmount = matchedAmount.add(amount);
                }
                case NEW_UNMATCHED -> {
                    unmatched++;
                    unmatchedAmount = unmatchedAmount.add(amount);
                }
                case FOREIGN_CURRENCY -> foreign++;
                case OUTGOING -> outgoing++;
            }

            outcomes.add(new TransactionOutcome(t.bookingDate(), amount, t.currency(), t.counterpartyName(),
                t.counterpartyIban(), t.title() == null ? "" : t.title(), row.outcome(), row.memberId()));
        }

        int total = a.rows().size();
        StatementFile f = a.file();

        return new StatementReport(importId, fileName(a.fileName()), f.format().name(), f.accountIban(), f.currency(),
            f.statementId(), f.from(), f.to(), a.alreadyImported(),
            new StatementTotals(total, total - duplicates, duplicates, byReference, byIban, unmatched, foreign,
                outgoing, matchedAmount, unmatchedAmount),
            outcomes);
    }

    private TBankTransaction find(long id) {
        return transactions.findById(id).orElseThrow(() -> new NotFoundException("Bank transaction", id));
    }

    private List<BankTransactionView> views(List<TBankTransaction> list) {
        if (list.isEmpty()) {
            return List.of();
        }

        List<TFeePayment> all = paymentEntities.findByBankTransactionIdInOrderByIdAsc(
            list.stream().map(TBankTransaction::getId).toList());

        return list.stream().map(t -> new BankTransactionView(
            t.getId(), t.getStatementImport().getId(), t.getAccountIban(), t.getBookingDate(), t.getValueDate(),
            t.getAmount(), t.getCurrency(), t.getCounterpartyName(), t.getCounterpartyIban(), t.getTitle(),
            t.getBankReference(), t.getStatus(), t.getMatchMethod(), t.getIgnoredNote(),
            t.getDecidedBy() == null ? null : t.getDecidedBy().getId(), t.getDecidedAt(),
            all.stream()
               .filter(p -> t.getId().equals(p.getBankTransactionId()))
               .map(p -> new TransactionPayment(p.getId(), p.getMember().getId(), p.getAmount(),
                   p.getReversedAt() != null))
               .toList())).toList();
    }

    private static StatementImportView view(TBankStatementImport i) {
        return new StatementImportView(i.getId(), i.getFileName(), i.getFormat(), i.getAccountIban(), i.getCurrency(),
            i.getStatementId(), i.getPeriodFrom(), i.getPeriodTo(), i.getTransactionsTotal(), i.getTransactionsNew(),
            i.getTransactionsDuplicate(), i.getTransactionsMatched(), i.getTransactionsUnmatched(),
            i.getTransactionsOutgoing(), i.getImportedBy().getId(), i.getCreatedAt());
    }

    private static String fileName(@Nullable String name) {
        String value = name == null || name.isBlank() ? "statement" : name.strip();
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static byte[] read(InputStream in) {
        try {
            byte[] bytes = in.readNBytes((int) MAX_FILE_BYTES + 1);

            if (bytes.length > MAX_FILE_BYTES) {
                throw new RuleViolationException("FILE_TOO_LARGE", "The statement file exceeds 10 MB");
            }

            if (bytes.length == 0) {
                throw new RuleViolationException("FILE_EMPTY", "The statement file is empty");
            }

            return bytes;
        } catch (IOException e) {
            throw new RuleViolationException("FILE_UNREADABLE", "The statement file could not be read");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
