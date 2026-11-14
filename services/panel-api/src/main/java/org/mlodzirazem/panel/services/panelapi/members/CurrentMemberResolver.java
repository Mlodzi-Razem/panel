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

package org.mlodzirazem.panel.services.panelapi.members;

import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.util.Optional;

public interface CurrentMemberResolver {
    /**
     * Linking rule: a member with {@code directory_user_id = sub}; otherwise the member whose {@code domain_email}
     * equals the token e-mail (case-insensitive) is linked by setting {@code directory_user_id} (audited as
     * {@code members :: link-user}); otherwise empty.
     */
    Optional<TMember> resolve(UserAuthInfo userAuthInfo);

    /**
     * Resolves the user bound to the current request, if any.
     */
    Optional<TMember> current();
}
