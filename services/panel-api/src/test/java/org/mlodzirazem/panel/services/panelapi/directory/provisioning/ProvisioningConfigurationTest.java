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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.mlodzirazem.panel.services.panelapi.directory.entra.EntraProperties;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: the startup validation (SEC-52) and the permission check of the token (SEC-2). */
class ProvisioningConfigurationTest {
    private static final String GUID = "11111111-1111-1111-1111-111111111111";
    private static final EntraProperties ENTRA = new EntraProperties("t", "c", "s", null, null, null, null);

    private static ProvisioningProperties properties(boolean enabled, String sku, String unit, boolean fixtureBand, Duration tap) {
        return new ProvisioningProperties(enabled, false, "mlodzirazem.org", "PL", sku, sku, tap, unit, unit, List.of(), fixtureBand,
            Duration.ofSeconds(15), 10, 10, Duration.ofSeconds(30), Duration.ofMinutes(30), Duration.ofMinutes(20), Duration.ofDays(7), 10, false, null);
    }

    @Test
    void anEnabledProvisioningNeedsItsLicencesAndAdministrativeUnits() {
        assertThatThrownBy(() -> new ProvisioningConfiguration(properties(true, "", "", false, Duration.ofMinutes(60)), ENTRA, new MockEnvironment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContainingAll("member-license-sku", "trial-license-sku", "members-administrative-unit-id", "group-administrative-unit-id");
        assertThatThrownBy(() -> new ProvisioningConfiguration(properties(true, GUID, GUID, false, Duration.ofMinutes(60)),
            new EntraProperties(null, null, null, null, null, null, null), new MockEnvironment()))
            .hasMessageContaining("panel.entra");
        assertThatCode(() -> new ProvisioningConfiguration(properties(true, GUID, GUID, false, Duration.ofMinutes(60)), ENTRA, new MockEnvironment()))
            .doesNotThrowAnyException();
        assertThatCode(() -> new ProvisioningConfiguration(properties(false, "", "", false, Duration.ofMinutes(60)), ENTRA, new MockEnvironment()))
            .doesNotThrowAnyException();
    }

    @Test
    void theFixtureBandIsRefusedOutsideTheDevProfileAndBadValuesAreNamedNotShown() {
        assertThatThrownBy(() -> new ProvisioningConfiguration(properties(false, "", "", true, Duration.ofMinutes(60)), ENTRA, new MockEnvironment()))
            .hasMessageContaining("allow-fixture-band");
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThatCode(() -> new ProvisioningConfiguration(properties(false, "", "", true, Duration.ofMinutes(60)), ENTRA, dev)).doesNotThrowAnyException();

        assertThatThrownBy(() -> new ProvisioningConfiguration(properties(false, "secret-looking-value", "", false, Duration.ofDays(2)), ENTRA, new MockEnvironment()))
            .hasMessageContainingAll("member-license-sku", "tap-lifetime")
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret-looking-value"));
    }

    private static GraphAppToken token(String jwt, AtomicInteger calls) {
        return new GraphAppToken() {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public String graphUrl() {
                return "http://graph.invalid";
            }

            @Override
            public String bearer() {
                calls.incrementAndGet();
                return jwt;
            }
        };
    }

    @Test
    void theGateReadsTheRolesOfTheTokenAndDoesNotAskWhileSwitchedOff() {
        AtomicInteger calls = new AtomicInteger();
        ProvisioningProperties on = properties(true, GUID, GUID, false, Duration.ofMinutes(60));
        ProvisioningProperties off = properties(false, GUID, GUID, false, Duration.ofMinutes(60));
        JsonMapper json = JsonMapper.builder().build();

        assertThat(new ProvisioningGate(off, token(ProvisioningTestSupport.jwt("UserAuthMethod-TAP.ReadWrite.All"), calls), json, Clock.systemUTC()).enabled()).isFalse();
        assertThat(calls.get()).isZero();

        assertThat(new ProvisioningGate(on, token(ProvisioningTestSupport.jwt("UserAuthMethod-TAP.ReadWrite.All"), calls), json, Clock.systemUTC()).enabled()).isTrue();
        assertThat(new ProvisioningGate(on, token(ProvisioningTestSupport.jwt("Directory.ReadWrite.All"), calls), json, Clock.systemUTC()).enabled()).isFalse();
        assertThat(new ProvisioningGate(on, token("garbage", calls), json, Clock.systemUTC()).enabled()).isFalse();
        assertThat(Set.of("User.ReadWrite.All")).isSubsetOf(ProvisioningProperties.DEFAULT_FORBIDDEN);
    }

    @Test
    void theLimitsHaveBoundsSoThatNoneOfThemSwitchesAProtectionOff() {
        ProvisioningProperties huge = new ProvisioningProperties(false, false, "mlodzirazem.org", "PL", "", "", Duration.ofMinutes(60),
            "", "", List.of(), false, Duration.ofSeconds(15), 10, 0, Duration.ofSeconds(30), Duration.ofMinutes(30),
            Duration.ofMinutes(20), Duration.ofDays(90), 1_000_000, false, null);

        assertThatThrownBy(() -> new ProvisioningConfiguration(huge, ENTRA, new MockEnvironment()))
            .hasMessageContainingAll("max-destructive-per-hour", "max-attempts", "max-job-age");
    }

    @Test
    void theConfiguredForbiddenPermissionsAddToTheBuiltInListInsteadOfReplacingIt() {
        ProvisioningProperties extra = new ProvisioningProperties(false, false, "mlodzirazem.org", "PL", "", "", Duration.ofMinutes(60),
            "", "", List.of(), false, Duration.ofSeconds(15), 10, 10, Duration.ofSeconds(30), Duration.ofMinutes(30),
            Duration.ofMinutes(20), Duration.ofDays(7), 10, false, Set.of("Mail.Send"));

        assertThat(extra.forbiddenPermissions()).contains("Mail.Send").containsAll(ProvisioningProperties.DEFAULT_FORBIDDEN);
    }
}
