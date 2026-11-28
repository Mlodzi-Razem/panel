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

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * {@code panel.directory.provisioning.*}. Everything that writes to Microsoft Graph sits behind {@link #enabled()},
 * which is {@code false} unless somebody switches it on. Which Graph permissions the application holds is decided in the
 * tenant (roles scoped to administrative units, decision D1); nothing here assumes tenant-wide write permissions.
 *
 * @param enabled                 master switch: the worker, every synchronous Graph method and the guard refuse to run
 *                                without it, so no Graph call (not even the token request) is made
 * @param destructiveEnabled      disable, delete and group-removal tasks wait in HELD while this is off
 * @param domain                  the verified domain of member accounts ({@code NNNNNN@domain})
 * @param usageLocation           ISO 3166-1 alpha-2 code Graph needs before it assigns a licence
 * @param memberLicenseSku        licence SKU id (GUID) of member accounts; blank = no licence (D2 is still open)
 * @param trialLicenseSku         licence SKU id (GUID) of trial accounts; blank = no licence
 * @param tapLifetime             lifetime of a Temporary Access Pass (10 minutes to 8 hours)
 * @param membersAdministrativeUnitId administrative unit new accounts are created in; blank = {@code POST /users}
 * @param groupAdministrativeUnitId   administrative unit the mapped groups must belong to; blank = not checked
 * @param protectedObjectIds      Entra object ids that are never written to
 * @param allowFixtureBand        accept the development number band 950000-969999 (refused unless the dev profile is on)
 * @param pollInterval            worker poll interval
 * @param batchSize               tasks claimed per poll
 * @param maxAttempts             attempts before a retryable task fails with ATTEMPTS_EXHAUSTED
 * @param backoffInitial          first backoff
 * @param backoffMax              backoff ceiling
 * @param forbiddenGrace          how long after an account was created a 403 counts as transient (administrative-unit
 *                                rule delay)
 * @param maxJobAge               tasks older than this are HELD instead of run when the worker picks them up
 * @param maxDestructivePerHour   destructive operations per sliding hour across all replicas
 * @param setMail                 also write the {@code mail} property from the member's domain address
 * @param forbiddenPermissions    application roles that stop the worker (SEC-1/SEC-2), <em>added</em> to the built-in list
 *                                {@link #DEFAULT_FORBIDDEN}, never replacing it (D1); {@code RoleManagement.*} is always
 *                                forbidden as well
 */
@ConfigurationProperties("panel.directory.provisioning")
public record ProvisioningProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("false") boolean destructiveEnabled,
    @DefaultValue("mlodzirazem.org") String domain,
    @DefaultValue("PL") String usageLocation,
    @DefaultValue("") String memberLicenseSku,
    @DefaultValue("") String trialLicenseSku,
    @DefaultValue("60m") Duration tapLifetime,
    @DefaultValue("") String membersAdministrativeUnitId,
    @DefaultValue("") String groupAdministrativeUnitId,
    @Nullable List<String> protectedObjectIds,
    @DefaultValue("false") boolean allowFixtureBand,
    @DefaultValue("15s") Duration pollInterval,
    @DefaultValue("10") int batchSize,
    @DefaultValue("10") int maxAttempts,
    @DefaultValue("30s") Duration backoffInitial,
    @DefaultValue("30m") Duration backoffMax,
    @DefaultValue("20m") Duration forbiddenGrace,
    @DefaultValue("7d") Duration maxJobAge,
    @DefaultValue("10") int maxDestructivePerHour,
    @DefaultValue("false") boolean setMail,
    @Nullable Set<String> forbiddenPermissions
) {
    static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * The permissions SEC-1 forbids on the application; none of them is needed with roles scoped to administrative units.
     * The last five are self-empowering (they would let the token grant itself more, or write authentication methods
     * beyond TAP), so they stay forbidden even when the configuration names others (D1).
     */
    static final Set<String> DEFAULT_FORBIDDEN = Set.of(
        "User.ReadWrite.All", "Directory.ReadWrite.All", "User.ReadUpdate.All", "User.EnableDisableAccount.All",
        "LicenseAssignment.ReadWrite.All", "GroupMember.ReadWrite.All", "Group.ReadWrite.All",
        "AppRoleAssignment.ReadWrite.All", "Application.ReadWrite.All", "Policy.ReadWrite.AuthenticationMethod",
        "UserAuthenticationMethod.ReadWrite.All", "Organization.ReadWrite.All");

    public ProvisioningProperties {
        protectedObjectIds = protectedObjectIds == null
            ? List.of()
            : protectedObjectIds.stream().map(String::strip).map(String::toLowerCase).filter(s -> !s.isEmpty()).toList();
        Set<String> forbidden = new TreeSet<>(DEFAULT_FORBIDDEN);

        if (forbiddenPermissions != null) {
            forbidden.addAll(forbiddenPermissions);
        }

        forbiddenPermissions = Set.copyOf(forbidden);
        domain = domain.strip().toLowerCase();
        usageLocation = usageLocation.strip().toUpperCase();
        memberLicenseSku = memberLicenseSku.strip();
        trialLicenseSku = trialLicenseSku.strip();
        membersAdministrativeUnitId = membersAdministrativeUnitId.strip();
        groupAdministrativeUnitId = groupAdministrativeUnitId.strip();
    }

    /** Nothing of the configuration is secret, but the list of protected accounts stays out of logs. */
    @Override
    public String toString() {
        return "ProvisioningProperties[enabled=" + enabled + ", destructiveEnabled=" + destructiveEnabled + ']';
    }
}
