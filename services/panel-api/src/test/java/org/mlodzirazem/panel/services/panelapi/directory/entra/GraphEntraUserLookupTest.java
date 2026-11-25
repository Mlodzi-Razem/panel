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

import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mlodzirazem.panel.services.panelapi.directory.api.EntraUserLookup.EntraUser;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class GraphEntraUserLookupTest {
    private static final String OID = "7d1f3a2b-0c4e-4f5a-9b8c-112233445566";
    private static final String TOKEN_URL = "https://login.example/tenant-1/oauth2/v2.0/token";
    private static final String USER_URL =
        "https://graph.example/v1.0/users/" + OID + "?$select=id,employeeId,accountEnabled,userPrincipalName";
    private static final String CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GraphEntraUserLookup lookup(EntraProperties properties) {
        return new GraphEntraUserLookup(new EntraGraphAppToken(properties, builder.build(), clock), builder.build());
    }

    private static EntraProperties secretConfigured() {
        return new EntraProperties("tenant-1", "client-1", "secret-1", null, null,
            "https://login.example", "https://graph.example");
    }

    private static EntraProperties certificateConfigured(Fixture fixture) {
        return new EntraProperties("tenant-1", "client-1", null, fixture.certificatePem(), fixture.keyPem(),
            "https://login.example", "https://graph.example");
    }

    private static EntraProperties certificateAndSecretConfigured(Fixture fixture, String clientSecret) {
        return new EntraProperties("tenant-1", "client-1", clientSecret, fixture.certificatePem(), fixture.keyPem(),
            "https://login.example", "https://graph.example");
    }

    @Test
    void returnsEmptyWithoutCallingGraphWhenNotConfigured() {
        EntraProperties properties = new EntraProperties(null, "client-1", null, null, null, null, null);

        assertThat(lookup(properties).find(OID)).isEmpty();
        server.verify();
    }

    @Test
    void readsEmployeeIdWithAnAppOnlyTokenAndReusesTheToken() {
        server.expect(requestTo(TOKEN_URL))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().formDataContains(Map.of(
                  "grant_type", "client_credentials",
                  "client_id", "client-1",
                  "client_secret", "secret-1",
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

        GraphEntraUserLookup lookup = lookup(secretConfigured());

        assertThat(lookup.find(OID)).contains(new EntraUser(OID, "000205", true, null));
        assertThat(lookup.find(OID)).contains(new EntraUser(OID, null, false, null));
        server.verify();
    }

    @Test
    void readsTheUserPrincipalNameForTheSignInCheck() {
        server.expect(requestTo(TOKEN_URL))
              .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
              .andExpect(header("Authorization", "Bearer tok"))
              .andRespond(withSuccess("{\"id\":\"" + OID + "\",\"employeeId\":\"000205\",\"accountEnabled\":true,"
                  + "\"userPrincipalName\":\" 000205@mlodzirazem.org \"}", MediaType.APPLICATION_JSON));

        GraphEntraUserLookup lookup = lookup(secretConfigured());

        assertThat(lookup.find(OID)).contains(new EntraUser(OID, "000205", true, "000205@mlodzirazem.org"));
        server.verify();
    }

    @Test
    void sendsAClientAssertionInsteadOfASecretWithACertificateCredential() throws Exception {
        Fixture fixture = Fixture.generate();
        AtomicReference<String> formBody = new AtomicReference<>();

        server.expect(requestTo(TOKEN_URL))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(recorded(formBody)))
              .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
              .andExpect(header("Authorization", "Bearer tok"))
              .andRespond(withSuccess("{\"id\":\"" + OID + "\",\"employeeId\":\"000205\",\"accountEnabled\":true}",
                  MediaType.APPLICATION_JSON));

        assertThat(lookup(certificateConfigured(fixture)).find(OID)).contains(new EntraUser(OID, "000205", true, null));

        MultiValueMap<String, String> form = form(formBody.get());

        assertThat(form.getFirst("grant_type")).isEqualTo("client_credentials");
        assertThat(form.getFirst("client_id")).isEqualTo("client-1");
        assertThat(form.getFirst("scope")).isEqualTo("https://graph.example/.default");
        assertThat(form.getFirst("client_assertion_type")).isEqualTo(CLIENT_ASSERTION_TYPE);
        assertThat(form).doesNotContainKey("client_secret");

        SignedJWT assertion = SignedJWT.parse(form.getFirst("client_assertion"));

        Map<String, Object> header = assertion.getHeader().toJSONObject();
        assertThat(String.valueOf(header.get("typ"))).isEqualTo("JWT");
        assertThat(String.valueOf(header.get("alg"))).isEqualTo("RS256");
        assertThat(String.valueOf(header.get("x5t"))).isEqualTo(Base64URL.encode(
            MessageDigest.getInstance("SHA-1").digest(fixture.certificate().getEncoded())).toString());

        JWTClaimsSet claims = assertion.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("client-1");
        assertThat(claims.getSubject()).isEqualTo("client-1");
        assertThat(claims.getAudience()).containsExactly(TOKEN_URL);
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(clock.instant());
        assertThat(claims.getNotBeforeTime().toInstant()).isEqualTo(clock.instant());
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
            .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void theCertificateCredentialWinsOverTheClientSecret() throws Exception {
        Fixture fixture = Fixture.generate();
        AtomicReference<String> formBody = new AtomicReference<>();

        server.expect(requestTo(TOKEN_URL))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(recorded(formBody)))
              .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
              .andExpect(header("Authorization", "Bearer tok"))
              .andRespond(withSuccess("{\"id\":\"" + OID + "\",\"employeeId\":\"301\",\"accountEnabled\":true}",
                  MediaType.APPLICATION_JSON));

        EntraProperties properties = certificateAndSecretConfigured(fixture, "secret-1");

        assertThat(lookup(properties).find(OID)).contains(new EntraUser(OID, "301", true, null));

        MultiValueMap<String, String> form = form(formBody.get());

        assertThat(form).doesNotContainKey("client_secret");
        assertThat(form.getFirst("client_assertion_type")).isEqualTo(CLIENT_ASSERTION_TYPE);
    }

    @Test
    void warnsAndStaysUnconfiguredWithAnIncompleteCertificateCredential(CapturedOutput output) throws Exception {
        Fixture fixture = Fixture.generate();

        EntraProperties certificateOnly = new EntraProperties("tenant-1", "client-1", null,
            fixture.certificatePem(), null, "https://login.example", "https://graph.example");
        EntraProperties keyOnly = new EntraProperties("tenant-1", "client-1", null,
            null, fixture.keyPem(), "https://login.example", "https://graph.example");

        assertThat(lookup(certificateOnly).find(OID)).isEmpty();
        assertThat(lookup(keyOnly).find(OID)).isEmpty();

        assertThat(output.getAll())
            .contains("without panel.entra.client-certificate-key;")
            .contains("without panel.entra.client-certificate;");
        assertThat(output.getAll()).doesNotContain("changeit");
        server.verify();
    }

    @Test
    void treatsUnknownAccountsAsEmptyAndRejectsMalformedIds() {
        server.expect(requestTo(TOKEN_URL))
              .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        GraphEntraUserLookup lookup = lookup(secretConfigured());

        assertThat(lookup.find(OID)).isEmpty();
        assertThat(lookup.find("../me")).isEmpty();
        server.verify();
    }

    @Test
    void failsLoudlyWhenGraphRefusesAccess() {
        server.expect(requestTo(TOKEN_URL))
              .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> lookup(secretConfigured()).find(OID)).isInstanceOf(IllegalStateException.class);
    }

    private static MultiValueMap<String, String> form(String formBody) {
        try {
            return new FormHttpMessageConverter()
                .read(null, new MockHttpInputMessage(formBody.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Matcher<String> recorded(AtomicReference<String> sink) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(String item) {
                sink.set(item);
                return true;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("the serialized form body");
            }
        };
    }

    /**
     * A throwaway self-signed RSA credential minted at test runtime with the JDK's bundled {@code keytool}: the tree
     * carries no BouncyCastle, and this way no test dependency is added. EC keys flow identically, signing ES256.
     */
    private record Fixture(String certificatePem, String keyPem, X509Certificate certificate) {
        private static Fixture generate() throws Exception {
            Path keyStorePath = Files.createTempFile("panel-entra-test", ".p12");
            Files.delete(keyStorePath);

            Process keyTool = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "panel-entra-test",
                "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12",
                "-keystore", keyStorePath.toString(),
                "-storepass", "changeit",
                "-dname", "CN=panel-entra-test")
                .start();

            if (keyTool.waitFor() != 0) {
                throw new IllegalStateException("keytool failed: "
                    + new String(keyTool.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            }

            KeyStore store = KeyStore.getInstance("PKCS12");
            try (var in = Files.newInputStream(keyStorePath)) {
                store.load(in, "changeit".toCharArray());
            } finally {
                Files.deleteIfExists(keyStorePath);
            }

            X509Certificate certificate = (X509Certificate) store.getCertificate("panel-entra-test");
            PrivateKey privateKey = (PrivateKey) store.getKey("panel-entra-test", "changeit".toCharArray());

            return new Fixture(
                pem("CERTIFICATE", certificate.getEncoded()),
                pem("PRIVATE KEY", privateKey.getEncoded()),
                certificate);
        }

        private static String pem(String type, byte[] der) {
            return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END " + type + "-----\n";
        }
    }
}
