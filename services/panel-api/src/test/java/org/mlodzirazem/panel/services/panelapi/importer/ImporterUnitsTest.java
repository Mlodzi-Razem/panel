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

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImporterUnitsTest {
    private final DistrictMatcher matcher = new DistrictMatcher(List.of(
        new DistrictMatcher.District(1, "Okręg Mazowiecki", "district-mazowieckie"),
        new DistrictMatcher.District(2, "Okręg Śląski", "district-slaskie"),
        new DistrictMatcher.District(3, "Okręg Warmińsko-Mazurski", "district-warminsko-mazurskie"),
        new DistrictMatcher.District(4, "Okręg Pomorski", "district-pomorskie"),
        new DistrictMatcher.District(5, "Okręg Zachodniopomorski", "district-zachodniopomorskie"),
        new DistrictMatcher.District(6, "Okręg Zagranica", "district-zagranica")
    ));

    @Test
    void districtNamesMatchIgnoringCaseDiacriticsOkregAndForm() {
        for (String variant : List.of("Mazowiecki", "mazowieckie", "Okręg Mazowiecki", "OKREG MAZOWIECKI",
            "okręg mazowieckiego", " Mazowieckie ")) {
            assertThat(matcher.match(variant)).as(variant).contains(1L);
        }

        assertThat(matcher.match("Śląskie")).contains(2L);
        assertThat(matcher.match("slaski")).contains(2L);
        assertThat(matcher.match("warminsko-mazurskie")).contains(3L);
        assertThat(matcher.match("Warmińsko Mazurski")).contains(3L);
        assertThat(matcher.match("pomorskie")).contains(4L);
        assertThat(matcher.match("zachodniopomorskie")).contains(5L);
        assertThat(matcher.match("Zagranica")).contains(6L);
        assertThat(matcher.match("Okręg Zagranica")).contains(6L);
        assertThat(matcher.match("Narnia")).isEmpty();
        assertThat(matcher.match("okręg")).isEmpty();
    }

    @Test
    void peselChecksumAndBirthDate() {
        String valid = Sheet.pesel(LocalDate.of(2005, 5, 5), 1234);

        assertThat(Pesel.hasFormat(valid)).isTrue();
        assertThat(Pesel.checksumValid(valid)).isTrue();
        assertThat(Pesel.birthDate(valid)).isEqualTo(Optional.of(LocalDate.of(2005, 5, 5)));
        assertThat(Pesel.birthDate(Sheet.pesel(LocalDate.of(1999, 12, 31), 1))).contains(LocalDate.of(1999, 12, 31));

        String broken = valid.substring(0, 10) + (valid.charAt(10) == '0' ? '1' : '0');
        assertThat(Pesel.checksumValid(broken)).isFalse();
        assertThat(Pesel.hasFormat("1234")).isFalse();
    }

    @Test
    void parsesSupportedDateFormats() {
        assertThat(Text.parseDate("2005-05-06")).contains(LocalDate.of(2005, 5, 6));
        assertThat(Text.parseDate("06.05.2005")).contains(LocalDate.of(2005, 5, 6));
        assertThat(Text.parseDate("6/5/2005")).contains(LocalDate.of(2005, 5, 6));
        assertThat(Text.parseDate("31.02.2005")).isEmpty();
        assertThat(Text.parseDate("05/06/05")).isEmpty();
    }

    @Test
    void splitsListsOnCommaAndSemicolon() {
        assertThat(Text.splitList(" 111 ,222;333 ;; 111")).containsExactly("111", "222", "333");
        assertThat(Text.splitList(null)).isEmpty();
    }

    @Test
    void detectsDelimiter() {
        assertThat(CsvTable.detectDelimiter("a;b;c\n1,2,3")).isEqualTo(';');
        assertThat(CsvTable.detectDelimiter("a\tb\tc")).isEqualTo('\t');
        assertThat(CsvTable.detectDelimiter("\"a;b\",c,d")).isEqualTo(',');
    }
}
