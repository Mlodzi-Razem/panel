/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

package org.mlodzirazem.panel.services.panelapi.suspensions;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SuspensionRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    private static Suspension s(SuspensionKind kind, SuspensionScope scope, LocalDate start, LocalDate end,
                                LocalDate lifted, LocalDate appealEnded, boolean voided) {
        return new Suspension(1L, 2L, kind, scope, null, 3L, "body", start, end, start, "ZO/1/2026",
            lifted, lifted == null ? null : "reason", lifted == null ? null : 4L,
            lifted == null ? null : "ZO/2/2026", appealEnded, appealEnded == null ? null : "ref",
            voided, voided ? Instant.now() : null, voided ? "omyłka" : null, null, Instant.now());
    }

    private static Suspension full(LocalDate start, LocalDate end, LocalDate lifted, LocalDate appealEnded) {
        return s(SuspensionKind.ARREARS, SuspensionScope.FULL, start, end, lifted, appealEnded, false);
    }

    @Test
    void stateFollowsTheDates() {
        assertThat(full(TODAY.plusDays(1), null, null, null).state(TODAY)).isEqualTo(SuspensionState.SCHEDULED);
        assertThat(full(TODAY, null, null, null).state(TODAY)).isEqualTo(SuspensionState.ACTIVE);
        // the end is exclusive: a suspension ending today is already over
        assertThat(full(TODAY.minusDays(10), TODAY, null, null).state(TODAY)).isEqualTo(SuspensionState.EXPIRED);
        assertThat(full(TODAY.minusDays(10), TODAY.plusDays(1), null, null).state(TODAY)).isEqualTo(SuspensionState.ACTIVE);
        assertThat(full(TODAY.minusDays(10), null, TODAY, null).state(TODAY)).isEqualTo(SuspensionState.LIFTED);
        // a lift dated in the future does not end the suspension yet
        assertThat(full(TODAY.minusDays(10), null, TODAY.plusDays(2), null).state(TODAY)).isEqualTo(SuspensionState.ACTIVE);
        assertThat(s(SuspensionKind.ARREARS, SuspensionScope.FULL, TODAY, null, null, null, true).state(TODAY))
            .isEqualTo(SuspensionState.VOIDED);
    }

    @Test
    void passiveRightLosesPositionsAfterSixtyTwoDays() {
        LocalDate start = TODAY.minusDays(70);
        Suspension open = s(SuspensionKind.BY_ORGAN, SuspensionScope.PASSIVE_ELECTORAL, start, null, null, null, false);
        assertThat(SuspensionRules.positionLossDueOn(open))
            .isEqualTo(new SuspensionRules.LossDate(true, start.plusDays(62)));

        Suspension endedAt62 = s(SuspensionKind.BY_ORGAN, SuspensionScope.PASSIVE_ELECTORAL, start,
            start.plusDays(62), null, null, false);
        assertThat(SuspensionRules.positionLossDueOn(endedAt62).applicable()).isFalse();

        Suspension liftedAt70 = s(SuspensionKind.BY_ORGAN, SuspensionScope.PASSIVE_ELECTORAL, start, null,
            start.plusDays(70), null, false);
        assertThat(SuspensionRules.positionLossDueOn(liftedAt70).dueOn()).isEqualTo(start.plusDays(62));
    }

    @Test
    void fullSuspensionWaitsForTheAppealPeriod() {
        Suspension unknown = full(TODAY.minusDays(5), null, null, null);
        assertThat(SuspensionRules.positionLossDueOn(unknown)).isEqualTo(new SuspensionRules.LossDate(true, null));

        LocalDate appeal = TODAY.minusDays(1);
        Suspension recorded = full(TODAY.minusDays(5), null, null, appeal);
        assertThat(SuspensionRules.positionLossDueOn(recorded)).isEqualTo(new SuspensionRules.LossDate(true, appeal));

        Suspension appealAfterEnd = full(TODAY.minusDays(5), TODAY.minusDays(3), null, appeal);
        assertThat(SuspensionRules.positionLossDueOn(appealAfterEnd).applicable()).isFalse();
    }

    @Test
    void partialAndVoidedRowsNeverLosePositions() {
        assertThat(SuspensionRules.positionLossDueOn(
            s(SuspensionKind.BY_ORGAN, SuspensionScope.PARTIAL_OTHER, TODAY.minusDays(100), null, null, null, false))
            .applicable()).isFalse();
        assertThat(SuspensionRules.positionLossDueOn(
            s(SuspensionKind.ARREARS, SuspensionScope.FULL, TODAY.minusDays(100), null, null, TODAY.minusDays(1), true))
            .applicable()).isFalse();
    }

    @Test
    void competenceMatrixOfArticleTen() {
        assertThat(SuspensionRules.competent(SuspensionKind.ARREARS, BodyKind.NATIONAL_BOARD, false)).isFalse();
        assertThat(SuspensionRules.competent(SuspensionKind.VOLUNTARY, BodyKind.NATIONAL_BOARD, false)).isTrue();
        assertThat(SuspensionRules.competent(SuspensionKind.BY_ORGAN, BodyKind.NATIONAL_BOARD, false)).isTrue();

        assertThat(SuspensionRules.competent(SuspensionKind.ARREARS, BodyKind.DISTRICT_BOARD, true)).isTrue();
        assertThat(SuspensionRules.competent(SuspensionKind.VOLUNTARY, BodyKind.DISTRICT_BOARD, true)).isTrue();
        assertThat(SuspensionRules.competent(SuspensionKind.BY_ORGAN, BodyKind.DISTRICT_BOARD, false)).isFalse();

        assertThat(SuspensionRules.competent(SuspensionKind.BY_ORGAN, BodyKind.PEER_COURT, false)).isTrue();
        assertThat(SuspensionRules.competent(SuspensionKind.ARREARS, BodyKind.PEER_COURT, false)).isFalse();
        assertThat(SuspensionRules.competent(SuspensionKind.BY_ORGAN, BodyKind.TEAM, true)).isFalse();
        assertThat(SuspensionRules.competent(SuspensionKind.BY_ORGAN, null, true)).isFalse();
    }
}
