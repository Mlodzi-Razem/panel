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

package org.mlodzirazem.panel.services.panelapi.core.user;

import org.jspecify.annotations.Nullable;

/**
 * The signed-in user as panel-web reports it.
 *
 * @param userId   the provider's immutable subject: the Google {@code sub} or the Entra object ID
 * @param email    the sign-in e-mail, used only to link a Google account on its first sign-in
 * @param name     display name, informational only
 * @param provider the provider that issued {@code userId}
 */
public record UserAuthInfo(
    String userId,

    @Nullable
    String email,

    @Nullable
    String name,

    IdentityProvider provider
) {
    public UserAuthInfo(String userId, @Nullable String email, @Nullable String name) {
        this(userId, email, name, IdentityProvider.GOOGLE);
    }
}
