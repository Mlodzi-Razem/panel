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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class Sheet {
    static final List<String> HEADER = List.of("", "Imię", "Nazwisko", "Preferowane imię", "Preferowane nazwisko",
        "zaimki", "nr tel", "Prywatny adres e-mail", "Okręg stowarzyszenia", "Okręg wyborczy", "powiat", "miasto",
        "adres", "uczelnia/pracodawca", "Data urodzenia", "pesel", "Facebook", "data przyjęcia",
        "Adres konta w organizacji", "Deklaracja członkowska", "Wiek", "Czy może głosować", "Zeus CSV",
        "Głosowanie nad usunieciem");

    private final List<Map<String, String>> rows = new ArrayList<>();
    private char delimiter = ',';
    private boolean bom;
    private List<String> header = HEADER;

    Sheet delimiter(char value) {
        this.delimiter = value;
        return this;
    }

    Sheet bom() {
        this.bom = true;
        return this;
    }

    Sheet header(List<String> value) {
        this.header = value;
        return this;
    }

    Sheet add(Map<String, String> row) {
        rows.add(row);
        return this;
    }

    Sheet add(String id, String first, String last, String district) {
        return add(row(id, first, last, district));
    }

    static Map<String, String> row(String id, String first, String last, String district) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("", id);
        row.put("Imię", first);
        row.put("Nazwisko", last);
        row.put("Okręg stowarzyszenia", district);
        row.put("Data urodzenia", "2005-05-05");
        return row;
    }

    static String pesel(LocalDate birth, int serial) {
        int month = birth.getMonthValue() + (birth.getYear() >= 2000 ? 20 : 0);
        String base = "%02d%02d%02d%04d".formatted(birth.getYear() % 100, month, birth.getDayOfMonth(), serial);
        int[] weights = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};
        int sum = 0;

        for (int i = 0; i < 10; i++) {
            sum += (base.charAt(i) - '0') * weights[i];
        }

        return base + (10 - sum % 10) % 10;
    }

    Path write(Path directory) throws IOException {
        StringBuilder out = new StringBuilder();

        if (bom) {
            out.append('﻿');
        }

        out.append(line(header)).append("\r\n");

        for (Map<String, String> row : rows) {
            out.append(line(header.stream().map(h -> row.getOrDefault(h, "")).toList())).append("\r\n");
        }

        Path file = Files.createTempFile(directory, "sheet", ".csv");
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private String line(List<String> cells) {
        return cells.stream().map(this::quote).collect(Collectors.joining(String.valueOf(delimiter)));
    }

    private String quote(String cell) {
        if (cell.indexOf(delimiter) >= 0 || cell.contains("\"") || cell.contains("\n")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }

        return cell;
    }
}
