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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Writes small mandate CSVs into a temp directory for the importer tests. Invented data only —
 * member numbers of test fixtures, seeded body codes, no real PESEL numbers.
 */
final class MandateFile {
    static final String HEADER = "numer członka,kod organu,kod funkcji,od,uchwała";

    private final List<String[]> rows = new ArrayList<>();
    private String header = HEADER;

    static MandateFile file() {
        return new MandateFile();
    }

    MandateFile header(String value) {
        this.header = value;
        return this;
    }

    MandateFile row(String number, String body, String function, String from, String resolution) {
        return raw(new String[] {number, body, function, from, resolution});
    }

    MandateFile raw(String... cells) {
        rows.add(cells);
        return this;
    }

    Path write(Path directory) throws IOException {
        StringBuilder out = new StringBuilder(header).append("\r\n");

        for (String[] cells : rows) {
            out.append(String.join(",", Arrays.stream(cells).map(this::quote).collect(Collectors.toList())))
               .append("\r\n");
        }

        Path file = Files.createTempFile(directory, "mandates", ".csv");
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private String quote(@Nullable String cell) {
        String value = cell == null ? "" : cell;

        if (value.indexOf(',') >= 0 || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }
}
