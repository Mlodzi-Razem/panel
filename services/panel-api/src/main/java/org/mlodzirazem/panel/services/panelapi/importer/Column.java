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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

enum Column {
    MEMBER_ID("member_id", true, ""),
    FORMAL_FIRST("Imię", true),
    FORMAL_LAST("Nazwisko", true),
    PREFERRED_FIRST("Preferowane imię", false),
    PREFERRED_LAST("Preferowane nazwisko", false),
    PRONOUNS("zaimki", false),
    PHONES("nr tel", false),
    PRIVATE_EMAILS("Prywatny adres e-mail", false),
    PRIMARY_DISTRICT("Okręg stowarzyszenia", true),
    /** The Sejm constituency number (1–41), not an MR district: recognised but never imported. */
    SEJM_CONSTITUENCY("Okręg wyborczy", false),
    PROVINCE("powiat", false),
    CITY("miasto", false),
    ADDRESS("adres", false),
    INSTITUTION("uczelnia/pracodawca", false),
    BIRTH_DATE("Data urodzenia", true),
    PESEL("pesel", false),
    SOCIAL("Facebook", false),
    MEMBER_SINCE("data przyjęcia", false),
    DOMAIN_EMAIL("Adres konta w organizacji", false),
    DECLARATION("Deklaracja członkowska", false),
    AGE("Wiek", false),
    CAN_VOTE("Czy może głosować", false),
    ZEUS("Zeus CSV", false),
    REMOVAL_VOTE("Głosowanie nad usunieciem", false);

    private static final Map<String, Column> BY_KEY = Arrays.stream(values()).collect(
        Collectors.toMap(c -> c.key, Function.identity()));

    private final String label;
    private final boolean required;
    private final String key;

    Column(String label, boolean required) {
        this(label, required, label);
    }

    Column(String label, boolean required, String headerText) {
        this.label = label;
        this.required = required;
        this.key = Text.headerKey(headerText.isEmpty() ? "" : headerText);
    }

    String label() {
        return label;
    }

    boolean required() {
        return required;
    }

    static Optional<Column> byHeader(String header) {
        String key = Text.headerKey(header);

        if (key.equals("memberid")) {
            return Optional.of(MEMBER_ID);
        }

        return Optional.ofNullable(BY_KEY.get(key));
    }
}
