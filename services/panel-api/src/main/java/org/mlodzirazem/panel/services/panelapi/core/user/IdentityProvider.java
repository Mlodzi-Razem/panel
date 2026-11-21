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

package org.mlodzirazem.panel.services.panelapi.core.user;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * The identity provider that signed the user in to panel-web. The subject of {@link UserAuthInfo} is only unique
 * within one provider: the Google {@code sub} or the Entra object ID ({@code oid}).
 */
public enum IdentityProvider {
    GOOGLE,
    ENTRA;

    /**
     * Reads the {@code idp} claim of a service token. A missing claim means {@link #GOOGLE}, because tokens minted
     * before Entra sign-in existed carry no claim; an unknown value yields empty.
     */
    public static Optional<IdentityProvider> fromClaim(@Nullable String claim) {
        if (claim == null || claim.isBlank()) {
            return Optional.of(GOOGLE);
        }

        return switch (claim.strip().toLowerCase(Locale.ROOT)) {
            case "google" -> Optional.of(GOOGLE);
            case "entra" -> Optional.of(ENTRA);
            default -> Optional.empty();
        };
    }
}
