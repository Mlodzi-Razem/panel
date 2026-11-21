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

package org.mlodzirazem.panel.services.panelapi.api.v1;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0"
)
@PostgresTest
public abstract class ApiTestSupport extends DomainTestSupport {
    @Value("${panel.api.auth.secret}")
    String secret;

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    private final RestClient client = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory())
        .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
        .build();

    public record Res(int status, JsonNode body) {
        public String code() {
            return body.path("code").asString();
        }
    }

    protected TMember linked(TMember member) {
        jdbc.update("INSERT INTO member_identities (member_id, provider, subject) VALUES (?, 'GOOGLE', ?)",
            member.getId(), sub(member));
        return member;
    }

    protected static String sub(TMember member) {
        return "sub-" + member.getId();
    }

    protected Res get(TMember as, String path) {
        return call(HttpMethod.GET, path, token(sub(as), as.getDomainEmail()), null);
    }

    protected Res post(TMember as, String path, Object body) {
        return call(HttpMethod.POST, path, token(sub(as), as.getDomainEmail()), body);
    }

    protected Res patch(TMember as, String path, Object body) {
        return call(HttpMethod.PATCH, path, token(sub(as), as.getDomainEmail()), body);
    }

    protected Res delete(TMember as, String path) {
        return call(HttpMethod.DELETE, path, token(sub(as), as.getDomainEmail()), null);
    }

    protected Res call(HttpMethod method, String path, String token, Object body) {
        var spec = client.method(method).uri("http://localhost:" + port + path);

        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
        }

        var entity = spec.retrieve().toEntity(String.class);
        String text = entity.getBody();

        return new Res(entity.getStatusCode().value(),
            text == null || text.isBlank() ? json.createObjectNode() : json.readTree(text));
    }

    protected static Map<String, Object> fields(Object... keysAndValues) {
        Map<String, Object> fields = new LinkedHashMap<>();

        for (int i = 0; i < keysAndValues.length; i += 2) {
            fields.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }

        return Map.of("fields", fields);
    }

    protected String token(String sub, String email) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("panel-web")
                .audience("panel-api")
                .subject(sub)
                .claim("email", email)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString())
                .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
