/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

/**
 * Suspensions of membership rights (art. 10 statutu): the register, its rules and the operations on it.
 * Lives in its own module because it needs bodies (mandate endings) and fees (arrears), both of which depend on
 * members; members only exposes the derived state through {@code SuspensionRegistry}.
 */
@NullMarked
@ApplicationModule(
    displayName = "Suspensions",
    allowedDependencies = {"persistence::*", "audit", "members", "bodies", "fees", "core::error", "core::user"}
)
package org.mlodzirazem.panel.services.panelapi.suspensions;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
