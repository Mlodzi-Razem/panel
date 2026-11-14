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

package org.mlodzirazem.panel.services.panelapi;

import org.springframework.boot.SpringApplication;
import org.mlodzirazem.panel.services.panelapi.importer.ImportMode;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
@EnableResilientMethods
public class PanelApiApplication {
    public static void main(String[] args) {
        setTimezoneToUtc();

        SpringApplication application = new SpringApplication(PanelApiApplication.class);

        if (!ImportMode.requestedBy(args)) {
            application.run(args);
            return;
        }

        application.addListeners(new ImportMode.Initializer());

        int exitCode;

        try {
            ConfigurableApplicationContext context = application.run(args);
            exitCode = SpringApplication.exit(context);
        } catch (RuntimeException | Error ex) {
            System.err.println("FATAL: import could not start (" + ex.getClass().getSimpleName() + ")");
            exitCode = ImportMode.EXIT_FATAL;
        }

        System.exit(exitCode);
    }

    private static void setTimezoneToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
