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

package org.mlodzirazem.panel.services.panelapi.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.mlodzirazem.panel.services.panelapi.core.cli.CliMode;

import java.io.PrintStream;

@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "panel.admin", name = "autorun", havingValue = "true", matchIfMissing = true)
class AdminRunner implements ApplicationRunner, ExitCodeGenerator {
    private final AdminProperties properties;
    private final BreakGlassAssigner assigner;

    private int exitCode = CliMode.EXIT_FATAL;

    @Override
    public void run(ApplicationArguments args) {
        PrintStream out = System.out;

        try {
            BreakGlassReport report = assigner.run(
                properties.assignFunction(),
                properties.member(),
                properties.operator(),
                properties.reason(),
                properties.decisionReference(),
                properties.apply()
            );

            out.print(report.render());
            exitCode = report.exitCode();
        } catch (RuntimeException ex) {
            out.println("FATAL: break-glass failed and nothing was written (" + ex.getClass().getSimpleName() + ")");
            exitCode = CliMode.EXIT_FATAL;
        }

        out.flush();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
