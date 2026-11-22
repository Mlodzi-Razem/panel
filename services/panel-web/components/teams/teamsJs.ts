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
 *
 * The Teams desktop and web clients embed tabs in an iframe, but the mobile clients load them frameless:
 * the page is the top-level document of a native WebView, and TeamsJS reaches the host through
 * the {@code nativeInterface} bridge the client injects on {@code window}. A top-level window without
 * that bridge is a plain browser tab; with it, it is a Microsoft 365 host.
 */
export function initializeTeams(): Promise<boolean> {
    if (!initialization) {
        const topLevel = window.parent === window;
        const nativeBridge = typeof (window as { nativeInterface?: unknown }).nativeInterface !== "undefined";
        initialization = topLevel && !nativeBridge
            ? Promise.resolve(false)
            : Promise.race([
                app.initialize().then(() => true, () => false),
                new Promise<boolean>(resolve => setTimeout(() => resolve(false), INITIALIZE_TIMEOUT_MS)),
            ]);
    }
    return initialization;
}
