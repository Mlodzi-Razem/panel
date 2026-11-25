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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReferenceCode {
    private static final String SEP = "[\\s\\p{Z}\\-\\u2010-\\u2015_.:/]*";
    private static final Pattern PATTERN = Pattern.compile(
        "M" + SEP + "R" + SEP + "(\\d{1,9})" + SEP + "(\\d)(?!\\d)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ReferenceCode() {
    }

    /**
     * ISO 7064 mod 11-10 check digit.
     */
    static int checkDigit(String digits) {
        int product = 10;

        for (int i = 0; i < digits.length(); i++) {
            int sum = (digits.charAt(i) - '0' + product) % 10;

            if (sum == 0) {
                sum = 10;
            }

            product = (sum * 2) % 11;
        }

        return (11 - product) % 10;
    }

    static String format(String number) {
        return "MR-" + number + "-" + checkDigit(number);
    }

    static Set<String> validNumbers(String text) {
        Set<String> numbers = new LinkedHashSet<>();
        Matcher matcher = PATTERN.matcher(text);

        while (matcher.find()) {
            String number = matcher.group(1);

            if (checkDigit(number) == matcher.group(2).charAt(0) - '0') {
                numbers.add(number);
            }
        }

        return numbers;
    }
}
