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

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeConfigurationTest {

    @Test
    void theDefaultClockIsTheSystemUtcClock() {
        Clock clock = new TimeConfiguration().clock(new DevClockProperties(null), environmentWith());

        Instant before = Instant.now().minus(Duration.ofSeconds(5));
        Instant now = Instant.now(clock);
        Instant after = Instant.now().plus(Duration.ofSeconds(5));

        // system clock, zero offset, still UTC (the application sets the JVM timezone to UTC)
        assertThat(now).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void theDevProfileShiftsTheSingleClockByWholeDays() {
        Clock clock = new TimeConfiguration().clock(new DevClockProperties(70), environmentWith("dev"));

        LocalDate shiftedToday = LocalDate.now(clock);

        assertThat(shiftedToday).isEqualTo(LocalDate.now(Clock.systemUTC()).plusDays(70));
    }

    @Test
    void theProductionPlanProfileRefusesToStartWithAShiftedClock() {
        TimeConfiguration configuration = new TimeConfiguration();

        assertThatThrownBy(() -> configuration.clock(new DevClockProperties(1), environmentWith("prod")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("panel.dev.clock-offset-days=1 is a test-only setting")
            .hasMessageContaining("prod");
    }

    @Test
    void theProductionProfileRefusesToStartWithAShiftedClock() {
        assertThatThrownBy(() ->
            new TimeConfiguration().clock(new DevClockProperties(62), environmentWith("production")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("refuses to start");
    }

    @Test
    void anUnprofiledDeploymentRefusesToStartWithAShiftedClock() {
        // stage and any other deployment run without profiles; a stray env var must not slide the clock
        assertThatThrownBy(() ->
            new TimeConfiguration().clock(new DevClockProperties(70), environmentWith()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires the 'dev' profile");
    }

    @Test
    void productionBootsFineWhenTheOffsetIsNotUsed() {
        Clock clock = new TimeConfiguration().clock(new DevClockProperties(0), environmentWith("prod"));

        assertThat(clock).isSameAs(Clock.systemUTC());
    }

    @Test
    void theOffsetBindsFromThePANEL_DEV_CLOCK_OFFSET_DAYSEnvironmentVariable() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
            new SystemEnvironmentPropertySource("test", Map.of(
                "PANEL_DEV_CLOCK_OFFSET_DAYS", "76"
            )));

        DevClockProperties properties = Binder.get(environment)
            .bind("panel.dev", Bindable.of(DevClockProperties.class))
            .get();

        assertThat(properties.clockOffsetDays()).isEqualTo(76);
    }

    @Test
    void aNegativeOrAbsurdOffsetIsRejectedAtBindingTime() {
        assertThatThrownBy(() -> new DevClockProperties(-1))
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new DevClockProperties(DevClockProperties.MAX_OFFSET_DAYS + 1))
            .isInstanceOf(IllegalStateException.class);

        // the harness range itself is valid
        assertThat(new DevClockProperties(DevClockProperties.MAX_OFFSET_DAYS).clockOffsetDays())
            .isEqualTo(DevClockProperties.MAX_OFFSET_DAYS);
    }

    private static StandardEnvironment environmentWith(String... activeProfiles) {
        StandardEnvironment environment = new StandardEnvironment();

        environment.setActiveProfiles(activeProfiles);

        return environment;
    }
}
