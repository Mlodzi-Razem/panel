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

package org.mlodzirazem.panel.services.panelapi.importer;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;

import java.util.Map;

public final class ImportMode {
    public static final String FILE_PROPERTY = "panel.import.file";
    public static final String PROFILE = "import";
    public static final int EXIT_OK = 0;
    public static final int EXIT_PROBLEMS = 1;
    public static final int EXIT_FATAL = 2;

    private ImportMode() {
    }

    public static boolean requestedBy(String[] args) {
        CommandLinePropertySource<?> source = new SimpleCommandLinePropertySource(args);

        return source.containsProperty(FILE_PROPERTY)
            || System.getProperty(FILE_PROPERTY) != null
            || System.getenv("PANEL_IMPORT_FILE") != null;
    }

    public static final class Initializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
        @Override
        public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
            ConfigurableEnvironment environment = event.getEnvironment();

            if (!environment.containsProperty(FILE_PROPERTY)) {
                return;
            }

            environment.getPropertySources().addFirst(new MapPropertySource("panelImportMode", Map.of(
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
