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

package org.mlodzirazem.panel.services.panelapi.bodies;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.bodies.impl.FunctionRules;
import org.mlodzirazem.panel.services.panelapi.bodies.impl.FunctionRules.Span;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    @Test
    void overlapTreatsValidToAsExclusiveAndNullAsOpenEnded() {
        Span a = new Span(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(FunctionRules.overlaps(a, new Span(LocalDate.of(2026, 1, 1), null))).isFalse();
        assertThat(FunctionRules.overlaps(a, new Span(LocalDate.of(2025, 12, 31), null))).isTrue();
        assertThat(FunctionRules.overlaps(new Span(LocalDate.of(2020, 1, 1), null), a)).isTrue();
    }

    @Test
    void countsConsecutiveOneYearTerms() {
        List<Span> existing = List.of(
            new Span(LocalDate.of(2024, 6, 1), LocalDate.of(2025, 6, 1)),
            new Span(LocalDate.of(2025, 6, 1), LocalDate.of(2026, 6, 1)));
        Span candidate = new Span(LocalDate.of(2026, 6, 1), LocalDate.of(2027, 6, 1));

        assertThat(FunctionRules.consecutiveTerms(existing, candidate, 12, TODAY)).isEqualTo(3);
        assertThat(FunctionRules.consecutiveTerms(existing.subList(1, 2), candidate, 12, TODAY)).isEqualTo(2);
        assertThat(FunctionRules.consecutiveTerms(List.of(), candidate, 12, TODAY)).isEqualTo(1);
    }

    @Test
    void smallGapsStillCountAsConsecutiveButBigGapsDoNot() {
        Span previous = new Span(LocalDate.of(2025, 6, 1), LocalDate.of(2026, 5, 20));
        Span candidate = new Span(LocalDate.of(2026, 6, 1), LocalDate.of(2027, 6, 1));
        Span farPrevious = new Span(LocalDate.of(2023, 6, 1), LocalDate.of(2024, 6, 1));

        assertThat(FunctionRules.consecutiveTerms(List.of(previous), candidate, 12, TODAY)).isEqualTo(2);
        assertThat(FunctionRules.consecutiveTerms(List.of(farPrevious), candidate, 12, TODAY)).isEqualTo(1);
    }

    @Test
    void openEndedCandidateCountsAsOneTerm() {
        Span candidate = new Span(LocalDate.of(2026, 6, 1), null);

        assertThat(FunctionRules.consecutiveTerms(List.of(), candidate, 12, TODAY)).isEqualTo(1);
    }

    @Test
    void previousChainCountsBackToBackSpans() {
        Span first = new Span(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1));
        Span second = new Span(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1));
        Span unrelated = new Span(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 4, 1));
        Span candidate = new Span(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 1));

        FunctionRules.Chain chain = FunctionRules.previousChain(List.of(unrelated, second, first), candidate);

        assertThat(chain.previousCount()).isEqualTo(2);
        assertThat(chain.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(FunctionRules.previousChain(List.of(), candidate).previousCount()).isZero();
    }
}
