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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RequestUserInfoProviderTest {
    private final RequestUserInfoProvider provider = new RequestUserInfoProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesUserFromJwtClaims() {
        authenticate(Map.of("sub", "ID", "email", "EMAIL", "name", "NAME"));

        Optional<UserAuthInfo> resolved = provider.resolve();

        assertThat(resolved).contains(new UserAuthInfo("ID", "EMAIL", "NAME"));
    }

    @Test
    void resolvesUserWithoutOptionalClaims() {
        authenticate(Map.of("sub", "ID"));

        Optional<UserAuthInfo> resolved = provider.resolve();

        assertThat(resolved).contains(new UserAuthInfo("ID", null, null));
    }

    @Test
    void returnsEmptyWhenNotAuthenticated() {
        assertThat(provider.resolve()).isEmpty();
    }

    @Test
    void returnsEmptyForAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
            new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
        );

        assertThat(provider.resolve()).isEmpty();
    }

    private static void authenticate(Map<String, Object> claims) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "HS256"), claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES));
    }
}
