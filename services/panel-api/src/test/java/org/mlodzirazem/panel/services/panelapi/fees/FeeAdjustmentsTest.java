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
class FeeAdjustmentsTest extends FeesTestSupport {
    private static final String SECRET = "SECRET-JUSTIFICATION-TEXT";

    private NewAdjustment exemption(TMember member, YearMonth from, YearMonth to) {
        return new NewAdjustment(member.getId(), AdjustmentKind.EXEMPTION, from, to, null,
            AdjustmentReason.FINANCIAL_HARDSHIP, SECRET, "ZK/1");
    }

    @Test
    void aRetroactiveExemptionCancelsOpenDuesAndKeepsPaidOnes() {
        setUpTierWithRate();
        TMember member = payingMember();
        payments.record(actor.getId(), new RecordPayment(member.getId(), money("60"), today(), PaymentSource.CASH, null, null));

        adjustments.grant(actor.getId(), exemption(member, NOW.minusMonths(6), NOW.minusMonths(3)));

        assertThat(dueOf(member, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(member, NOW.minusMonths(5)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(member, NOW.minusMonths(4)).status()).isEqualTo(DueStatus.CANCELLED);
        assertThat(dueOf(member, NOW.minusMonths(3)).status()).isEqualTo(DueStatus.CANCELLED);
        assertThat(dueOf(member, NOW.minusMonths(2)).status()).isEqualTo(DueStatus.OPEN);
        assertThat(payments.balance(member.getId()).owed()).isEqualByComparingTo("90");
    }

    @Test
    void anExemptionDoesNotCreateFutureDues() {
        setUpTierWithRate();
        TMember member = payingMember();

        adjustments.grant(actor.getId(), exemption(member, NOW.plusMonths(1), null));
        dues.generateDues(NOW.plusMonths(3));

        assertThat(allDues(member)).extracting(Due::month).doesNotContain(NOW.plusMonths(1), NOW.plusMonths(3));
        assertThat(schedule.resolve(member.getId(), NOW.plusMonths(2)).orElseThrow().exempt()).isTrue();
    }

    @Test
    void aDiscountReducesOpenDuesToTheFixedAmount() {
        setUpTierWithRate();
        TMember member = payingMember();
        payments.record(actor.getId(), new RecordPayment(member.getId(), money("30"), today(), PaymentSource.CASH, null, null));

        adjustments.grant(actor.getId(), new NewAdjustment(member.getId(), AdjustmentKind.DISCOUNT, NOW.minusMonths(6),
            NOW, money("10"), AdjustmentReason.HEALTH, SECRET, null));

        assertThat(dueOf(member, NOW.minusMonths(6)).amount()).isEqualByComparingTo("30");
        assertThat(dueOf(member, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
        assertThat(dueOf(member, NOW.minusMonths(5)).amount()).isEqualByComparingTo("10");
        assertThat(dueOf(member, NOW).amount()).isEqualByComparingTo("10");
    }

    @Test
    void aDiscountAboveTheRateDoesNotRaiseTheDue() {
        setUpTierWithRate();
        TMember member = payingMember();

        adjustments.grant(actor.getId(), new NewAdjustment(member.getId(), AdjustmentKind.DISCOUNT, NOW, null,
            money("99"), AdjustmentReason.OTHER, SECRET, null));

        assertThat(dueOf(member, NOW).amount()).isEqualByComparingTo("30");
    }

    @Test
    void aDeferralKeepsTheDuesOwed() {
        setUpTierWithRate();
        TMember member = payingMember();

        adjustments.grant(actor.getId(), new NewAdjustment(member.getId(), AdjustmentKind.DEFERRAL, NOW.minusMonths(6),
            NOW, null, AdjustmentReason.HEALTH, SECRET, null));

        assertThat(count(member, DueStatus.OPEN)).isEqualTo(7);
        assertThat(payments.balance(member.getId()).owed()).isEqualByComparingTo("210");
    }

    @Test
    void revokingOnlyAffectsMonthsFromTheRevocationOn() {
        setUpTierWithRate();
        TMember member = payingMember();
        AdjustmentDetails granted = adjustments.grant(actor.getId(), exemption(member, NOW.minusMonths(4), null));
        assertThat(count(member, DueStatus.CANCELLED)).isEqualTo(5);

        AdjustmentDetails revoked = adjustments.revoke(actor.getId(), granted.view().id(), NOW.minusMonths(1));

        assertThat(revoked.view().revoked()).isTrue();
        assertThat(dueOf(member, NOW.minusMonths(4)).status()).isEqualTo(DueStatus.CANCELLED);
        assertThat(dueOf(member, NOW.minusMonths(2)).status()).isEqualTo(DueStatus.CANCELLED);
        assertThat(dueOf(member, NOW.minusMonths(1)).status()).isEqualTo(DueStatus.OPEN);
        assertThat(dueOf(member, NOW).status()).isEqualTo(DueStatus.OPEN);
        assertThat(dueOf(member, NOW).amount()).isEqualByComparingTo("30");
        assertThat(codeOf(() -> adjustments.revoke(actor.getId(), granted.view().id()))).isEqualTo("ADJUSTMENT_ALREADY_REVOKED");
    }

    @Test
    void revokingFromTheCurrentMonthKeepsEarlierAdjustedMonths() {
        setUpTierWithRate();
        TMember member = payingMember();
        AdjustmentDetails granted = adjustments.grant(actor.getId(), exemption(member, NOW.minusMonths(3), null));

        adjustments.revoke(actor.getId(), granted.view().id());

        assertThat(dueOf(member, NOW.minusMonths(1)).status()).isEqualTo(DueStatus.CANCELLED);
        assertThat(dueOf(member, NOW).status()).isEqualTo(DueStatus.OPEN);
        assertThat(count(member, DueStatus.CANCELLED)).isEqualTo(3);
    }

    @Test
    void invalidAdjustmentsAreRejected() {
        setUpTierWithRate();
        TMember member = payingMember();

        assertThat(codeOf(() -> adjustments.grant(actor.getId(), exemption(member, NOW, NOW.minusMonths(1)))))
            .isEqualTo("ADJUSTMENT_RANGE_INVALID");
        assertThat(codeOf(() -> adjustments.grant(actor.getId(), new NewAdjustment(member.getId(),
            AdjustmentKind.DISCOUNT, NOW, null, null, AdjustmentReason.OTHER, "x", null))))
            .isEqualTo("ADJUSTMENT_AMOUNT_REQUIRED");
        assertThat(codeOf(() -> adjustments.grant(actor.getId(), new NewAdjustment(member.getId(),
            AdjustmentKind.EXEMPTION, NOW, null, money("5"), AdjustmentReason.OTHER, "x", null))))
            .isEqualTo("ADJUSTMENT_AMOUNT_NOT_ALLOWED");
        assertThat(codeOf(() -> adjustments.grant(actor.getId(), new NewAdjustment(member.getId(),
            AdjustmentKind.EXEMPTION, NOW, null, null, AdjustmentReason.OTHER, " ", null))))
            .isEqualTo("ADJUSTMENT_REASON_REQUIRED");

        adjustments.grant(actor.getId(), exemption(member, NOW.minusMonths(2), NOW));

        assertThat(codeOf(() -> adjustments.grant(actor.getId(), exemption(member, NOW, NOW.plusMonths(2)))))
            .isEqualTo("ADJUSTMENT_OVERLAP");
        assertThat(codeOf(() -> adjustments.grant(actor.getId(), exemption(member, NOW.plusMonths(1), null))))
            .isEqualTo("NONE");
    }

    @Test
    void reasonsAreOnlyInTheDetailsAndNeverInTheAudit() {
        setUpTierWithRate();
        TMember member = payingMember();

        AdjustmentDetails granted = adjustments.grant(actor.getId(), exemption(member, NOW.minusMonths(2), NOW));
        adjustments.revoke(actor.getId(), granted.view().id());

        assertThat(adjustments.detailsOf(member.getId())).singleElement().satisfies(d -> {
            assertThat(d.reasonCategory()).isEqualTo(AdjustmentReason.FINANCIAL_HARDSHIP);
            assertThat(d.justification()).isEqualTo(SECRET);
        });
        assertThat(adjustments.adjustmentsOf(member.getId())).singleElement().satisfies(v -> {
            assertThat(v.kind()).isEqualTo(AdjustmentKind.EXEMPTION);
            assertThat(v.toString()).doesNotContain(SECRET).doesNotContain("FINANCIAL_HARDSHIP");
        });
        assertThat(payments.history(member.getId()).adjustments()).hasSize(1);
        assertThat(AdjustmentView.class.getRecordComponents()).extracting(c -> c.getName())
                                                               .doesNotContain("reasonCategory", "justification");

        String audit = auditPayloads("fees :: adjustment-grant") + auditPayloads("fees :: adjustment-revoke");
        assertThat(audit).isNotBlank().doesNotContain(SECRET).doesNotContain("FINANCIAL_HARDSHIP").doesNotContain("ZK/1");
    }
}
