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

package org.mlodzirazem.panel.services.panelapi.api.filter;

import org.mlodzirazem.panel.services.panelapi.core.user.IdentityProvider;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RequestUserInfoProvider {
    /**
     * Service-token claim naming the identity provider that signed the user in ({@code google} or {@code entra}).
     */
    public static final String IDENTITY_PROVIDER_CLAIM = "idp";

    public Optional<UserAuthInfo> resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }

        return IdentityProvider.fromClaim(jwt.getClaimAsString(IDENTITY_PROVIDER_CLAIM))
            .map(provider -> new UserAuthInfo(
                userId,
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                provider
            ));
    }
}
