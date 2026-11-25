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
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SpringBootTest
@PostgresTest
class FeeDuesTest extends FeesTestSupport {
    @Test
    void ordinaryMembersOwePerMonthFromTheStartMonth() {
        setUpTierWithRate();
        TMember member = payingMember();

        List<Due> all = allDues(member);

        assertThat(all).hasSize(7);
        assertThat(all.getFirst().month()).isEqualTo(NOW.minusMonths(6));
        assertThat(all.getLast().month()).isEqualTo(NOW);
        assertThat(all).allSatisfy(d -> {
            assertThat(d.amount()).isEqualByComparingTo("30");
            assertThat(d.status()).isEqualTo(DueStatus.OPEN);
        });
    }

    @Test
    void firstDueIsTheMonthAfterMemberSinceUnlessItIsTheFirstDay() {
        setUpTierWithRate();
        TMember midMonth = newMember("Mid", "Month", "mazowieckie");
        jdbc.update("UPDATE members SET member_since = ? WHERE id = ?", NOW.minusMonths(3).atDay(15), midMonth.getId());
        TMember firstDay = ordinaryMember("First", "Day", NOW.minusMonths(3));
        TMember noSince = newMember("No", "Since", "mazowieckie");

        dues.generateDues(NOW);

        assertThat(allDues(midMonth).getFirst().month()).isEqualTo(NOW.minusMonths(2));
        assertThat(allDues(firstDay).getFirst().month()).isEqualTo(NOW.minusMonths(3));
        assertThat(allDues(noSince)).isEmpty();
    }

    @Test
    void onlyOrdinaryActiveOrSuspendedMembersOwe() {
        setUpTierWithRate();
        TMember supporting = unbilledMember();
        TMember honorary = unbilledMember();
        TMember suspended = unbilledMember();
        TMember probation = unbilledMember();
        jdbc.update("UPDATE members SET membership_type = 'SUPPORTING' WHERE id = ?", supporting.getId());
        jdbc.update("UPDATE members SET membership_type = 'HONORARY' WHERE id = ?", honorary.getId());
        jdbc.update("UPDATE members SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        jdbc.update("UPDATE members SET status = 'PROBATION' WHERE id = ?", probation.getId());

        dues.generateDues(NOW);

        assertThat(allDues(supporting)).isEmpty();
        assertThat(allDues(honorary)).isEmpty();
        assertThat(allDues(probation)).isEmpty();
        assertThat(allDues(suspended)).hasSize(7);
    }

    @Test
    void removalStopsNewDuesButKeepsExistingDebt() {
        setUpTierWithRate();
        TMember removed = unbilledMember();
        jdbc.update("UPDATE members SET status = 'REMOVED', removed_at = ? WHERE id = ?",
            NOW.minusMonths(3).atDay(15), removed.getId());

        dues.generateDues(NOW);

        assertThat(allDues(removed)).hasSize(4);
        assertThat(allDues(removed).getLast().month()).isEqualTo(NOW.minusMonths(3));
        assertThat(count(removed, DueStatus.OPEN)).isEqualTo(4);
    }

    @Test
    void noDuesUntilARateExistsAndTheFirstRateBackfills() {
        actor = newMember("Tre", "Asurer", "mazowieckie");
        TMember member = payingMember();

        dues.generateDues(NOW);
        assertThat(allDues(member)).isEmpty();

        tierId = schedule.createTier(actor.getId(), new NewFeeTier("standard", "Standardowa", false)).id();
        assertThat(allDues(member)).isEmpty();

        schedule.addRate(actor.getId(), tierId, NOW.minusMonths(3), money("25"), "U/1");

        assertThat(allDues(member)).hasSize(4);
        assertThat(allDues(member).getFirst().month()).isEqualTo(NOW.minusMonths(3));
    }

    @Test
    void generationIsIdempotent() {
        setUpTierWithRate();
        TMember member = payingMember();

        DuesRun again = dues.generateDues(NOW);

        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(allDues(member)).hasSize(7);
    }

    @Test
    void concurrentRunsDoNotDoubleGenerate() throws Exception {
        setUpTierWithRate();
        TMember member = newMember("Con", "Current", "mazowieckie");
        jdbc.update("UPDATE members SET member_since = ? WHERE id = ?", NOW.minusMonths(6).atDay(1), member.getId());
        jdbc.update("DELETE FROM fee_dues");

        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Callable<DuesRun> run = () -> dues.generateDues(NOW);
            Future<DuesRun> a = pool.submit(run);
            Future<DuesRun> b = pool.submit(run);

            assertThat(a.get().created() + b.get().created()).isEqualTo(7);
        } finally {
            pool.shutdownNow();
        }

        assertThat(allDues(member)).hasSize(7);
    }

