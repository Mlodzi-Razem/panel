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

package org.mlodzirazem.panel.services.panelapi.admin;

import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text helpers for the mandate CSV — a compact local twin of the register importer's {@code Text}.
 * The {@code admin} module cannot depend on the {@code importer} module (modulith boundary), and
 * this keeps the mandate importer self-contained: trivial to rebase next to A3/A4.
 */
final class MandateText {
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern DOT_DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern SLASH_DATE = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,15}");

    private MandateText() {
    }

    static String fold(String value) {
        String decomposed = Normalizer.normalize(value.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD);

        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    static String headerKey(String header) {
        return fold(header).replaceAll("[^a-z0-9]", "");
    }

    static @Nullable String blankToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String stripped = value.strip();

        return stripped.isEmpty() ? null : stripped;
    }

    static Optional<LocalDate> parseDate(String value) {
        String text = value.strip();

        try {
            Matcher iso = ISO_DATE.matcher(text);

            if (iso.matches()) {
                return Optional.of(LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                    Integer.parseInt(iso.group(3))));
            }

            Matcher dot = DOT_DATE.matcher(text);

            if (dot.matches()) {
                return Optional.of(LocalDate.of(Integer.parseInt(dot.group(3)), Integer.parseInt(dot.group(2)),
                    Integer.parseInt(dot.group(1))));
            }

            Matcher slash = SLASH_DATE.matcher(text);

            if (slash.matches()) {
                return Optional.of(LocalDate.of(Integer.parseInt(slash.group(3)), Integer.parseInt(slash.group(2)),
                    Integer.parseInt(slash.group(1))));
            }
        } catch (DateTimeException ex) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    /** The member number without leading zeros, or {@code null} when it is not purely numeric. */
    static @Nullable String numberKey(String memberId) {
        return NUMBER.matcher(memberId).matches() ? memberId.replaceFirst("^0+", "") : null;
    }
}
