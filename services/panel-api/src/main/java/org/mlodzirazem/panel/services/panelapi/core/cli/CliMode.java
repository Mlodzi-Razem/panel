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

package org.mlodzirazem.panel.services.panelapi.core.cli;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;

import java.util.Arrays;
import java.util.Map;

public final class CliMode {
    public static final String PROFILE = "cli";
    public static final int EXIT_OK = 0;
    public static final int EXIT_PROBLEMS = 1;
    public static final int EXIT_FATAL = 2;

    private CliMode() {
    }

    public record Trigger(String property, String environmentVariable) {
    }

    public static boolean requested(String[] args, Trigger... triggers) {
        CommandLinePropertySource<?> source = new SimpleCommandLinePropertySource(args);

        return Arrays.stream(triggers).anyMatch(trigger -> source.containsProperty(trigger.property())
            || System.getProperty(trigger.property()) != null
            || System.getenv(trigger.environmentVariable()) != null);
    }

    public static ApplicationListener<ApplicationEnvironmentPreparedEvent> initializer(Trigger... triggers) {
        return new EnvironmentPreparer(triggers);
    }

    private static final class EnvironmentPreparer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
        private final Trigger[] triggers;

        private EnvironmentPreparer(Trigger[] triggers) {
            this.triggers = triggers;
        }

        @Override
        public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
            ConfigurableEnvironment environment = event.getEnvironment();

            if (Arrays.stream(triggers).noneMatch(trigger -> environment.containsProperty(trigger.property()))) {
                return;
            }

            environment.getPropertySources().addFirst(new MapPropertySource("panelCliMode", Map.of(
                "spring.main.web-application-type", "none",
                "spring.main.banner-mode", "off",
                "management.otlp.metrics.export.enabled", "false",
                "management.tracing.export.otlp.enabled", "false",
                "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper", "OFF"
            )));
            environment.addActiveProfile(PROFILE);
        }
    }
}
