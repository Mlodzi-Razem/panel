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

package org.mlodzirazem.panel.services.panelapi.core.time;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The single source of "now" in the application. Every statutory window (the recruitment 62/31/14
 * day clocks, the +7 day admission resolution deadline, token TTLs) reads it through
 * {@code LocalDate.now(clock)} etc., so the whole application lives on one clock.
 *
 * <p>{@code panel.dev.clock-offset-days} (decision D8) shifts this single clock forward for the
 * local test harness; it is refused at startup unless the {@code dev} profile is active. Production
 * profiles ({@code prod}/{@code production}) and unprofiled deployments (stage included) always
 * refuse — the offset never reaches production.</p>
 */
@Configuration
@EnableConfigurationProperties(DevClockProperties.class)
public class TimeConfiguration {
    @Bean
    @Primary
    public Clock clock(DevClockProperties dev, Environment environment) {
        int offsetDays = dev.clockOffsetDays();

        if (offsetDays == 0) {
            return Clock.systemUTC();
        }

        Set<String> active = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));

        if (active.contains(DevClockProperties.DEVIATION_PROFILE)) {
            return Clock.offset(Clock.systemUTC(), Duration.ofDays(offsetDays));
        }

        throw new IllegalStateException(
            "panel.dev.clock-offset-days=" + offsetDays + " is a test-only setting (decision D8) and "
                + "refuses to start here: a shifted clock requires the '"
                + DevClockProperties.DEVIATION_PROFILE + "' profile (SPRING_PROFILES_ACTIVE="
                + DevClockProperties.DEVIATION_PROFILE + "), while production profiles "
                + DevClockProperties.PRODUCTION_PROFILES + " and unprofiled deployments never allow it");
    }
}

