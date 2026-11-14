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

package org.mlodzirazem.panel.services.panelapi.bodies.impl;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FunctionRules {
    public static final int CONSECUTIVE_GAP_DAYS = 31;
    private static final double DAYS_PER_MONTH = 30.4375;

    private FunctionRules() {
    }

    public record Span(LocalDate from, @Nullable LocalDate to) {
        boolean overlaps(Span other) {
            boolean thisStartsBeforeOtherEnds = other.to == null || from.isBefore(other.to);
            boolean otherStartsBeforeThisEnds = to == null || other.from.isBefore(to);
            return thisStartsBeforeOtherEnds && otherStartsBeforeThisEnds;
        }
    }

    public static boolean overlaps(Span a, Span b) {
        return a.overlaps(b);
    }

    public static int consecutiveTerms(List<Span> existing, Span candidate, int termLengthMonths, LocalDate today) {
        List<Span> all = new ArrayList<>(existing);
        all.add(candidate);
        all.sort(Comparator.comparing(Span::from));

        LocalDate chainStart = null;
        LocalDate chainEnd = null;
        boolean candidateInChain = false;

        for (Span span : all) {
            LocalDate end = effectiveEnd(span, termLengthMonths, today);

            if (chainStart == null) {
                chainStart = span.from();
                chainEnd = end;
                candidateInChain = span == candidate;
            } else if (!span.from().isAfter(chainEnd.plusDays(CONSECUTIVE_GAP_DAYS))) {
                chainEnd = end.isAfter(chainEnd) ? end : chainEnd;
                candidateInChain |= span == candidate;
            } else if (candidateInChain) {
                break;
            } else {
                chainStart = span.from();
                chainEnd = end;
                candidateInChain = span == candidate;
            }
        }

        return terms(chainStart, chainEnd, termLengthMonths);
    }

    public record Chain(int previousCount, LocalDate start) {
    }

    public static Chain previousChain(List<Span> existing, Span candidate) {
        List<Span> remaining = new ArrayList<>(existing);
        LocalDate current = candidate.from();
        int count = 0;

        while (true) {
            LocalDate currentStart = current;

            Span previous = remaining.stream()
                                     .filter(s -> s.from().isBefore(currentStart))
                                     .filter(s -> s.to() != null && !s.to().plusDays(CONSECUTIVE_GAP_DAYS).isBefore(currentStart))
                                     .max(Comparator.comparing(Span::to))
                                     .orElse(null);

            if (previous == null) {
                return new Chain(count, current);
            }

            remaining.remove(previous);
            current = previous.from();
            count++;
        }
    }

    private static LocalDate effectiveEnd(Span span, int termLengthMonths, LocalDate today) {
        if (span.to() != null) {
            return span.to();
        }

        LocalDate minimum = span.from().plusMonths(termLengthMonths);
        return today.isAfter(minimum) ? today : minimum;
    }

    private static int terms(LocalDate start, LocalDate end, int termLengthMonths) {
        long days = ChronoUnit.DAYS.between(start, end);
        return (int) Math.max(1, Math.round(days / (termLengthMonths * DAYS_PER_MONTH)));
    }
}
