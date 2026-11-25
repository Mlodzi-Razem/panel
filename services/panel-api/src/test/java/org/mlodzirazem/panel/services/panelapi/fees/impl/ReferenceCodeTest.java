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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceCodeTest {
    @Test
    void checkDigitFollowsIso7064Mod1110() {
        assertThat(ReferenceCode.checkDigit("6592997048")).isEqualTo(9);
        assertThat(ReferenceCode.format("7001")).isEqualTo("MR-7001-" + ReferenceCode.checkDigit("7001"));
    }

    @Test
    void parsingToleratesCaseSpacesAndSeparators() {
        int check = ReferenceCode.checkDigit("7001");

        for (String title : new String[]{
            "MR-7001-" + check, "mr-7001-" + check, "Skladka mr 7001 " + check + " za maj", "M R - 7001 - " + check,
            "MR7001" + check, "SKLADKA/MR_7001_" + check + "."}) {
            assertThat(ReferenceCode.validNumbers(title)).as(title).containsExactly("7001");
        }
    }

    @Test
    void rejectsAWrongCheckDigitAndNoise() {
        int wrong = (ReferenceCode.checkDigit("7001") + 1) % 10;

        assertThat(ReferenceCode.validNumbers("MR-7001-" + wrong)).isEmpty();
        assertThat(ReferenceCode.validNumbers("składka za maj")).isEmpty();
        assertThat(ReferenceCode.validNumbers("MR-7001")).isEmpty();
    }
}
