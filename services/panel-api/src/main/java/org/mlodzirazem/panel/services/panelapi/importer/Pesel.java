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

package org.mlodzirazem.panel.services.panelapi.importer;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;

final class Pesel {
    private static final int[] WEIGHTS = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};

    private Pesel() {
    }

    static boolean hasFormat(String value) {
        return value.length() == 11 && value.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    static boolean checksumValid(String value) {
        int sum = 0;

        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (value.charAt(i) - '0') * WEIGHTS[i];
        }

        return (10 - sum % 10) % 10 == value.charAt(10) - '0';
    }

    static Optional<LocalDate> birthDate(String value) {
        int yy = Integer.parseInt(value.substring(0, 2));
        int mm = Integer.parseInt(value.substring(2, 4));
        int dd = Integer.parseInt(value.substring(4, 6));

        int century = switch (mm / 20) {
            case 0 -> 1900;
            case 1 -> 2000;
            case 2 -> 2100;
            case 3 -> 2200;
            case 4 -> 1800;
            default -> -1;
        };

        if (century < 0) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.of(century + yy, mm % 20, dd));
        } catch (DateTimeException ex) {
            return Optional.empty();
        }
    }
}
