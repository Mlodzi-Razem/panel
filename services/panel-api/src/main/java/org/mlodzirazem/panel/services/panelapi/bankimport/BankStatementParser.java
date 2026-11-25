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

import static org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException.Code.MALFORMED;
import static org.mlodzirazem.panel.services.panelapi.bankimport.BankStatementParseException.Code.UNKNOWN_FORMAT;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class BankStatementParser {
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int SNIFF_BYTES = 16 * 1024;
    private static final Pattern MT940_HEADER = Pattern.compile("(?m)^:(?:20|25|28C|60[FM]):");

    public StatementFile parse(InputStream input, @Nullable String fileName) {
        byte[] data;
        try {
            data = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw new BankStatementParseException(MALFORMED, "The file could not be read");
        }
        if (data.length > MAX_BYTES) {
            throw new BankStatementParseException(MALFORMED, "The file is too large");
        }
        return switch (detect(data, fileName)) {
            case CAMT_053 -> Camt053Parser.parse(data);
            case MT940 -> Mt940Parser.parse(data);
        };
    }

    static BankStatementFormat detect(byte[] data, @Nullable String fileName) {
        int offset = 0;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        while (offset < data.length && (data[offset] == ' ' || data[offset] == '\t' || data[offset] == '\r' || data[offset] == '\n')) {
            offset++;
        }
        if (offset >= data.length) {
            throw new BankStatementParseException(UNKNOWN_FORMAT, "The file is empty");
        }
        if (data[offset] == '<') {
            return BankStatementFormat.CAMT_053;
        }
        String sample = new String(Arrays.copyOfRange(data, offset, Math.min(data.length, offset + SNIFF_BYTES)),
            StandardCharsets.ISO_8859_1).replace("\r\n", "\n").replace('\r', '\n');
        if (MT940_HEADER.matcher(sample).find()) {
            return BankStatementFormat.MT940;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xml")) {
            return BankStatementFormat.CAMT_053;
        }
        if (name.endsWith(".sta") || name.endsWith(".mt940") || name.endsWith(".940") || name.endsWith(".swi")) {
            return BankStatementFormat.MT940;
        }
        throw new BankStatementParseException(UNKNOWN_FORMAT, "The file is neither a camt.053 nor an MT940 statement");
    }
}
