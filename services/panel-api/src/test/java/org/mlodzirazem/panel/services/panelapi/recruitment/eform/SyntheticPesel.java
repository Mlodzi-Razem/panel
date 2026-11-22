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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Test fixture: synthetic, mathematically valid PESEL numbers (real checksum, chosen birth dates,
 * never claimed to belong to an actual person — test data only, synthetic GDPR-safe names in the
 * e-form suite).
 */
public final class SyntheticPesel {
    private static final int[] WEIGHTS = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};

    private SyntheticPesel() {
    }

    public static String of(LocalDate birthDate, int serialNumber) {
        int century = birthDate.getYear() / 100;
        int pocket = switch (century) {
            case 18 -> 4;
            case 19 -> 0;
            case 20 -> 1;
            case 21 -> 2;
            default -> throw new IllegalArgumentException("Unsupported century: " + century);
        };

        String yy = "%02d".formatted(birthDate.getYear() % 100);
        String mm = "%02d".formatted(birthDate.getMonthValue() + pocket * 20);
        String dd = "%02d".formatted(birthDate.getDayOfMonth());
        String serial = "%04d".formatted(serialNumber);

        String ten = yy + mm + dd + serial;

        int weighted = 0;

        for (int i = 0; i < 10; i++) {
            weighted += (ten.charAt(i) - '0') * WEIGHTS[i];
        }

        int checksum = (10 - weighted % 10) % 10;

        return ten + checksum;
    }

    /** Age in completed years at the given date (same idiom as MemberRules.ageOn). */
    public static int ageAt(LocalDate birthDate, LocalDate at) {
        return (int) ChronoUnit.YEARS.between(birthDate, at);
    }
}
