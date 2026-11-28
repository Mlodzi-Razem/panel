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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Single place of the e-declaration rules: magic-link TTL, candidate-identifier validation,
 * name-pair shaping and the hashing discipline of the form tokens (SHA-256; plaintext tokens
 * exist once, in the link response visible to RECRUITMENT_MANAGE holders only).
 */
public final class RecruitmentEformRules {
    /** Link lifetime: one week to fill and sign; the case manager reissues on expiry. */
    public static final int LINK_TTL_HOURS = 168;

    /** Token entropy (bytes, url-safe Base64 of the raw bytes reaches the candidate). */
    public static final int TOKEN_BYTES = 32;

    /** Versioned artifact of the rendered declaration (template family "MR-deklaracja-2026"). */
    public static final String TEMPLATE_VERSION = "MR-deklaracja-2026.2";

    /** Marker for a future supporting-membership template variant of the same document. */
    public static final String TEMPLATE_VARIANT_SUPPORTING = "supporting";

    private static final int[] PESEL_WEIGHTS = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};

    private RecruitmentEformRules() {
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder(64);

            for (byte chunk : digest.digest(content)) {
                hex.append(String.format("%02x", chunk));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isPdf(byte[] content) {
        return content.length > 4
            && content[0] == '%'
            && content[1] == 'P'
            && content[2] == 'D'
            && content[3] == 'F';
    }

    /**
     * Declared candidacy is ordinary membership: the candidate must fit the statutory admission age
     * (statut jednolity 2026, art. 7 ust. 1) at the declaration date. The supporting-membership
     * variant is a template marker outside the workflow engine (owner decision 2026-11-22).
     */
    public static void assertAdmissionAge(LocalDate birthDate, LocalDate declarationDate) {
        MemberRules.validateAdmissionAge(birthDate, MembershipType.ORDINARY, declarationDate);
    }

    /** Formal/preferred names come in pairs: first without last (or the other way round) is a defect. */
    public static void assertNamePair(@Nullable String first, @Nullable String last, String code) {
        if ((first == null || first.isBlank()) != (last == null || last.isBlank())) {
            throw new RuleViolationException(code,
                "The first name and the last name of a name pair must be given together");
        }
    }

    /**
     * Normalizes a PESEL to plain digits; {@code null} when blank. Throws
     * {@code PERSONAL_ID_INVALID} for a wrong length, non-digits, a failing checksum (the official
     * weights) or an unparseable birth date. When the case already carries a birth date, the two
     * dates must match — a mismatch is reported with the same generic code for phishing hygiene.
     */
    public static String normalizePersonalId(String personalId, @Nullable LocalDate knownBirthDate) {
        String digits = personalId == null ? "" : MemberRules.canonicalDigits(personalId);

        if (digits.length() != 11 || !MemberRules.isAsciiDigits(digits)) {
            throw invalidPersonalId();
        }

        int weighted = 0;

        for (int i = 0; i < 10; i++) {
            weighted += (digits.charAt(i) - '0') * PESEL_WEIGHTS[i];
        }

        int checksum = (10 - weighted % 10) % 10;

        if (checksum != digits.charAt(10) - '0') {
            throw invalidPersonalId();
        }

        LocalDate fromPesel = peselBirthDate(digits).orElseThrow(RecruitmentEformRules::invalidPersonalId);

        if (knownBirthDate != null && !fromPesel.equals(knownBirthDate)) {
            throw invalidPersonalId();
        }

        return digits;
    }

    /** Birth date encoded in the PESEL; empty when the date part is not a valid date. */
    public static Optional<LocalDate> peselBirthDate(String pesel) {
        if (pesel.length() != 11 || !MemberRules.isAsciiDigits(pesel)) {
            return Optional.empty();
        }

        int rawYear = Integer.parseInt(pesel.substring(0, 2));
        int rawMonth = Integer.parseInt(pesel.substring(2, 4));
        int day = Integer.parseInt(pesel.substring(4, 6));

        // month pockets: 01-12 -> 19xx, 21-32 -> 20xx, 41-52 -> 21xx, 81-92 -> 18xx
        int century = switch ((rawMonth - 1) / 20) {
            case 0 -> 1900;
            case 1 -> 2000;
            case 2 -> 2100;
            case 4 -> 1800;
            default -> -1;
        };

        if (century < 0 || rawMonth % 20 < 1 || rawMonth % 20 > 12) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.of(century + rawYear, rawMonth % 20, day));
        } catch (java.time.DateTimeException malformed) {
            return Optional.empty();
        }
    }

    private static RuleViolationException invalidPersonalId() {
        return new RuleViolationException("PERSONAL_ID_INVALID",
            "The personal identifier is not valid");
    }

    /** Formatter of the canonical, server-set declaration date line of the rendered PDF. */
    public static String declarationDateLine(LocalDate date) {
        DateTimeFormatter polish = DateTimeFormatter.ofPattern("d MMMM yyyy").withLocale(Locale.of("pl"));
        return "Data wypełnienia (kanoniczna, ustawiona przez serwer): " + date.format(polish);
    }
}
