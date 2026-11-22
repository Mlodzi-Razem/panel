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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecruitmentEformRulesTest {
    @Test
    void syntheticPeselPassesTheOfficialChecksum() {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 1);

        assertThat(pesel).hasSize(11);
        assertThatCode(() -> RecruitmentEformRules.normalizePersonalId(pesel, null)).doesNotThrowAnyException();
        assertThat(RecruitmentEformRules.normalizePersonalId(pesel, null)).isEqualTo(pesel);
        assertThat(RecruitmentEformRules.peselBirthDate(pesel)).contains(LocalDate.of(2007, 4, 1));
    }

    @Test
    void centuryPocketsDecodeBackToTheBirthDate() {
        LocalDate nineteenth = LocalDate.of(1995, 12, 31);
        LocalDate millennial = LocalDate.of(2001, 1, 1);
        LocalDate future = LocalDate.of(2103, 5, 20);

        assertThat(RecruitmentEformRules.peselBirthDate(SyntheticPesel.of(nineteenth, 11)))
            .contains(nineteenth);
        assertThat(RecruitmentEformRules.peselBirthDate(SyntheticPesel.of(millennial, 12)))
            .contains(millennial);
        assertThat(RecruitmentEformRules.peselBirthDate(SyntheticPesel.of(future, 13)))
            .contains(future);
    }

    @Test
    void normalizationRejectsWrongChecksumsAndFormats() {
        String valid = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 1);
        char wrongChecksum = valid.charAt(10) == '9' ? '0' : '9';
        String invalid = valid.substring(0, 10) + wrongChecksum;

        assertThatThrownBy(() -> RecruitmentEformRules.normalizePersonalId(invalid, null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("PERSONAL_ID_INVALID");
        assertThatThrownBy(() -> RecruitmentEformRules.normalizePersonalId(valid.substring(1), null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("PERSONAL_ID_INVALID");
        assertThatThrownBy(() ->
            RecruitmentEformRules.normalizePersonalId(valid.replace('0', 'O').replace('1', 'l'), null))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("PERSONAL_ID_INVALID");
    }

    @Test
    void knownBirthDateMustMatchThePesel() {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 1);

        assertThatThrownBy(() ->
            RecruitmentEformRules.normalizePersonalId(pesel, LocalDate.of(2007, 4, 2)))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("PERSONAL_ID_INVALID");
    }

    @Test
    void separatorsAreToleratedAndStripped() {
        String pesel = SyntheticPesel.of(LocalDate.of(2007, 4, 1), 1);

        assertThat(RecruitmentEformRules.normalizePersonalId(
            pesel.substring(0, 6) + "-" + pesel.substring(6), null)).isEqualTo(pesel);
    }

    @Test
    void namePairsMustBeComplete() {
        assertThatCode(() ->
            RecruitmentEformRules.assertNamePair("Ada", "Korwin", "NAMES_ARE_A_PAIR")).doesNotThrowAnyException();
        assertThatCode(() ->
            RecruitmentEformRules.assertNamePair(null, null, "NAMES_ARE_A_PAIR")).doesNotThrowAnyException();
        assertThatCode(() ->
            RecruitmentEformRules.assertNamePair(" ", "", "NAMES_ARE_A_PAIR")).doesNotThrowAnyException();

        assertThatThrownBy(() ->
            RecruitmentEformRules.assertNamePair("Ada", "", "NAMES_ARE_A_PAIR"))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("NAMES_ARE_A_PAIR");
        assertThatThrownBy(() ->
            RecruitmentEformRules.assertNamePair("Ada", null, "NAMES_ARE_A_PAIR"))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("NAMES_ARE_A_PAIR");
        assertThatThrownBy(() ->
            RecruitmentEformRules.assertNamePair(null, "Korwin", "NAMES_ARE_A_PAIR"))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("NAMES_ARE_A_PAIR");
    }

    @Test
    void admissionAgeFollowsTheOrdinaryStatutoryBand() {
        LocalDate declaration = LocalDate.of(2026, 11, 22);
        LocalDate sixteenToday = LocalDate.of(2010, 11, 22);
        LocalDate twentySixToday = LocalDate.of(2000, 11, 22);
        LocalDate tooYoung = LocalDate.of(2011, 11, 23);
        LocalDate tooOld = LocalDate.of(1999, 11, 22);

        assertThatCode(() ->
            RecruitmentEformRules.assertAdmissionAge(sixteenToday, declaration)).doesNotThrowAnyException();
        assertThatCode(() ->
            RecruitmentEformRules.assertAdmissionAge(twentySixToday, declaration)).doesNotThrowAnyException();

        assertThatThrownBy(() -> RecruitmentEformRules.assertAdmissionAge(tooYoung, declaration))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");
        assertThatThrownBy(() -> RecruitmentEformRules.assertAdmissionAge(tooOld, declaration))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");
    }

    @Test
    void pdfMagicIsCheckedByHeader() {
        assertThat(RecruitmentEformRules.isPdf("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(RecruitmentEformRules.isPdf("<html>".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(RecruitmentEformRules.isPdf("%PD".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void sha256IsLowercaseHexLength64() {
        byte[] content = "deklaracja".getBytes(StandardCharsets.UTF_8);

        assertThat(RecruitmentEformRules.sha256Hex(content))
            .hasSize(64)
            .isEqualTo(RecruitmentEformRules.sha256Hex(content))
            .matches("[0-9a-f]{64}");
        assertThat(RecruitmentEformRules.sha256Hex("token")).isNotEqualTo(
            RecruitmentEformRules.sha256Hex("tokeN"));
    }

    @Test
    void declarationDateLineCarriesThePolishMonthName() {
        String line = RecruitmentEformRules.declarationDateLine(LocalDate.of(2026, 11, 22));

        assertThat(line).contains("22 listopada 2026").contains("serwer");
    }
}
