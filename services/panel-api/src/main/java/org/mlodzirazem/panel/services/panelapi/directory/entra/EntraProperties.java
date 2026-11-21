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

package org.mlodzirazem.panel.services.panelapi.directory.entra;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access to Microsoft Graph with the client credentials of the Panel MR app registration
 * (env {@code PANEL_ENTRA_TENANT_ID}, {@code PANEL_ENTRA_CLIENT_ID}, {@code PANEL_ENTRA_CLIENT_SECRET}).
 * Without all three, Entra lookups return empty and Entra sign-ins stay unlinked.
 */
@ConfigurationProperties("panel.entra")
public record EntraProperties(
    @Nullable String tenantId,
    @Nullable String clientId,
    @Nullable String clientSecret,
    @Nullable String authorityUrl,
    @Nullable String graphUrl
) {
    public EntraProperties {
        authorityUrl = blank(authorityUrl) ? "https://login.microsoftonline.com" : authorityUrl;
        graphUrl = blank(graphUrl) ? "https://graph.microsoft.com" : graphUrl;
    }

    public boolean configured() {
        return !blank(tenantId) && !blank(clientId) && !blank(clientSecret);
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
