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
import org.mlodzirazem.panel.services.panelapi.directory.api.EntraUserLookup;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads accounts from Microsoft Graph with the app-only token of {@link GraphAppToken}
 * ({@code User.Read.All}). How the token is obtained (client secret or certificate assertion) is described on
 * {@link EntraGraphAppToken}.
 */
@Slf4j
@Component
class GraphEntraUserLookup implements EntraUserLookup {
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final GraphAppToken token;
    private final RestClient restClient;

    private boolean warnedUnconfigured;

    @Autowired
    GraphEntraUserLookup(GraphAppToken token) {
        this(token, RestClient.create());
    }

    GraphEntraUserLookup(GraphAppToken token, RestClient restClient) {
        this.token = token;
        this.restClient = restClient;
    }

    @Override
    public Optional<EntraUser> find(String objectId) {
        if (!token.configured()) {
            warnUnconfiguredOnce();
            return Optional.empty();
        }

        if (!OBJECT_ID.matcher(objectId).matches()) {
            return Optional.empty();
        }

        Map<String, Object> user;
        try {
            user = restClient.get()
                .uri(token.graphUrl() + "/v1.0/users/{id}?$select=id,employeeId,accountEnabled", objectId)
                .header("Authorization", "Bearer " + token.bearer())
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

    private void warnUnconfiguredOnce() {
        if (!warnedUnconfigured) {
            warnedUnconfigured = true;
            log.warn("panel.entra is not configured: Entra sign-ins cannot be linked to members");
        }
    }
}
