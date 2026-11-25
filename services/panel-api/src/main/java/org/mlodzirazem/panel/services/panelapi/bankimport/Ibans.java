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

package org.mlodzirazem.panel.services.panelapi.bankimport;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class Ibans {
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-]");
    private static final Pattern NRB = Pattern.compile("\\d{26}");
    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}");
    private static final int POLISH_IBAN_LENGTH = 28;

    private Ibans() {
    }

    public static String normalise(@Nullable String raw) {
        return tryNormalise(raw).orElseThrow(() -> new IllegalArgumentException("Invalid IBAN"));
    }

    public static Optional<String> tryNormalise(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = SEPARATORS.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
        if (NRB.matcher(compact).matches()) {
            compact = "PL" + compact;
        }
        if (!IBAN.matcher(compact).matches()) {
            return Optional.empty();
        }
        if (compact.startsWith("PL") && compact.length() != POLISH_IBAN_LENGTH) {
            return Optional.empty();
        }
        return checksumValid(compact) ? Optional.of(compact) : Optional.empty();
    }

    public static boolean isValid(@Nullable String raw) {
        return tryNormalise(raw).isPresent();
    }

    private static boolean checksumValid(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (int i = 0; i < rearranged.length(); i++) {
            int digit = Character.digit(rearranged.charAt(i), 36);
            remainder = digit >= 10 ? (remainder * 100 + digit) % 97 : (remainder * 10 + digit) % 97;
        }
        return remainder == 1;
    }
}
