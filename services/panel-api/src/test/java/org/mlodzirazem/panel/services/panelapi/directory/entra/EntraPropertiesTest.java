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
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntraPropertiesTest {

    @Test
    void bindsTheCleanCertificateEnvNamesThroughTheirApplicationYmlPlaceholders() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                   .addFirst(new MapPropertySource("panel.yml", Map.of(
                       "panel.entra.client-certificate", "${PANEL_ENTRA_CLIENT_CERTIFICATE:}",
                       "panel.entra.client-certificate-key", "${PANEL_ENTRA_CLIENT_CERTIFICATE_KEY:}")));
        environment.getPropertySources()
                   .addFirst(new SystemEnvironmentPropertySource("test", Map.of(
                       "PANEL_ENTRA_CLIENT_CERTIFICATE", "cert-fixture",
                       "PANEL_ENTRA_CLIENT_CERTIFICATE_KEY", "key-fixture")));

        EntraProperties properties = Binder.get(environment)
                                            .bind("panel.entra", Bindable.of(EntraProperties.class))
                                            .get();

        assertThat(properties.clientCertificate()).isEqualTo("cert-fixture");
        assertThat(properties.clientCertificateKey()).isEqualTo("key-fixture");
        assertThat(properties.usesCertificateCredential()).isTrue();
        assertThat(properties.configured()).isFalse();
    }

    @Test
    void aCompleteCertificateCredentialTakesPrecedenceAnIncompleteOneIsUnconfigured() {
        assertThat(new EntraProperties("t", "c", "secret", null, null, null, null).usesSecretCredential()).isTrue();
        assertThat(new EntraProperties("t", "c", "secret", null, null, null, null).configured()).isTrue();

        assertThat(new EntraProperties("t", "c", null, "cert", "key", null, null).usesCertificateCredential()).isTrue();
        assertThat(new EntraProperties("t", "c", null, "cert", "key", null, null).configured()).isTrue();

        assertThat(new EntraProperties("t", "c", "secret", "cert", "key", null, null).usesCertificateCredential())
            .isTrue();
        assertThat(new EntraProperties("t", "c", "secret", "cert", "key", null, null).usesSecretCredential()).isTrue();

        EntraProperties certificateOnly = new EntraProperties("t", "c", null, "cert", null, null, null);
        EntraProperties keyOnly = new EntraProperties("t", "c", null, null, "key", null, null);

        assertThat(certificateOnly.configured()).isFalse();
        assertThat(certificateOnly.hasIncompleteCertificateCredential()).isTrue();
        assertThat(certificateOnly.missingCertificateCredentialKey()).contains("panel.entra.client-certificate-key");
        assertThat(keyOnly.configured()).isFalse();
        assertThat(keyOnly.missingCertificateCredentialKey()).contains("panel.entra.client-certificate");

        assertThat(new EntraProperties("t", "c", null, null, null, null, null).configured()).isFalse();
        assertThat(new EntraProperties("t", null, "secret", null, null, null, null).configured()).isFalse();
    }

    @Test
    void toStringNeverRendersTheCredentialValues() {
        EntraProperties properties = new EntraProperties("tenant-1", "client-1", "secret-1",
            "CERTIFICATE-PEM", "KEY-PEM", "https://login.example", "https://graph.example");

        assertThat(properties.toString())
            .contains("tenantId=tenant-1")
            .contains("clientId=client-1")
            .contains("clientSecret=<set>")
            .contains("clientCertificate=<set>")
            .contains("clientCertificateKey=<set>")
            .doesNotContain("secret-1")
            .doesNotContain("CERTIFICATE-PEM")
            .doesNotContain("KEY-PEM");
    }
}
