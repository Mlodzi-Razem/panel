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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;

import java.time.LocalDate;
import java.time.Period;

public final class MemberRules {
    /** Statute jednolity 2026, art. 7 ust. 1 pkt 1. */
    public static final int ORDINARY_MIN_AGE = 16;
    /** Statute jednolity 2026, art. 7 ust. 1 pkt 2 — admission before the year of the 26th birthday ends. */
    public static final int ORDINARY_MAX_AGE = 26;
    /** Statute jednolity 2026, art. 8 ust. 1 pkt 1 (under-16 supporting memberships are only transitional, art. 48 ust. 1). */
    public static final int SUPPORTING_MIN_AGE = 16;
    /** Statute jednolity 2026, art. 11 ust. 1 pkt 5. */
    public static final int MEMBERSHIP_END_AGE = 26;

    /** Reserved for test fixtures only (PO-7); the number allocator never produces values in this band. */
    public static final int RESERVED_TEST_MEMBER_NUMBER_FROM = 950_000;
    public static final int RESERVED_TEST_MEMBER_NUMBER_TO = 999_999;

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

    /** NFKC-folded text: full-width and other compatibility digits become ASCII (BAN-1). */
    public static String fold(String raw) {
        return java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC);
    }

    /**
     * Canonical PESEL/member-number digits: NFKC-folded, spaces and hyphens removed. Only ASCII
     * '0'..'9' pass {@link #isAsciiDigits}: Character.isDigit and parseInt accept other scripts (BAN-1).
     */
    public static String canonicalDigits(String raw) {
        return fold(raw).replaceAll("[\\s-]", "");
    }

    public static boolean isAsciiDigits(String value) {
        return value.chars().allMatch(ch -> ch >= '0' && ch <= '9');
    }

    public static boolean isReservedTestMemberNumber(@Nullable String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return false;
        }

        String digits = fold(memberId.strip());

        if (!isAsciiDigits(digits)) {
            return false;
        }

        String normalized = digits.replaceFirst("^0+", "");

        if (normalized.isEmpty()) {
            return false;
        }

        try {
            long number = Long.parseLong(normalized);

            return number >= RESERVED_TEST_MEMBER_NUMBER_FROM && number <= RESERVED_TEST_MEMBER_NUMBER_TO;
        } catch (NumberFormatException tooLong) {
            return false;
        }
    }

    public static void assertMemberNumberNotReserved(@Nullable String memberId) {
        if (isReservedTestMemberNumber(memberId)) {
            throw new RuleViolationException("MEMBER_ID_RESERVED",
                "Member number %s is in the reserved test band %d-%d".formatted(memberId,
                    RESERVED_TEST_MEMBER_NUMBER_FROM, RESERVED_TEST_MEMBER_NUMBER_TO));
        }
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
