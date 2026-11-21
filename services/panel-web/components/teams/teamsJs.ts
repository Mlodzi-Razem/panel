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

import { app } from "@microsoft/teams-js";

const INITIALIZE_TIMEOUT_MS = 4000;

let initialization: Promise<boolean> | undefined;

/**
 * Initializes TeamsJS once per page. Resolves false outside a Microsoft 365 host (a plain browser tab),
 * where TeamsJS would otherwise wait for a host that never answers.
 */
export function initializeTeams(): Promise<boolean> {
    if (!initialization) {
        initialization = window.parent === window
            ? Promise.resolve(false)
            : Promise.race([
                app.initialize().then(() => true, () => false),
                new Promise<boolean>(resolve => setTimeout(() => resolve(false), INITIALIZE_TIMEOUT_MS)),
            ]);
    }
    return initialization;
}
