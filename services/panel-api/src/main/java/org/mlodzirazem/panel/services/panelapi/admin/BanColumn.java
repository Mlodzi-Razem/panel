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

package org.mlodzirazem.panel.services.panelapi.admin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Columns of the ban file (art. 9 ust. 5): the PESEL of the banned person, the optional start date
 * (blank = the day the uchwała is recorded), the mandatory end date, the signature of the ZK
 * uchwała and its mandatory reason.
 */
enum BanColumn {
    PESEL("pesel", true, new String[] {"pesel", "numer pesel", "pesel kandydata", "personal id",
        "personal identifier"}),
    FROM("from", false, new String[] {"from", "od", "od dnia", "data od", "od (data)", "obowiązuje od"}),
    UNTIL("until", true, new String[] {"until", "do", "do dnia", "data do", "do (data)", "obowiązuje do"}),
    RESOLUTION("resolution", true, new String[] {"resolution", "uchwała", "nr uchwały", "numer uchwały",
        "uchwała nr", "sygnatura uchwały", "decision reference"}),
    REASON("reason", true, new String[] {"reason", "powód", "powod", "uzasadnienie", "przyczyna"});

    private static final Map<String, BanColumn> BY_KEY = new HashMap<>();

    static {
        for (BanColumn column : values()) {
            column.keys().distinct().forEach(k -> BY_KEY.put(k, column));
        }
    }

    private final String label;
    private final String[] headers;
    private final String[] keys;
    private final boolean required;

    BanColumn(String label, boolean required, String[] headers) {
        this.label = label;
        this.headers = headers;
        this.keys = Arrays.stream(headers).map(BanText::headerKey).toArray(String[]::new);
        this.required = required;
    }

    String label() {
        return label;
    }

    boolean required() {
        return required;
    }

    Stream<String> keys() {
        return Arrays.stream(keys);
    }

    static BanColumn byKey(String key) {
        return BY_KEY.get(key);
    }
}
