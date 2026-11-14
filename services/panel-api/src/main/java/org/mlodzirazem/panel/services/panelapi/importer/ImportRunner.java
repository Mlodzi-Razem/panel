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

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.PrintStream;
import java.nio.file.Path;

@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "panel.import", name = "autorun", havingValue = "true", matchIfMissing = true)
class ImportRunner implements ApplicationRunner, ExitCodeGenerator {
    private final ImportProperties properties;
    private final SpreadsheetImporter importer;

    private int exitCode = ImportMode.EXIT_FATAL;

    @Override
    public void run(ApplicationArguments args) {
        PrintStream out = System.out;

        try {
            ImportReport report = importer.run(
                Path.of(String.valueOf(properties.file())),
                new ImportOptions(properties.apply(), properties.skipInvalidRows())
            );

            out.print(report.render());
            exitCode = report.exitCode();
        } catch (ImportFatalException ex) {
            out.println("FATAL: " + ex.getMessage());
            exitCode = ImportMode.EXIT_FATAL;
        } catch (RuntimeException ex) {
            out.println("FATAL: import failed and nothing was written (" + ex.getClass().getSimpleName() + ")");
            exitCode = ImportMode.EXIT_FATAL;
        }

        out.flush();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
