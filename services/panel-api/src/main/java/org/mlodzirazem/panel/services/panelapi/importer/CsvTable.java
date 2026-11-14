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

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;
import tools.jackson.dataformat.csv.CsvSchema;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CsvTable {
    record Row(int number, String[] cells) {
    }

    private final Map<Column, Integer> columns;
    private final List<String> unknownHeaders;
    private final List<String> duplicateHeaders;
    private final List<Row> rows;

    private CsvTable(
        Map<Column, Integer> columns,
        List<String> unknownHeaders,
        List<String> duplicateHeaders,
        List<Row> rows
    ) {
        this.columns = columns;
        this.unknownHeaders = unknownHeaders;
        this.duplicateHeaders = duplicateHeaders;
        this.rows = rows;
    }

    boolean has(Column column) {
        return columns.containsKey(column);
    }

    @Nullable String get(Row row, Column column) {
        Integer index = columns.get(column);

        if (index == null || index >= row.cells().length) {
            return null;
        }

        return Text.blankToNull(row.cells()[index]);
    }

    List<String> unknownHeaders() {
        return unknownHeaders;
    }

    List<String> duplicateHeaders() {
        return duplicateHeaders;
    }

    List<Row> rows() {
        return rows;
    }

    static CsvTable read(Path file) {
        String content = decode(file);

        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1);
        }

        char delimiter = detectDelimiter(content);

        List<String[]> records;

        try {
            CsvMapper mapper = CsvMapper.builder()
                                        .enable(CsvReadFeature.WRAP_AS_ARRAY)
                                        .build();
            CsvSchema schema = CsvSchema.emptySchema().withColumnSeparator(delimiter);

            try (MappingIterator<String[]> iterator = mapper.readerFor(String[].class)
                                                            .with(schema)
                                                            .readValues(new StringReader(content))) {
                records = iterator.readAll();
            }
        } catch (JacksonException ex) {
            throw new ImportFatalException("UNPARSEABLE_FILE", "the CSV structure is invalid");
        }

        if (records.isEmpty()) {
            throw new ImportFatalException("EMPTY_FILE", "the file has no header row");
        }

        return build(records);
    }

    private static CsvTable build(List<String[]> records) {
        String[] header = records.getFirst();
        Map<Column, Integer> columns = new EnumMap<>(Column.class);
        List<String> unknown = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        for (int i = 0; i < header.length; i++) {
            String text = header[i] == null ? "" : header[i].strip();
            var column = Column.byHeader(text);

            if (column.isEmpty()) {
                unknown.add(text.isEmpty() ? "(column " + (i + 1) + ")" : text);
            } else if (columns.containsKey(column.get())) {
                duplicates.add(column.get().label());
            } else {
                columns.put(column.get(), i);
            }
        }

        Set<String> missing = new LinkedHashSet<>();

        for (Column column : Column.values()) {
            if (column.required() && !columns.containsKey(column)) {
                missing.add(column.label().isEmpty() ? "member_id" : column.label());
            }
        }

        if (!missing.isEmpty()) {
            throw new ImportFatalException("MISSING_COLUMNS", String.join(", ", missing));
        }

        List<Row> rows = new ArrayList<>();

        for (int i = 1; i < records.size(); i++) {
            String[] cells = records.get(i);

            if (java.util.Arrays.stream(cells).allMatch(c -> c == null || c.isBlank())) {
                continue;
            }

            rows.add(new Row(i + 1, cells));
        }

        return new CsvTable(columns, unknown, duplicates, rows);
    }

    private static String decode(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);

            return StandardCharsets.UTF_8.newDecoder()
                                         .onMalformedInput(CodingErrorAction.REPORT)
                                         .onUnmappableCharacter(CodingErrorAction.REPORT)
                                         .decode(ByteBuffer.wrap(bytes))
                                         .toString();
        } catch (CharacterCodingException ex) {
            throw new ImportFatalException("NOT_UTF8", "the file is not valid UTF-8");
        } catch (IOException ex) {
            throw new ImportFatalException("UNREADABLE_FILE", "the file cannot be read");
        }
    }

    static char detectDelimiter(String content) {
        int[] counts = new int[3];
        char[] candidates = {',', ';', '\t'};
        boolean quoted = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '\n' || c == '\r')) {
                break;
            } else if (!quoted) {
                for (int k = 0; k < candidates.length; k++) {
                    if (c == candidates[k]) {
                        counts[k]++;
                    }
                }
            }
        }

        int best = 0;

        for (int k = 1; k < counts.length; k++) {
            if (counts[k] > counts[best]) {
                best = k;
            }
        }

        return candidates[best];
    }
}
