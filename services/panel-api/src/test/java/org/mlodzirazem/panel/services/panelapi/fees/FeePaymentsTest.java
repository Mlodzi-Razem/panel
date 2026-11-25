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

import org.mlodzirazem.panel.services.panelapi.fees.*;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.*;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@PostgresTest
class FeePaymentsTest extends FeesTestSupport {
    private Payment pay(TMember member, String amount) {
        return payments.record(actor.getId(), new RecordPayment(member.getId(), money(amount), today(), PaymentSource.CASH, null, null));
    }

    @Test
    void paymentsFillTheOldestOpenDuesFirst() {
        setUpTierWithRate();
        TMember member = payingMember();

        pay(member, "70");

        assertThat(dueOf(member, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(member, NOW.minusMonths(5)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(member, NOW.minusMonths(4)).paid()).isEqualByComparingTo("10");
        assertThat(dueOf(member, NOW.minusMonths(4)).status()).isEqualTo(DueStatus.OPEN);
        assertThat(dueOf(member, NOW.minusMonths(3)).paid()).isEqualByComparingTo("0");

        MemberBalance balance = payments.balance(member.getId());
        assertThat(balance.owed()).isEqualByComparingTo("140");
        assertThat(balance.overdue()).isEqualByComparingTo("110");
        assertThat(balance.credit()).isEqualByComparingTo("0");
        assertThat(payments.openDues(member.getId())).hasSize(5);
    }

    @Test
    void surplusIsCreditAppliedToFutureDues() {
        setUpTierWithRate();
        TMember member = payingMember();

        Payment payment = pay(member, "500");

        assertThat(payment.allocated()).isEqualByComparingTo("210");
        assertThat(payments.balance(member.getId()).credit()).isEqualByComparingTo("290");
        assertThat(payments.balance(member.getId()).owed()).isEqualByComparingTo("0");

        dues.generateDues(NOW.plusMonths(2));

        assertThat(dueOf(member, NOW.plusMonths(2)).status()).isEqualTo(DueStatus.PAID);
        assertThat(payments.balance(member.getId()).credit()).isEqualByComparingTo("230");
    }

    @Test
    void creditBeforeAnyDuesIsUsedWhenDuesAppear() {
        actor = newMember("Tre", "Asurer", "mazowieckie");
        TMember member = unbilledMember();
        pay(member, "60");
        assertThat(payments.balance(member.getId()).credit()).isEqualByComparingTo("60");

        tierId = schedule.createTier(actor.getId(), new NewFeeTier("standard", "Standardowa", true)).id();
        schedule.addRate(actor.getId(), tierId, NOW.minusMonths(6), money("30"), "U/1");

        assertThat(dueOf(member, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(member, NOW.minusMonths(5)).status()).isEqualTo(DueStatus.PAID);
        assertThat(payments.balance(member.getId()).credit()).isEqualByComparingTo("0");
    }

    @Test
    void reversingReallocatesTheRemainingPayments() {
        setUpTierWithRate();
        TMember member = payingMember();
        Payment wrong = pay(member, "70");
        pay(member, "30");

        assertThat(dueOf(member, NOW.minusMonths(4)).status()).isEqualTo(DueStatus.PAID);

        Payment reversed = payments.reverse(actor.getId(), wrong.id(), "wrong match");

        assertThat(reversed.reversed()).isTrue();
        assertThat(dueOf(member, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(member, NOW.minusMonths(5)).status()).isEqualTo(DueStatus.OPEN);
        assertThat(payments.balance(member.getId()).owed()).isEqualByComparingTo("180");
        assertThat(payments.paymentsOf(member.getId())).hasSize(2);
        assertThat(codeOf(() -> payments.reverse(actor.getId(), wrong.id(), "again"))).isEqualTo("PAYMENT_ALREADY_REVERSED");
        assertThat(codeOf(() -> payments.reverse(actor.getId(), wrong.id() + 1000000, "x"))).isEqualTo("NOT_FOUND");
    }

    @Test
    void invalidPaymentsAreRejected() {
        setUpTierWithRate();
        TMember member = payingMember();

        assertThat(codeOf(() -> pay(member, "0"))).isEqualTo("PAYMENT_AMOUNT_INVALID");
        assertThat(codeOf(() -> payments.record(actor.getId(), new RecordPayment(member.getId(),
            new BigDecimal("1.001"), today(), PaymentSource.CASH, null, null)))).isEqualTo("PAYMENT_AMOUNT_INVALID");
        assertThat(codeOf(() -> payments.record(actor.getId(), new RecordPayment(member.getId(), money("5"),
            today().plusDays(3), PaymentSource.CASH, null, null)))).isEqualTo("PAYMENT_DATE_INVALID");
        assertThat(codeOf(() -> payments.record(actor.getId(), new RecordPayment(member.getId(), money("5"),
            today(), PaymentSource.CASH, 5L, null)))).isEqualTo("PAYMENT_SOURCE_INVALID");
        assertThat(codeOf(() -> payments.reverse(actor.getId(), pay(member, "5").id(), " ")))
            .isEqualTo("PAYMENT_REASON_REQUIRED");
    }

    @Test
    void aBankTransactionCanBeSplitAcrossMembers() {
        setUpTierWithRate();
        // V0.0.12.02 enforces fee_payments_bank_transaction_fk, so the shared transaction must exist.
        jdbc.update("INSERT INTO bank_statement_imports (id, file_name, file_sha256, format, account_iban, currency,"
            + " transactions_total, transactions_new, transactions_duplicate, transactions_matched,"
            + " transactions_unmatched, transactions_outgoing, imported_by_id)"
            + " VALUES (1, 'seed.sta',"
            + " '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',"
            + " 'MT940', 'PL61109010140000072219811111', 'PLN', 1, 1, 0, 0, 0, 0, ?)", actor.getId());
        jdbc.update("INSERT INTO bank_transactions (id, import_id, account_iban, stable_key, booking_date, amount,"
            + " currency, title, status)"
            + " VALUES (77, 1, 'PL61109010140000072219811111', 'stable-77', CURRENT_DATE, 60.00, 'PLN', 'title',"
            + " 'UNMATCHED')");
        TMember a = payingMember();
        TMember b = payingMember();

        Payment first = payments.record(actor.getId(), new RecordPayment(a.getId(), money("30"), today(), PaymentSource.BANK, 77L, "title"));
        Payment second = payments.record(actor.getId(), new RecordPayment(b.getId(), money("30"), today(), PaymentSource.BANK, 77L, null));

        assertThat(first.bankTransactionId()).isEqualTo(77L);
        assertThat(second.bankTransactionId()).isEqualTo(77L);
        assertThat(dueOf(a, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(b, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
    }

    @Test
    void historyHoldsDuesPaymentsAndAdjustments() {
        setUpTierWithRate();
        TMember member = payingMember();
        pay(member, "30");
        adjustments.grant(actor.getId(), new NewAdjustment(member.getId(), AdjustmentKind.DEFERRAL, NOW, null, null,
            AdjustmentReason.OTHER, "why", null));

        FeeHistory history = payments.history(member.getId());

        assertThat(history.dues()).hasSize(7);
        assertThat(history.payments()).hasSize(1);
        assertThat(history.adjustments()).hasSize(1);
    }

    @Test
    void paymentAuditsCarryIdsAndFieldNamesButNoAmounts() {
        setUpTierWithRate();
        TMember member = payingMember();

        Payment payment = pay(member, "123.45");
        payments.reverse(actor.getId(), payment.id(), "secret reversal reason");

        String audit = auditPayloads("fees :: payment-record") + auditPayloads("fees :: payment-reverse");
        assertThat(audit).contains("paymentId", "amount").doesNotContain("123.45").doesNotContain("12345")
                         .doesNotContain("secret reversal reason");
        assertThat(auditCount("fees :: payment-record")).isEqualTo(1);
        assertThat(auditCount("fees :: payment-reverse")).isEqualTo(1);
    }
}
