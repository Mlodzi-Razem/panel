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

package org.mlodzirazem.panel.services.panelapi.members;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberRulesTest {
    private final LocalDate admission = LocalDate.of(2026, 6, 1);

    @Test
    void membershipEndsAtTheEndOfTheYearOfTheTwentySixthBirthday() {
        assertThat(MemberRules.membershipEndDate(LocalDate.of(2000, 1, 1))).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(MemberRules.membershipEndDate(LocalDate.of(2000, 12, 31))).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(MemberRules.membershipEndsIn(LocalDate.of(2000, 7, 7), 2026)).isTrue();
        assertThat(MemberRules.membershipEndsIn(LocalDate.of(2000, 7, 7), 2025)).isFalse();
    }

    @Test
    void ordinaryMembershipRequiresAgeSixteenToTwentySix() {
        assertThatCode(() -> MemberRules.validateAdmissionAge(LocalDate.of(2010, 6, 1), MembershipType.ORDINARY, admission))
            .doesNotThrowAnyException();
        assertThatCode(() -> MemberRules.validateAdmissionAge(LocalDate.of(2000, 6, 1), MembershipType.ORDINARY, admission))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> MemberRules.validateAdmissionAge(LocalDate.of(2010, 6, 2), MembershipType.ORDINARY, admission))
            .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> MemberRules.validateAdmissionAge(LocalDate.of(1999, 6, 1), MembershipType.ORDINARY, admission))
            .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void supportingMembershipRequiresAgeSixteenAndHonoraryHasNoLimit() {
        assertThatCode(() -> MemberRules.validateAdmissionAge(LocalDate.of(2010, 6, 1), MembershipType.SUPPORTING, admission))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> MemberRules.validateAdmissionAge(LocalDate.of(2010, 6, 2), MembershipType.SUPPORTING, admission))
            .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> MemberRules.validateAdmissionAge(LocalDate.of(2012, 6, 1), MembershipType.SUPPORTING, admission))
            .isInstanceOf(RuleViolationException.class);
        assertThatCode(() -> MemberRules.validateAdmissionAge(LocalDate.of(1950, 1, 1), MembershipType.HONORARY, admission))
            .doesNotThrowAnyException();
    }

    @Test
    void recognisesReservedTestBandMemberNumbers() {
        assertThat(MemberRules.isReservedTestMemberNumber("950000")).isTrue();
        assertThat(MemberRules.isReservedTestMemberNumber("999999")).isTrue();
        assertThat(MemberRules.isReservedTestMemberNumber("975001")).isTrue();
        assertThat(MemberRules.isReservedTestMemberNumber("0950000")).isTrue();

        assertThat(MemberRules.isReservedTestMemberNumber("949999")).isFalse();
        assertThat(MemberRules.isReservedTestMemberNumber("1000000")).isFalse();
        assertThat(MemberRules.isReservedTestMemberNumber("000205")).isFalse();
        assertThat(MemberRules.isReservedTestMemberNumber("1")).isFalse();
        assertThat(MemberRules.isReservedTestMemberNumber("MR-950123")).isFalse();
        assertThat(MemberRules.isReservedTestMemberNumber("")).isFalse();
        assertThat(MemberRules.isReservedTestMemberNumber(null)).isFalse();

        assertThatThrownBy(() -> MemberRules.assertMemberNumberNotReserved("999999"))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("MEMBER_ID_RESERVED");
        assertThatCode(() -> MemberRules.assertMemberNumberNotReserved("000205")).doesNotThrowAnyException();
        assertThatCode(() -> MemberRules.assertMemberNumberNotReserved(null)).doesNotThrowAnyException();
    }
}
