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

"use client";

import { useEffect } from "react";
import { app } from "@microsoft/teams-js";
import { initializeTeams } from "@/components/teams/teamsJs";

/** Tells the host the tab has loaded and exposes the Teams theme as `data-teams-theme` on the root element. */
export default function TeamsHost() {
    useEffect(() => {
        let active = true;
        const applyTheme = (theme: string) => {
            document.documentElement.dataset.teamsTheme = theme;
        };

        initializeTeams().then(async inTeams => {
            if (!inTeams || !active) {
                return;
            }
            const context = await app.getContext();
            applyTheme(context.app.theme);
            app.registerOnThemeChangeHandler(applyTheme);
            app.notifySuccess();
        }).catch(() => undefined);

        return () => {
            active = false;
        };
    }, []);

    return null;
}
