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

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountState.AccountKind;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-3: before the Panel writes to a user, it reads that user by the object id of its own registry and refuses unless
 * the object is what the registry says: a {@code Member} user, user principal name {@code NNNNNN@<domain>} with the
 * registered number, a number in the band of the account kind, an {@code employeeId} that is empty or equal to the number
 * (never set on a trial account), and an object id outside the protected list. The azure-side administrative-unit rule is
 * configuration that can be widened by mistake; this guard does not rely on it.
 */
@Component
@RequiredArgsConstructor
class ManagedAccountGuard {
    private static final Pattern UPN = Pattern.compile("^(\\d{6})@(.+)$");

    private final GraphDirectoryClient graph;
    private final ProvisioningProperties properties;

    /**
     * Reads the account's user and checks it.
     *
     * @return the user as Graph has it now
     * @throws ProvisioningException {@link ProvisioningErrorCode#ACCOUNT_NOT_MANAGED} when any check fails,
     *                               {@link ProvisioningErrorCode#GRAPH_NOT_FOUND} when the object no longer exists
     */
    GraphUser verify(AccountRegistry.Account account) {
        if (properties.protectedObjectIds().contains(account.objectId().toLowerCase())) {
            throw notManaged();
        }

        GraphUser user = graph.user(account.objectId()).orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.GRAPH_NOT_FOUND));
        require(user, account.number(), account.kind(), account.objectId());
        return user;
    }

    /**
     * The same checks on a user found some other way (adoption): the caller supplies the number that the user principal
     * name must carry and the kind the account would have.
     */
    void require(GraphUser user, String expectedNumber, AccountKind kind, String expectedObjectId) {
        if (!user.id().equalsIgnoreCase(expectedObjectId)
            || properties.protectedObjectIds().contains(user.id().toLowerCase())
            || !"member".equalsIgnoreCase(user.userType())) {
            throw notManaged();
        }

        String upn = user.userPrincipalName();
        Matcher matcher = upn == null ? null : UPN.matcher(upn);

        if (matcher == null || !matcher.matches() || !matcher.group(2).equalsIgnoreCase(properties.domain())
            || !matcher.group(1).equals(expectedNumber) || !inBand(kind, expectedNumber)) {
            throw notManaged();
        }

        String employeeId = user.employeeId();

        if (employeeId != null && (kind == AccountKind.TRIAL || !employeeId.equals(expectedNumber))) {
            throw notManaged();
        }
    }

    /** The number band of an account kind: members 000001-949999 (and 950000-969999 where the dev band is allowed), trials 990000-999999. */
    boolean inBand(AccountKind kind, String number) {
        if (!number.matches("\\d{6}")) {
            return false;
        }

        int value = Integer.parseInt(number);

        return switch (kind) {
            case MEMBER -> (value >= 1 && value <= 949_999) || (properties.allowFixtureBand() && value >= 950_000 && value <= 969_999);
            case TRIAL -> value >= 990_000 && value <= 999_999;
        };
    }

    String upn(String number) {
        return number + "@" + properties.domain();
    }

    private static ProvisioningException notManaged() {
        return new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
    }
}
