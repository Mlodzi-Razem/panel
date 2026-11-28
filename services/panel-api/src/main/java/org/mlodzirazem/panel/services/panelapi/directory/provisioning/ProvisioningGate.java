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

package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import lombok.extern.slf4j.Slf4j;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Whether provisioning may call Microsoft Graph right now: the switch is on, Graph is configured and (SEC-2) the
 * application token does not carry a tenant-wide permission that SEC-1 forbids. With the switch off nothing is asked of
 * Graph, not even a token; with the switch on the token is read once and the answer is cached for a few minutes.
 *
 * <p>The check decodes the {@code roles} claim of the app-only token without verifying its signature: the claim is only
 * read to refuse to start, never to authorise anything. A token that cannot be read counts as "not enabled". Only the
 * <em>names</em> of the forbidden permissions are logged, never the token.</p>
 */
@Slf4j
@Component
class ProvisioningGate {
    private static final Duration OK_FOR = Duration.ofMinutes(5);
    private static final Duration NOT_OK_FOR = Duration.ofMinutes(1);

    private final ProvisioningProperties properties;
    private final GraphAppToken token;
    private final JsonMapper json;
    private final Clock clock;

    private boolean lastAnswer;
    private Instant validUntil = Instant.EPOCH;

    ProvisioningGate(ProvisioningProperties properties, GraphAppToken token, JsonMapper json, Clock clock) {
        this.properties = properties;
        this.token = token;
        this.json = json;
        this.clock = clock;
    }

    synchronized boolean enabled() {
        if (!properties.enabled() || !token.configured()) {
            return false;
        }

        Instant now = clock.instant();

        if (now.isBefore(validUntil)) {
            return lastAnswer;
        }

        lastAnswer = evaluate();
        validUntil = now.plus(lastAnswer ? OK_FOR : NOT_OK_FOR);
        return lastAnswer;
    }

    private boolean evaluate() {
        String bearer;

        try {
            bearer = token.bearer();
        } catch (RuntimeException e) {
            log.warn("provisioning is not enabled: the Graph token cannot be obtained ({})", e.getClass().getSimpleName());
            return false;
        }

        Set<String> forbidden = forbiddenRoles(bearer);

        if (forbidden == null) {
            log.error("provisioning is not enabled: GRAPH_TOKEN_UNREADABLE");
            return false;
        }

        if (!forbidden.isEmpty()) {
            log.error("provisioning is not enabled: GRAPH_PERMISSIONS_TOO_BROAD, the application holds {}", forbidden);
            return false;
        }

        return true;
    }

    /**
     * The forbidden roles of the token, empty when there are none, or null when the token cannot be read.
     */
    Set<String> forbiddenRoles(String jwt) {
        try {
            String[] parts = jwt.split("\\.");

            if (parts.length < 2) {
                return null;
            }

            Map<?, ?> claims = json.readValue(Base64.getUrlDecoder().decode(parts[1].getBytes(StandardCharsets.US_ASCII)), Map.class);
            Set<String> found = new TreeSet<>();

            if (claims.get("roles") instanceof List<?> roles) {
                for (Object role : roles) {
                    if (role instanceof String name && (properties.forbiddenPermissions().contains(name) || name.startsWith("RoleManagement."))) {
                        found.add(name);
                    }

                    // O2: Mail.Send shares this token with the mail sender; accepted for now, but never silently
                    if ("Mail.Send".equals(role)) {
                        log.warn("provisioning token also holds Mail.Send (shared with the mail sender); see checklist E1");
                    }
                }
            }

            return found;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
