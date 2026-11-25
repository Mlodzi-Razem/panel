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

package org.mlodzirazem.panel.services.panelapi.bankimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IbansTest {
    private static final String IBAN = "PL61109010140000071219812874";

    @Test
    void normalisesSpacesAndCase() {
        assertThat(Ibans.normalise("pl61 1090 1014 0000 0712 1981 2874")).isEqualTo(IBAN);
        assertThat(Ibans.normalise(" PL61-1090-1014-0000-0712-1981-2874 ")).isEqualTo(IBAN);
    }

    @Test
    void convertsPolishNrbToIban() {
        assertThat(Ibans.normalise("61109010140000071219812874")).isEqualTo(IBAN);
        assertThat(Ibans.normalise("61 1090 1014 0000 0712 1981 2874")).isEqualTo(IBAN);
    }

    @Test
    void acceptsForeignIbans() {
        assertThat(Ibans.normalise("de89 3704 0044 0532 0130 00")).isEqualTo("DE89370400440532013000");
    }

    @Test
    void rejectsInvalidChecksum() {
        assertThat(Ibans.tryNormalise("PL61109010140000071219812875")).isEmpty();
        assertThat(Ibans.tryNormalise("61109010140000071219812875")).isEmpty();
        assertThatThrownBy(() -> Ibans.normalise("PL61109010140000071219812875"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("1090");
    }

    @Test
    void rejectsMalformedValues() {
        assertThat(Ibans.tryNormalise(null)).isEmpty();
        assertThat(Ibans.tryNormalise("")).isEmpty();
        assertThat(Ibans.tryNormalise("not an iban")).isEmpty();
        assertThat(Ibans.tryNormalise("PL6110901014000007121981287")).isEmpty();
        assertThat(Ibans.tryNormalise("6110901014000007121981287")).isEmpty();
        assertThat(Ibans.isValid(IBAN)).isTrue();
        assertThat(Ibans.isValid("PL00")).isFalse();
    }
}
