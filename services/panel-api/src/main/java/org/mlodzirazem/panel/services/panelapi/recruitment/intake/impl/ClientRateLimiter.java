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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake.impl;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding one-hour window per client address, in memory only: addresses are never stored or logged. It is asked only
 * after the global limits, and every call first forgets what left the window, so it holds no more addresses than
 * the global hourly limit allows and none for longer than the window (until the next call).
 */
@Component
class ClientRateLimiter {
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new HashMap<>();

    ClientRateLimiter(Clock clock) {
        this.clock = clock;
    }

    synchronized boolean tryAcquire(String client, int limit) {
        Instant now = clock.instant();
        Instant oldest = now.minus(WINDOW);

        hits.values().forEach(times -> {
            while (!times.isEmpty() && times.peekFirst().isBefore(oldest)) {
                times.removeFirst();
            }
        });
        hits.values().removeIf(Deque::isEmpty);

        Deque<Instant> times = hits.computeIfAbsent(client, unused -> new ArrayDeque<>());

        if (times.size() >= limit) {
            return false;
        }

        times.addLast(now);
        return true;
    }
}
