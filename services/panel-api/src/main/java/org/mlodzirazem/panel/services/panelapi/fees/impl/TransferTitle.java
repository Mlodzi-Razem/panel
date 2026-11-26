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

package org.mlodzirazem.panel.services.panelapi.fees.impl;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The association's transfer title: {@code <number> SKŁADKA CZŁONKOWSKA STOWARZYSZENIE MŁODZI RAZEM}, where the
 * number is the six-digit member number, alone or as the Entra ID {@code <number>@mlodzirazem.org}.
 * <p>
 * There is no check digit, so the match is strict: the title has to start with the number, the phrase has to follow
 * it and nothing but the name of the association (possibly cut off by the bank) may come after. Case, Polish
 * diacritics, punctuation and spaces inserted by banks are ignored. Anything else is left to the treasurer.
 */
final class TransferTitle {
    private static final String ASSOCIATION = "STOWARZYSZENIEMLODZIRAZEM";
    private static final Pattern TEMPLATE = Pattern.compile(
        "(\\d{6})(?:@MLODZIRAZEMORG)?SKLADKACZLONKOWSKA([A-Z0-9@]*)");
    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private TransferTitle() {
    }

    /**
     * The member number of a title that follows the template, if it does.
     */
    static Optional<String> templateNumber(String title) {
        Matcher matcher = TEMPLATE.matcher(compact(title));

        return matcher.matches() && ASSOCIATION.startsWith(matcher.group(2))
            ? Optional.of(matcher.group(1))
            : Optional.empty();
    }

    /**
     * Every standalone six-digit group, whatever the rest of the title says.
     */
    static Set<String> numbersNamed(String title) {
        Set<String> numbers = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(title);

        while (matcher.find()) {
            numbers.add(matcher.group());
        }

        return numbers;
    }

    private static String compact(String title) {
        return Normalizer.normalize(title.toUpperCase(Locale.ROOT).replace('Ł', 'L'), Normalizer.Form.NFD)
                         .replaceAll("[^A-Z0-9@]", "");
    }
}
