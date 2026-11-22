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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The shared app token as seen by callers; the credential mechanics (secret, certificate assertion) are covered
 * through {@link GraphEntraUserLookupTest}.
 */
class EntraGraphAppTokenTest {
    private static final String TOKEN_URL = "https://login.example/tenant-1/oauth2/v2.0/token";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-22T12:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private EntraGraphAppToken token(EntraProperties properties) {
        return new EntraGraphAppToken(properties, builder.build(), clock);
    }

    private static EntraProperties configured() {
        return new EntraProperties("tenant-1", "client-1", "secret-1", null, null,
            "https://login.example", "https://graph.example");
    }

    @Test
    void reportsConfigurationAndGraphUrl() {
        assertThat(token(configured()).configured()).isTrue();
        assertThat(token(configured()).graphUrl()).isEqualTo("https://graph.example");
        assertThat(token(new EntraProperties(null, "client-1", null, null, null, null, null)).configured()).isFalse();
        assertThat(token(new EntraProperties(null, null, null, null, null, null, null)).graphUrl())
            .isEqualTo("https://graph.microsoft.com");
    }

    @Test
    void cachesTheTokenUntilTwoMinutesBeforeExpiry() {
        server.expect(once(), requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess("{\"access_token\":\"one\",\"expires_in\":600}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URL))
              .andRespond(withSuccess("{\"access_token\":\"two\",\"expires_in\":600}", MediaType.APPLICATION_JSON));

        EntraGraphAppToken token = token(configured());

        assertThat(token.bearer()).isEqualTo("one");
        now.set(now.get().plus(Duration.ofMinutes(7)));
        assertThat(token.bearer()).isEqualTo("one");
        now.set(now.get().plus(Duration.ofMinutes(2)));
        assertThat(token.bearer()).isEqualTo("two");
        server.verify();
    }

    @Test
    void failsWithIllegalStateWhenTheTokenEndpointRefuses() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> token(configured()).bearer()).isInstanceOf(IllegalStateException.class);
    }
}
