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
 * Columns of the owner's list of current functions (wave 0, package A1): who holds which function, in
 * which body, since when, under which resolution (uchwała). Every column is required — a mandate
 * without a decision reference cannot land in the register (the reference is the audit anchor of
 * art. 18 integrity). Headers are matched case- and diacritics-insensitively against a set of
 * synonyms so the owner's CSV needs no renaming.
 */
enum MandateColumn {
    MEMBER("member", "numer członka", "nr członka", "numer", "numer legitymacji", "nr legitymacji", "member id",
        "member_id"),
    BODY("body", "kod organu", "organ", "body code"),
    FUNCTION("function", "kod funkcji", "funkcja", "typ mandatu", "typ funkcji", "typ", "member type"),
    FROM("from", "od", "od kiedy", "od dnia", "data od", "od (data)"),
    RESOLUTION("resolution", "uchwała", "numer uchwały", "nr uchwały", "uchwała nr", "rezolucja",
        "decision reference");

    private static final Map<String, MandateColumn> BY_KEY = new HashMap<>();

    static {
        for (MandateColumn column : values()) {
            column.keys().distinct().forEach(k -> BY_KEY.put(k, column));
        }
    }

    private final String label;
    private final String[] headers;
    private final String[] keys;

    MandateColumn(String label, String... headers) {
        this.label = label;
        this.headers = headers;
        this.keys = Arrays.stream(headers).map(MandateText::headerKey).toArray(String[]::new);
    }

    String label() {
        return label;
    }

    Stream<String> keys() {
        return Arrays.stream(keys);
    }

    static MandateColumn byKey(String key) {
        return BY_KEY.get(key);
    }
}
