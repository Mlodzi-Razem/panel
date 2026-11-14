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

import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;

import java.time.LocalDate;
import java.time.Period;

public final class MemberRules {
    public static final int ORDINARY_MIN_AGE = 16;
    public static final int ORDINARY_MAX_AGE = 26;
    public static final int SUPPORTING_MIN_AGE = 14;
    public static final int MEMBERSHIP_END_AGE = 26;

    private MemberRules() {
    }

    public static int ageOn(LocalDate birthDate, LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    public static LocalDate membershipEndDate(LocalDate birthDate) {
        return LocalDate.of(birthDate.getYear() + MEMBERSHIP_END_AGE, 12, 31);
    }

    public static boolean membershipEndsIn(LocalDate birthDate, int year) {
        return birthDate.getYear() + MEMBERSHIP_END_AGE == year;
    }

    public static void validateAdmissionAge(LocalDate birthDate, MembershipType type, LocalDate admissionDate) {
        int age = ageOn(birthDate, admissionDate);

        switch (type) {
            case ORDINARY -> {
                if (age < ORDINARY_MIN_AGE || age > ORDINARY_MAX_AGE) {
                    throw new RuleViolationException("ADMISSION_AGE",
                        "Ordinary membership requires age %d-%d at admission".formatted(ORDINARY_MIN_AGE,
                            ORDINARY_MAX_AGE));
                }
            }
            case SUPPORTING -> {
                if (age < SUPPORTING_MIN_AGE) {
                    throw new RuleViolationException("ADMISSION_AGE",
                        "Supporting membership requires age %d+ at admission".formatted(SUPPORTING_MIN_AGE));
                }
            }
            case HONORARY -> {
            }
        }
    }
}
