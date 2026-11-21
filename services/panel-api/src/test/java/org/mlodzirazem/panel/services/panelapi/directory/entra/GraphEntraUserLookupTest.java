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
import org.mlodzirazem.panel.services.panelapi.directory.api.EntraUserLookup.EntraUser;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphEntraUserLookupTest {
    private static final String OID = "7d1f3a2b-0c4e-4f5a-9b8c-112233445566";
    private static final String TOKEN_URL = "https://login.example/tenant-1/oauth2/v2.0/token";
    private static final String USER_URL =
        "https://graph.example/v1.0/users/" + OID + "?$select=id,employeeId,accountEnabled";

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GraphEntraUserLookup lookup(EntraProperties properties) {
        return new GraphEntraUserLookup(properties, builder.build(), clock);
    }

    private static EntraProperties configured() {
        return new EntraProperties("tenant-1", "client-1", "secret-1", "https://login.example", "https://graph.example");
    }

    @Test
    void returnsEmptyWithoutCallingGraphWhenNotConfigured() {
        EntraProperties properties = new EntraProperties(null, "client-1", null, null, null);

        assertThat(lookup(properties).find(OID)).isEmpty();
        server.verify();
    }

    @Test
    void readsEmployeeIdWithAnAppOnlyTokenAndReusesTheToken() {
        server.expect(requestTo(TOKEN_URL))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().formDataContains(java.util.Map.of(
                  "grant_type", "client_credentials",
                  "client_id", "client-1",
                  "scope", "https://graph.example/.default")))
              .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
              .andExpect(header("Authorization", "Bearer tok"))
              .andRespond(withSuccess("{\"id\":\"" + OID + "\",\"employeeId\":\" 000205 \",\"accountEnabled\":true}",
                  MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
              .andExpect(header("Authorization", "Bearer tok"))
              .andRespond(withSuccess("{\"id\":\"" + OID + "\",\"employeeId\":null,\"accountEnabled\":false}",
                  MediaType.APPLICATION_JSON));

        GraphEntraUserLookup lookup = lookup(configured());

        assertThat(lookup.find(OID)).contains(new EntraUser(OID, "000205", true));
        assertThat(lookup.find(OID)).contains(new EntraUser(OID, null, false));
        server.verify();
    }

    @Test
    void treatsUnknownAccountsAsEmptyAndRejectsMalformedIds() {
        server.expect(requestTo(TOKEN_URL))
              .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        GraphEntraUserLookup lookup = lookup(configured());

        assertThat(lookup.find(OID)).isEmpty();
        assertThat(lookup.find("../me")).isEmpty();
        server.verify();
    }

    @Test
    void failsLoudlyWhenGraphRefusesAccess() {
        server.expect(requestTo(TOKEN_URL))
              .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> lookup(configured()).find(OID)).isInstanceOf(IllegalStateException.class);
    }
}
