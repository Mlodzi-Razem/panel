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

package org.mlodzirazem.panel.services.panelapi.api;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfoResolver;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "management.server.port=0",
        "spring.config.import=file:src/main/resources/application.yml"
    }
)
@PostgresTest
@Import(ServiceAuthenticationTest.TestController.class)
class ServiceAuthenticationTest {
    @Value("${panel.api.auth.secret}")
    String secret;

    @LocalServerPort
    int serverPort;

    @LocalManagementPort
    int managementPort;

    private final RestClient client = RestClient.builder()
        .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
        .build();

    @Test
    void rejectsRequestWithoutToken() {
        var response = get(serverPort, "/auth-test", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body()).contains("\"status\":401").doesNotContain("Exception");
    }

    @Test
    void acceptsValidTokenAndResolvesUserFromClaims() throws Exception {
        var response = get(serverPort, "/auth-test", bearer(token("panel-web", "panel-api", secret, 60)));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("UserAuthInfo[userId=SUB, email=user@example.com, name=Jan Kowalski]");
    }

    @Test
    void rejectsWrongSignature() throws Exception {
        String otherSecret = "another-secret-another-secret-another-secret";

        var response = get(serverPort, "/auth-test", bearer(token("panel-web", "panel-api", otherSecret, 60)));

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        var response = get(serverPort, "/auth-test", bearer(token("panel-web", "something-else", secret, 60)));

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        var response = get(serverPort, "/auth-test", bearer(token("evil", "panel-api", secret, 60)));

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        var response = get(serverPort, "/auth-test", bearer(token("panel-web", "panel-api", secret, -300)));

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void rejectsLegacyUserHeadersWithoutToken() {
        var response = client.get()
            .uri("http://localhost:" + serverPort + "/auth-test")
            .header("X-Auth-User-Id", "SUB")
            .header("X-Auth-User-Email", "user@example.com")
            .header("X-Auth-User-Name", "Jan Kowalski")
            .retrieve()
            .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void managementPortExposesHealthOnly() {
        assertThat(get(managementPort, "/actuator/health", null).status()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health", null).body()).doesNotContain("components");
        assertThat(get(managementPort, "/actuator/prometheus", null).status()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/heapdump", null).status()).isIn(401, 403, 404);
        assertThat(get(managementPort, "/actuator/env", null).status()).isIn(401, 403, 404);
        assertThat(get(managementPort, "/actuator/loggers", null).status()).isIn(401, 403, 404);
    }

    @Test
    void mainPortDoesNotServeActuatorWithoutToken() {
        assertThat(get(serverPort, "/actuator/health", null).status()).isEqualTo(401);
    }

    private Response get(int port, String path, String authorization) {
        var spec = client.get().uri("http://localhost:" + port + path);
        if (authorization != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        var entity = spec.retrieve().toEntity(String.class);
        return new Response(entity.getStatusCode().value(), Objects.requireNonNullElse(entity.getBody(), ""));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String token(String issuer, String audience, String secret, long ttlSeconds) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("SUB")
            .claim("email", "user@example.com")
            .claim("name", "Jan Kowalski")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
            .jwtID(UUID.randomUUID().toString())
            .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private record Response(int status, String body) {
    }

    @RestController
    @RequiredArgsConstructor
    static class TestController {
        private final UserAuthInfoResolver resolver;

        @GetMapping("/auth-test")
        String test() {
            return resolver.resolve().map(Object::toString).orElse("");
        }
    }
}
