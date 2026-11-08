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

package org.mlodzirazem.panel.services.panelapi.api.security;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "panel.api.auth")
public record ServiceTokenProperties(
    @Nullable String secret,
    @Nullable String issuer,
    @Nullable String audience,
    @Nullable Duration clockSkew
) {
    public static final int MIN_SECRET_BYTES = 32;

    public ServiceTokenProperties {
        issuer = issuer == null || issuer.isBlank() ? "panel-web" : issuer;
        audience = audience == null || audience.isBlank() ? "panel-api" : audience;
        clockSkew = clockSkew == null ? Duration.ofSeconds(5) : clockSkew;

        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "panel.api.auth.secret (env PANEL_API_AUTH_SECRET) must be set and be at least "
                    + MIN_SECRET_BYTES + " bytes long"
            );
        }
    }

    public byte[] secretBytes() {
        return Objects.requireNonNull(secret).getBytes(StandardCharsets.UTF_8);
    }
}
