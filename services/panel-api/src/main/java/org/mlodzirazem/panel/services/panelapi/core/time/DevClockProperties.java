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

package org.mlodzirazem.panel.services.panelapi.core.time;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Test-only deviation of the single application clock (decision D8 of the workflow plan: clock
 * shifting is allowed only outside production, and a production profile refuses to start with it).
 *
 * <p>The offset is applied inside the {@link Clock} bean — the one source of "today" every statutory
 * window (recruitment 62/31/14 days, the +7 admission resolution deadline, token TTLs) already
 * reads through {@code LocalDate.now(clock)}. No second source of time is introduced.</p>
 *
 * <p>Environment variable: {@code PANEL_DEV_CLOCK_OFFSET_DAYS} (see {@code panel.yml}).</p>
 */
@ConfigurationProperties(prefix = "panel.dev")
public record DevClockProperties(@Nullable Integer clockOffsetDays) {

    /** Enough headroom for any statutory window (a decade); anything larger is a misconfiguration. */
    public static final int MAX_OFFSET_DAYS = 3660;

    /** The Spring profile that opts a deployment into allowing a shifted clock. */
    public static final String DEVIATION_PROFILE = "dev";

    /** Spring profiles that mean production; they always refuse a shifted clock (decision D8). */
    public static final java.util.Set<String> PRODUCTION_PROFILES = java.util.Set.of("prod", "production");

    public DevClockProperties {
        clockOffsetDays = clockOffsetDays == null ? 0 : clockOffsetDays;

        if (clockOffsetDays < 0 || clockOffsetDays > MAX_OFFSET_DAYS) {
            throw new IllegalStateException(
                "panel.dev.clock-offset-days must be between 0 and " + MAX_OFFSET_DAYS
                    + " days (PANEL_DEV_CLOCK_OFFSET_DAYS)");
        }
    }
}
