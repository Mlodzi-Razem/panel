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

package org.mlodzirazem.panel.services.panelapi.directory.api;

/**
 * The Panel MR app registration's app-only access to Microsoft Graph, shared by every Graph caller. Which
 * Graph permissions the token carries is decided on the app registration (for example {@code User.Read.All},
 * {@code Mail.Send}); callers must tolerate Graph answering 403 for a permission that was not granted.
 */
public interface GraphAppToken {
    /**
     * Tenant, client and a complete credential are configured. Without it {@link #bearer()} must not be called.
     */
    boolean configured();

    /**
     * Base URL of Microsoft Graph without a trailing path, for example {@code https://graph.microsoft.com}.
     */
    String graphUrl();

    /**
     * A valid access token, cached until shortly before it expires.
     *
     * @throws IllegalStateException when the token cannot be obtained
     */
    String bearer();
}
