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


package org.mlodzirazem.panel.services.panelapi.recruitment;

import org.mlodzirazem.panel.services.panelapi.members.MemberRules;

/**
 * Canonical identifier digits for the admission-ban lookups (BAN-1): NFKC-folded, separators
 * stripped, ASCII 0-9 only. Exposed at the recruitment root so the admin module can use it.
 */
public final class AdmissionIdentifiers {
    private AdmissionIdentifiers() {
    }

    public static String canonicalDigits(String raw) {
        return MemberRules.canonicalDigits(raw);
    }

    public static boolean isAsciiDigits(String value) {
        return MemberRules.isAsciiDigits(value);
    }
}
