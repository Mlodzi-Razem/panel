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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The Panel MR app's app-only Microsoft Graph token (client credentials), shared by every Graph caller
 * (user lookup, mail). Permissions are granted on the app registration, not here.
 *
 * The token request authenticates the app with whichever credential {@link EntraProperties} carries: a client
 * secret ({@code client_secret}), or a certificate credential ({@code panel.entra.client-certificate} +
 * {@code panel.entra.client-certificate-key}, env {@code PANEL_ENTRA_CLIENT_CERTIFICATE} /
 * {@code PANEL_ENTRA_CLIENT_CERTIFICATE_KEY}), which wins when both are present. A certificate sends an OAuth 2.0
 * client assertion instead of the secret (RFC 7523): {@code client_assertion_type=
 * urn:ietf:params:oauth:client-assertion-type:jwt-bearer} plus a fresh five-minute JWT per token request, signed
 * RS256 with an RSA key or ES256 with a P-256 EC key. Its header carries {@code typ=JWT} and {@code x5t}, the
 * base64url SHA-1 thumbprint Microsoft matches against the app registration's certificate; its claims are
 * {@code iss} and {@code sub} of the client id, an {@code aud} of the tenant's v2.0 token endpoint and a new
 * {@code jti}.
 */
@Slf4j
@Component
@EnableConfigurationProperties(EntraProperties.class)
class EntraGraphAppToken implements GraphAppToken {
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final String CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final Duration ASSERTION_LIFETIME = Duration.ofMinutes(5);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(2);

    private final EntraProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    private @Nullable String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    @Autowired
    EntraGraphAppToken(EntraProperties properties, Clock clock) {
        this(properties, RestClient.create(), clock);
    }

    EntraGraphAppToken(EntraProperties properties, RestClient restClient, Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;

        properties.missingCertificateCredentialKey()
                  .ifPresent(key -> log.warn("panel.entra has a certificate credential without {}; "
                      + "it is ignored and Entra access stays unconfigured", key));
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public String graphUrl() {
        return Objects.requireNonNull(properties.graphUrl());
    }

    @Override
    public synchronized String bearer() {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(accessTokenExpiresAt)) {
            return accessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", Objects.requireNonNull(properties.clientId()));
        form.add("scope", properties.graphUrl() + "/.default");

        if (properties.usesCertificateCredential()) {
            form.add("client_assertion_type", CLIENT_ASSERTION_TYPE);
            form.add("client_assertion", clientAssertion(now));
        } else {
            form.add("client_secret", Objects.requireNonNull(properties.clientSecret()));
        }

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
            // No cause and no response body: the token endpoint quotes the request in error_description (W5, SEC-51).
            String status = e instanceof RestClientResponseException rejection
                ? String.valueOf(rejection.getStatusCode())
                : "no response";
            log.warn("Cannot obtain a Microsoft Graph token: {} {}", e.getClass().getSimpleName(), status);
            throw new IllegalStateException("Cannot obtain a Microsoft Graph token: " + status);
        }

        if (response == null || !(response.get("access_token") instanceof String token) || token.isBlank()) {
            throw new IllegalStateException("The token endpoint returned no access token");
        }

        long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 300L;
        accessToken = token;
        accessTokenExpiresAt = now.plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
        return token;
    }

    /**
     * A one-time OAuth 2.0 client assertion (RFC 7523) for the tenant's token endpoint, signed with the
     * configured certificate credential: RSA keys sign RS256, EC (P-256) keys sign ES256. Generated fresh for
     * every token request and never cached.
     */
    private String clientAssertion(Instant now) {
        try {
            X509Certificate certificate = certificate(properties.clientCertificate());
            PrivateKey key = privateKey(properties.clientCertificateKey());

            JWSSigner signer;
            JWSAlgorithm algorithm;

            if (key instanceof ECPrivateKey ecKey) {
                algorithm = JWSAlgorithm.ES256;
                signer = new ECDSASigner(ecKey);
            } else if (key instanceof RSAPrivateKey) {
                algorithm = JWSAlgorithm.RS256;
                signer = new RSASSASigner(key);
            } else {
                throw new IllegalStateException("Unsupported private key type: " + key.getAlgorithm());
            }

            SignedJWT assertion = new SignedJWT(
                jwsHeader(algorithm, certificate),
                new JWTClaimsSet.Builder()
                    .issuer(properties.clientId())
                    .subject(properties.clientId())
                    .audience(properties.authorityUrl() + "/" + properties.tenantId() + "/oauth2/v2.0/token")
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(ASSERTION_LIFETIME)))
                    .jwtID(UUID.randomUUID().toString())
                    .build());
            assertion.sign(signer);

            return assertion.serialize();
        } catch (GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException(
                "Cannot build the Microsoft Graph client assertion from the configured certificate credential", e);
        }
    }

    /**
     * {@code {"typ":"JWT","alg":…,"x5t":…}} with {@code x5t} the base64url SHA-1 thumbprint — the RFC 7515 form
     * Entra's token endpoint matches against the registered certificate, which nimbus deprecated in favour of
     * {@code x5t#S256} that Entra does not accept here.
     */
    @SuppressWarnings("deprecation")
    private static JWSHeader jwsHeader(JWSAlgorithm algorithm, X509Certificate certificate)
        throws NoSuchAlgorithmException, CertificateException {

        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(certificate.getEncoded());
        return new JWSHeader.Builder(algorithm)
            .type(JOSEObjectType.JWT)
            .x509CertThumbprint(Base64URL.encode(sha1))
            .build();
    }

    private static X509Certificate certificate(String pem) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pemBody(pem, "CERTIFICATE")));
        } catch (CertificateException e) {
            throw new IllegalArgumentException("panel.entra.client-certificate is not a readable X.509 PEM certificate", e);
        }
    }

    private static PrivateKey privateKey(String pem) {
        byte[] encoded = pemBody(pem, "PRIVATE KEY");

        for (String algorithm : List.of("RSA", "EC")) {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);

            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec);
            } catch (InvalidKeySpecException e) {
                // try the other key family
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        throw new IllegalArgumentException("panel.entra.client-certificate-key is not a readable PKCS#8 PEM key");
    }

    private static byte[] pemBody(String pem, String type) {
        String body = pem.replaceAll("(?s)-----BEGIN " + type + "-----|-----END " + type + "-----", "")
                         .replaceAll("\s", "");
        return Base64.getMimeDecoder().decode(body);
    }
}
