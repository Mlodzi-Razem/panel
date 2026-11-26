/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclarationPropertiesTest {
    private static DeclarationProperties on(String webUrl) {
        DeclarationProperties properties = new DeclarationProperties();
        properties.setWebUrl(webUrl);
        return properties;
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://dev.panel.example.org", "https://dev.panel.example.org/",
        "https://dev.panel.example.org//"})
    void formUrlIsAbsoluteAndHasExactlyOneSeparator(String webUrl) {
        assertThatCode(on(webUrl)::requireAbsoluteWebUrl).doesNotThrowAnyException();
        assertThat(on(webUrl).formUrl("abc"))
            .isEqualTo("https://dev.panel.example.org/rekrutacja/deklaracja/abc");
    }

    @Test
    void formRouteIsARouteThatPanelWebServes() {
        // the link used to point at the API path, which panel-web answers with 404
        assertThat(Path.of("../panel-web/app/(public)" + DeclarationProperties.FORM_ROUTE + "[token]/page.tsx"))
            .exists();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "/public/declaration-form/", "dev.panel.example.org", "ftp://panel.example.org",
        "https://panel.example.org?x=1", "https://panel.example.org#form", "https://panel example.org"})
    void aRelativeOrMalformedWebAddressStopsTheBoot(String webUrl) {
        assertThatThrownBy(on(webUrl)::requireAbsoluteWebUrl)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PANEL_WEB_URL");
    }
}
