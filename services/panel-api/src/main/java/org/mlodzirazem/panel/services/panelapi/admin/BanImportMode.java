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

import org.mlodzirazem.panel.services.panelapi.core.cli.CliMode;

public final class BanImportMode {
    public static final String FILE_PROPERTY = "panel.bans.file";
    public static final CliMode.Trigger TRIGGER = new CliMode.Trigger(FILE_PROPERTY, "PANEL_BAN_FILE");
    public static final int EXIT_OK = CliMode.EXIT_OK;
    public static final int EXIT_PROBLEMS = CliMode.EXIT_PROBLEMS;
    public static final int EXIT_FATAL = CliMode.EXIT_FATAL;

    private BanImportMode() {
    }
}
