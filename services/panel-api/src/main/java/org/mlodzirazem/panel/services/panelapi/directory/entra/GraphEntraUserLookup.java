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

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.EntraUserLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads accounts from Microsoft Graph with an app-only token (client credentials, {@code User.Read.All}).
 */
@Slf4j
@Component
@EnableConfigurationProperties(EntraProperties.class)
class GraphEntraUserLookup implements EntraUserLookup {
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-fA-F-]{36}$");
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(2);

    private final EntraProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    private @Nullable String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;
    private boolean warnedUnconfigured;

    @Autowired
    GraphEntraUserLookup(EntraProperties properties, Clock clock) {
        this(properties, RestClient.create(), clock);
    }

    GraphEntraUserLookup(EntraProperties properties, RestClient restClient, Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;
    }

    @Override
    public Optional<EntraUser> find(String objectId) {
        if (!properties.configured()) {
            warnUnconfiguredOnce();
            return Optional.empty();
        }

        if (!OBJECT_ID.matcher(objectId).matches()) {
            return Optional.empty();
        }

        Map<String, Object> user;
        try {
            user = restClient.get()
                .uri(properties.graphUrl() + "/v1.0/users/{id}?$select=id,employeeId,accountEnabled", objectId)
                .header("Authorization", "Bearer " + token())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JSON_OBJECT);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw new IllegalStateException("Microsoft Graph rejected the user lookup: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Microsoft Graph is not reachable", e);
        }

        if (user == null) {
            return Optional.empty();
        }

        Object employeeId = user.get("employeeId");
        return Optional.of(new EntraUser(
            objectId,
            employeeId instanceof String s && !s.isBlank() ? s.strip() : null,
            Boolean.TRUE.equals(user.get("accountEnabled"))
        ));
    }

    private synchronized String token() {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", Objects.requireNonNull(properties.clientId()));
        form.add("client_secret", Objects.requireNonNull(properties.clientSecret()));
        form.add("scope", properties.graphUrl() + "/.default");

        Map<String, Object> response;
        try {
            response = restClient.post()
                .uri(properties.authorityUrl() + "/{tenant}/oauth2/v2.0/token", properties.tenantId())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(JSON_OBJECT);
        } catch (RestClientException e) {
            throw new IllegalStateException("Cannot obtain a Microsoft Graph token", e);
        }

        if (response == null || !(response.get("access_token") instanceof String token) || token.isBlank()) {
            throw new IllegalStateException("The token endpoint returned no access token");
        }

        long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 300L;
        accessToken = token;
        accessTokenExpiresAt = now.plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
        return token;
    }

    private void warnUnconfiguredOnce() {
        if (!warnedUnconfigured) {
            warnedUnconfigured = true;
            log.warn("panel.entra is not configured: Entra sign-ins cannot be linked to members");
        }
    }
}
