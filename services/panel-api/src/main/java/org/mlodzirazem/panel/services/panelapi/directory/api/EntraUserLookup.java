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

package org.mlodzirazem.panel.services.panelapi.directory.api;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Reads Microsoft Entra ID accounts. Used to link an Entra sign-in to a member through the account's
 * {@code employeeId}, which holds the member number.
 */
public interface EntraUserLookup {
    /**
     * @param objectId the Entra object ID ({@code oid}) of the account
     * @return the account, or empty when it does not exist or Entra access is not configured
     * @throws IllegalStateException when Microsoft Graph cannot be reached or rejects the request
     */
    Optional<EntraUser> find(String objectId);

    /**
     * @param objectId       the Entra object ID
     * @param employeeId     the member number stored on the account, if any
     * @param accountEnabled     whether the account may sign in
     * @param userPrincipalName  the sign-in name ({@code userPrincipalName}); a member's account is
     *                           linked only when its local part is the member number (SEC-20c)
     */
    record EntraUser(String objectId, @Nullable String employeeId, boolean accountEnabled,
                     @Nullable String userPrincipalName) {
    }
}