    @Test
    void aNewRateAppliesFromItsMonthAndKeepsTheHistory() {
        setUpTierWithRate();
        TMember member = payingMember();

        schedule.addRate(actor.getId(), tierId, NOW.plusMonths(1), money("40"), "U/2");
        dues.generateDues(NOW.plusMonths(2));

        assertThat(dueOf(member, NOW).amount()).isEqualByComparingTo("30");
        assertThat(dueOf(member, NOW.plusMonths(1)).amount()).isEqualByComparingTo("40");
        assertThat(dueOf(member, NOW.plusMonths(2)).amount()).isEqualByComparingTo("40");
        assertThat(dueOf(member, NOW.minusMonths(6)).amount()).isEqualByComparingTo("30");
        assertThat(schedule.rates(tierId)).extracting(FeeRate::monthlyAmount)
                                           .usingElementComparator(BigDecimal::compareTo)
                                           .containsExactly(money("30"), money("40"));
        assertThat(schedule.resolve(member.getId(), NOW.plusMonths(3)).orElseThrow().amount()).isEqualByComparingTo("40");
    }

    @Test
    void aRateCannotStartInAMonthThatHasDues() {
        setUpTierWithRate();
        payingMember();

        assertThat(codeOf(() -> schedule.addRate(actor.getId(), tierId, NOW, money("40"), "U/2")))
            .isEqualTo("RATE_MONTH_HAS_DUES");
        assertThat(codeOf(() -> schedule.addRate(actor.getId(), tierId, NOW.minusMonths(2), money("40"), "U/2")))
            .isEqualTo("RATE_MONTH_HAS_DUES");
        assertThat(codeOf(() -> schedule.addRate(actor.getId(), tierId, NOW.minusMonths(12), money("40"), "U/2")))
            .isEqualTo("RATE_EXISTS");
        assertThat(codeOf(() -> schedule.addRate(actor.getId(), tierId, NOW.plusMonths(1), money("0"), "U/2")))
            .isEqualTo("RATE_AMOUNT_INVALID");
        assertThat(codeOf(() -> schedule.addRate(actor.getId(), tierId, NOW.plusMonths(1), money("5"), " ")))
            .isEqualTo("RATE_REFERENCE_REQUIRED");
        assertThat(schedule.rates(tierId)).hasSize(1);
    }

    @Test
    void generatedDuesKeepTheirAmountWhenRatesChangeLater() {
        setUpTierWithRate();
        TMember member = payingMember();
        BigDecimal before = dueOf(member, NOW).amount();

        schedule.addRate(actor.getId(), tierId, NOW.plusMonths(1), money("99"), "U/2");
        dues.generateDues(NOW);

        assertThat(dueOf(member, NOW).amount()).isEqualByComparingTo(before);
    }

    @Test
    void changingTheTierRecalculatesOpenDuesFromItsMonth() {
        setUpTierWithRate();
        long cheap = schedule.createTier(actor.getId(), new NewFeeTier("reduced", "Ulgowa", false)).id();
        schedule.addRate(actor.getId(), cheap, NOW.minusMonths(12), money("10"), "U/1");
        TMember member = payingMember();

        schedule.assignTier(actor.getId(), member.getId(), cheap, NOW.minusMonths(2));

        assertThat(dueOf(member, NOW.minusMonths(3)).amount()).isEqualByComparingTo("30");
        assertThat(dueOf(member, NOW.minusMonths(2)).amount()).isEqualByComparingTo("10");
        assertThat(dueOf(member, NOW).amount()).isEqualByComparingTo("10");
        assertThat(dueOf(member, NOW).tierId()).isEqualTo(cheap);
        assertThat(schedule.assignments(member.getId())).hasSize(1);
    }

    @Test
    void paidDuesAreNotRewrittenByTierChanges() {
        setUpTierWithRate();
        long cheap = schedule.createTier(actor.getId(), new NewFeeTier("reduced", "Ulgowa", false)).id();
        schedule.addRate(actor.getId(), cheap, NOW.minusMonths(12), money("10"), "U/1");
        TMember member = payingMember();
        payments.record(actor.getId(), new RecordPayment(member.getId(), money("60"), today(), PaymentSource.CASH, null, null));

        schedule.assignTier(actor.getId(), member.getId(), cheap, NOW.minusMonths(6));

        assertThat(dueOf(member, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(member, NOW.minusMonths(6)).amount()).isEqualByComparingTo("30");
        assertThat(dueOf(member, NOW.minusMonths(5)).amount()).isEqualByComparingTo("30");
        assertThat(dueOf(member, NOW.minusMonths(4)).amount()).isEqualByComparingTo("10");
    }

    @Test
    void theDefaultTierIsUnique() {
        setUpTierWithRate();
        FeeTier second = schedule.createTier(actor.getId(), new NewFeeTier("other", "Inna", true));

        assertThat(schedule.tiers(true)).filteredOn(FeeTier::isDefault).extracting(FeeTier::id)
                                         .containsExactly(second.id());
        assertThat(codeOf(() -> schedule.updateTier(actor.getId(), second.id(), new FeeTierUpdate(null, false, null))))
            .isEqualTo("FEE_TIER_DEFAULT_REQUIRED");
        assertThat(codeOf(() -> schedule.createTier(actor.getId(), new NewFeeTier("other", "Inna", false))))
            .isEqualTo("FEE_TIER_CODE_TAKEN");
    }

    @Test
    void tierWritesAreAuditedWithoutAmounts() {
        setUpTierWithRate();

        assertThat(auditCount("fees :: tier-create")).isEqualTo(1);
        assertThat(auditCount("fees :: rate-add")).isEqualTo(1);
        assertThat(auditPayloads("fees :: rate-add")).doesNotContain("30.00");
    }
}
