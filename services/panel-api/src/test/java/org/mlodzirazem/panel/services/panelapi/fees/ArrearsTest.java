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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.mlodzirazem.panel.services.panelapi.members.ElectoralRightsService;

@SpringBootTest
@PostgresTest
class ArrearsTest extends FeesTestSupport {
    @Autowired
    ElectoralRightsService electoralRights;

    private LocalDate dueDate(YearMonth month) {
        return month.atEndOfMonth();
    }

    @Test
    void noOpenDuesMeansNoArrears() {
        setUpTierWithRate();
        TMember member = unbilledMember();

        assertThat(arrears.of(member.getId()).since()).isNull();
        assertThat(arrears.of(member.getId()).suspendsElectoralRights()).isFalse();
    }

    @Test
    void arrearsStartAtTheDueDateOfTheOldestOverdueDue() {
        setUpTierWithRate();
        TMember member = payingMember();
        LocalDate since = dueDate(NOW.minusMonths(6));

        Arrears onToday = arrears.of(member.getId(), today());

        assertThat(onToday.since()).isEqualTo(since);
        assertThat(onToday.overdueDues()).isEqualTo(6);
        assertThat(onToday.overdueAmount()).isEqualByComparingTo("180");
        assertThat(arrears.of(member.getId(), since).since()).isNull();
    }

    @Test
    void thresholdsAreStrictlyLongerThanThreeAndFourMonths() {
        setUpTierWithRate();
        TMember member = payingMember();
        LocalDate since = dueDate(NOW.minusMonths(6));

        assertThat(arrears.of(member.getId(), since.plusMonths(3)).suspendsElectoralRights()).isFalse();
        assertThat(arrears.of(member.getId(), since.plusMonths(3).plusDays(1)).suspendsElectoralRights()).isTrue();
        assertThat(arrears.of(member.getId(), since.plusMonths(4)).groundsForExclusion()).isFalse();
        assertThat(arrears.of(member.getId(), since.plusMonths(4).plusDays(1)).groundsForExclusion()).isTrue();
    }

    @Test
    void duesCoveredByARunningDeferralAreJustified() {
        setUpTierWithRate();
        TMember member = payingMember();
        adjustments.grant(actor.getId(), new NewAdjustment(member.getId(), AdjustmentKind.DEFERRAL, NOW.minusMonths(6),
            null, null, AdjustmentReason.HEALTH, "why", null));

        Arrears now = arrears.of(member.getId(), today());

        assertThat(now.since()).isNull();
        assertThat(now.justifiedDues()).isEqualTo(6);
        assertThat(now.justifiedAmount()).isEqualByComparingTo("180");
        assertThat(now.suspendsElectoralRights()).isFalse();
        assertThat(electoralRights.rightsOf(member.getId()).feeArrears()).isFalse();
    }

    @Test
    void arrearsOfADeferredDueStartWhenTheDeferralEnds() {
        setUpTierWithRate();
        TMember member = payingMember();
        adjustments.grant(actor.getId(), new NewAdjustment(member.getId(), AdjustmentKind.DEFERRAL, NOW.minusMonths(6),
            NOW.minusMonths(4), null, AdjustmentReason.HEALTH, "why", null));

        assertThat(arrears.of(member.getId(), dueDate(NOW.minusMonths(5))).since()).isNull();
        assertThat(arrears.of(member.getId(), today()).since()).isEqualTo(dueDate(NOW.minusMonths(4)));
    }

    @Test
    void aRevokedDeferralStopsJustifyingFromTheRevocation() {
        setUpTierWithRate();
        TMember member = payingMember();
        AdjustmentDetails deferral = adjustments.grant(actor.getId(), new NewAdjustment(member.getId(),
            AdjustmentKind.DEFERRAL, NOW.minusMonths(6), null, null, AdjustmentReason.HEALTH, "why", null));

        adjustments.revoke(actor.getId(), deferral.view().id(), NOW.minusMonths(3));

        assertThat(arrears.of(member.getId(), today()).since()).isEqualTo(dueDate(NOW.minusMonths(4)));
    }

    @Test
    void feeArrearsPolicyRemovesElectoralRightsAndARetroactiveExemptionRestoresThem() {
        setUpTierWithRate();
        TMember member = payingMember();
        payments.record(actor.getId(), new RecordPayment(member.getId(), money("30"), today(), PaymentSource.CASH, null, null));

        assertThat(electoralRights.rightsOf(member.getId()).feeArrears()).isTrue();
        assertThat(electoralRights.rightsOf(member.getId()).eligible()).isFalse();
        assertThat(electoralRights.nationalElectorate()).extracting(TMember::getId).doesNotContain(member.getId());

        adjustments.grant(actor.getId(), new NewAdjustment(member.getId(), AdjustmentKind.EXEMPTION, NOW.minusMonths(6),
            NOW, null, AdjustmentReason.FINANCIAL_HARDSHIP, "hardship", "ZK/2"));

        assertThat(dueOf(member, NOW.minusMonths(6)).status()).isEqualTo(DueStatus.PAID);
        assertThat(arrears.of(member.getId()).since()).isNull();
        assertThat(electoralRights.rightsOf(member.getId()).feeArrears()).isFalse();
        assertThat(electoralRights.rightsOf(member.getId()).eligible()).isTrue();
        assertThat(electoralRights.nationalElectorate()).extracting(TMember::getId).contains(member.getId());
    }

    @Test
    void payingTheArrearsRestoresElectoralRights() {
        setUpTierWithRate();
        TMember member = payingMember();

        assertThat(electoralRights.rightsOf(member.getId()).feeArrears()).isTrue();

        payments.record(actor.getId(), new RecordPayment(member.getId(), money("210"), today(), PaymentSource.CASH, null, null));

        assertThat(electoralRights.rightsOf(member.getId()).feeArrears()).isFalse();
    }

    @Test
    void listsMembersInArrearsScopedByDistrict() {
        setUpTierWithRate();
        TMember late = payingMember();
        TMember inSilesia = newMember("Sil", "Esia", "slaskie");
        jdbc.update("UPDATE members SET member_since = ? WHERE id = ?", NOW.minusMonths(6).atDay(1), inSilesia.getId());
        dues.refresh(inSilesia.getId());
        TMember removed = payingMember();
        jdbc.update("UPDATE members SET status = 'REMOVED' WHERE id = ?", removed.getId());
        TMember current = ordinaryMember("Cur", "Rent", NOW);
        dues.refresh(current.getId());

        long mazowieckie = district("mazowieckie").getId();
        long slaskie = district("slaskie").getId();

        assertThat(arrears.longerThan(ArrearsService.ELECTORAL_RIGHTS_MONTHS, DistrictScope.all()))
            .extracting(MemberArrears::memberId).containsExactlyInAnyOrder(late.getId(), inSilesia.getId());
        assertThat(arrears.longerThan(ArrearsService.ELECTORAL_RIGHTS_MONTHS, DistrictScope.of(List.of(slaskie))))
            .extracting(MemberArrears::memberId).containsExactly(inSilesia.getId());
        assertThat(arrears.longerThan(ArrearsService.EXCLUSION_MONTHS, DistrictScope.of(List.of(mazowieckie))))
            .singleElement().satisfies(m -> {
                assertThat(m.memberId()).isEqualTo(late.getId());
                assertThat(m.districtId()).isEqualTo(mazowieckie);
            });
        assertThat(arrears.longerThan(1, DistrictScope.of(List.of()))).isEmpty();
    }
}
