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

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Optional;

/**
 * Access to Microsoft Graph with the client credentials of the Panel MR app registration. The tenant's app
 * registration may authenticate with either credential:
 *
 * <ul>
 *     <li><b>Client secret</b> — {@code panel.entra.client-secret}: env {@code PANEL_ENTRA_CLIENT_SECRET}
 *     (Spring relaxed binding, as before);</li>
 *     <li><b>Certificate</b> — {@code panel.entra.client-certificate} and {@code panel.entra.client-certificate-key}:
 *     the X.509 certificate and its private key, each as a PEM string (the key in PKCS#8). The env names are
 *     {@code PANEL_ENTRA_CLIENT_CERTIFICATE} and {@code PANEL_ENTRA_CLIENT_CERTIFICATE_KEY}, wired through the
 *     placeholders in {@code panel.yml} (binders that read the property files directly can also use the kebab
 *     or camel-case {@code panel.entra.client-certificate} forms).</li>
 * </ul>
 *
 * Both credential options need {@code panel.entra.tenant-id} and {@code panel.entra.client-id} (env
 * {@code PANEL_ENTRA_TENANT_ID}, {@code PANEL_ENTRA_CLIENT_ID}). A complete certificate credential takes
 * precedence over the client secret. A certificate with only one of its halves configured is incomplete: it is
 * ignored with one warning at startup (never values) and Entra access stays unconfigured, as it does when no
 * credential is present — Entra lookups then return empty. A malformed PEM fails the lookup that uses it, not
 * the application startup.
 *
 * Without all of tenant, client and one complete credential, Entra lookups return empty and Entra sign-ins stay
 * unlinked.
 */
@ConfigurationProperties("panel.entra")
public record EntraProperties(
    @Nullable String tenantId,
    @Nullable String clientId,
    @Nullable String clientSecret,
    @Nullable String clientCertificate,
    @Nullable String clientCertificateKey,
    @Nullable String authorityUrl,
    @Nullable String graphUrl
) {
    public EntraProperties {
        authorityUrl = blank(authorityUrl) ? "https://login.microsoftonline.com" : authorityUrl;
        graphUrl = blank(graphUrl) ? "https://graph.microsoft.com" : graphUrl;
    }

    public boolean configured() {
        return !blank(tenantId) && !blank(clientId) && (usesSecretCredential() || usesCertificateCredential());
    }

    /**
     * The client secret authenticates the token request; it is ignored when a complete certificate credential is
     * present.
     */
    public boolean usesSecretCredential() {
        return !blank(clientSecret);
    }

    /**
     * The certificate credential (PEM certificate + PKCS#8 PEM key with {@link #clientCertificateKey()}) signs an
     * OAuth 2.0 client assertion instead; it takes precedence over the client secret.
     */
    public boolean usesCertificateCredential() {
        return !blank(clientCertificate) && !blank(clientCertificateKey);
    }

    /**
     * Exactly one of the certificate and its private key is configured, which cannot authenticate anything.
     */
    public boolean hasIncompleteCertificateCredential() {
        return blank(clientCertificate) != blank(clientCertificateKey);
    }

    /**
     * The config key of the missing half, for a startup warning naming configuration keys, never values.
     */
    public Optional<String> missingCertificateCredentialKey() {
        if (!hasIncompleteCertificateCredential()) {
            return Optional.empty();
        }

        return blank(clientCertificate)
            ? Optional.of("panel.entra.client-certificate")
            : Optional.of("panel.entra.client-certificate-key");
    }

    /**
     * Credentials never appear rendered: neither {@link #clientSecret()} nor {@link #clientCertificateKey()} may
     * leak into logs, and the certificate itself would only be noise.
     */
    @Override
    public String toString() {
        return "EntraProperties[tenantId=" + tenantId
            + ", clientId=" + clientId
            + ", clientSecret=" + (usesSecretCredential() ? "<set>" : "<unset>")
            + ", clientCertificate=" + (blank(clientCertificate) ? "<unset>" : "<set>")
            + ", clientCertificateKey=" + (blank(clientCertificateKey) ? "<unset>" : "<set>")
            + ", authorityUrl=" + authorityUrl
            + ", graphUrl=" + graphUrl
            + ']';
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
