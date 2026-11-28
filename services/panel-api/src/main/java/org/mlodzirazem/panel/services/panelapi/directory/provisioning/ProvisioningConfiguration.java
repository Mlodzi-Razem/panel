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
import org.mlodzirazem.panel.services.panelapi.directory.entra.EntraProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SEC-52: the configuration is validated when the application starts, and the message names keys, never values.
 * Format is always checked; with {@code enabled=true} the settings provisioning cannot do without must be present too.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ProvisioningProperties.class)
class ProvisioningConfiguration {
    private static final Pattern DOMAIN = Pattern.compile("^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$");
    private static final String PREFIX = "panel.directory.provisioning.";

    ProvisioningConfiguration(ProvisioningProperties properties, EntraProperties entra, Environment environment) {
        List<String> problems = new ArrayList<>();

        if (!DOMAIN.matcher(properties.domain()).matches()) {
            problems.add(PREFIX + "domain");
        }

        if (!properties.usageLocation().matches("^[A-Z]{2}$")) {
            problems.add(PREFIX + "usage-location");
        }

        uuid(properties.memberLicenseSku(), "member-license-sku", problems);
        uuid(properties.trialLicenseSku(), "trial-license-sku", problems);
        uuid(properties.membersAdministrativeUnitId(), "members-administrative-unit-id", problems);
        uuid(properties.groupAdministrativeUnitId(), "group-administrative-unit-id", problems);

        if (properties.protectedObjectIds().stream().anyMatch(id -> !ProvisioningProperties.UUID.matcher(id).matches())) {
            problems.add(PREFIX + "protected-object-ids");
        }

        if (properties.tapLifetime().compareTo(Duration.ofMinutes(10)) < 0 || properties.tapLifetime().compareTo(Duration.ofHours(8)) > 0) {
            problems.add(PREFIX + "tap-lifetime");
        }

        if (properties.batchSize() < 1 || properties.batchSize() > 100) {
            problems.add(PREFIX + "batch-size");
        }

        if (properties.maxAttempts() < 1 || properties.maxAttempts() > 50) {
            problems.add(PREFIX + "max-attempts (1-50)");
        }

        if (properties.pollInterval().isZero() || properties.pollInterval().isNegative()) {
            problems.add(PREFIX + "poll-interval");
        }

        if (properties.backoffInitial().isZero() || properties.backoffInitial().isNegative()
            || properties.backoffMax().compareTo(properties.backoffInitial()) < 0) {
            problems.add(PREFIX + "backoff-initial / backoff-max");
        }

        if (properties.forbiddenGrace().isNegative()) {
            problems.add(PREFIX + "forbidden-grace");
        }

        if (properties.maxJobAge().isZero() || properties.maxJobAge().isNegative() || properties.maxJobAge().compareTo(Duration.ofDays(30)) > 0) {
            problems.add(PREFIX + "max-job-age (0-30d)");
        }

        // D2: a limit set to a huge value silently switches SEC-41 off, so the bounds are part of the validation
        if (properties.maxDestructivePerHour() < 0 || properties.maxDestructivePerHour() > 200) {
            problems.add(PREFIX + "max-destructive-per-hour (0-200)");
        }

        Set<String> profiles = Set.of(environment.getActiveProfiles());

        if (properties.allowFixtureBand() && !profiles.contains("dev")) {
            // the 950000-969999 fixture numbers must never exist outside development
            problems.add(PREFIX + "allow-fixture-band (only with the dev profile)");
        }

        if (properties.enabled()) {
            if (!entra.configured()) {
                problems.add("panel.entra.tenant-id / client-id / credential (required when provisioning is enabled)");
            }

            for (var required : List.of(
                new String[]{"member-license-sku", properties.memberLicenseSku()},
                new String[]{"trial-license-sku", properties.trialLicenseSku()},
                new String[]{"members-administrative-unit-id", properties.membersAdministrativeUnitId()},
                new String[]{"group-administrative-unit-id", properties.groupAdministrativeUnitId()})) {
                if (required[1].isBlank()) {
                    problems.add(PREFIX + required[0] + " (required when provisioning is enabled)");
                }
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid provisioning configuration: " + String.join(", ", problems));
        }

        if (properties.enabled()) {
            log.warn("Entra provisioning is ENABLED (destructive operations {})", properties.destructiveEnabled() ? "enabled" : "held");

            if (properties.protectedObjectIds().isEmpty()) {
                // D2: without an entry the break-glass and staff accounts are protected by the UPN pattern alone
                log.warn("panel.directory.provisioning.protected-object-ids is empty: no object is protected by id");
            }
        }
    }

    private static void uuid(String value, String key, List<String> problems) {
        if (!value.isBlank() && !ProvisioningProperties.UUID.matcher(value.strip()).matches()) {
            problems.add(PREFIX + key);
        }
    }
}
