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

package org.mlodzirazem.panel.services.panelapi.api.v1.common;

import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

public final class Names {
    private Names() {
    }

    public static String display(TMember member) {
        return member.getPreferredNameFirst() + " " + member.getPreferredNameLast();
    }

    public static String mask(String identifier) {
        int visible = identifier.length() > 4 ? 4 : 0;
        return "*".repeat(identifier.length() - visible) + identifier.substring(identifier.length() - visible);
    }
}
