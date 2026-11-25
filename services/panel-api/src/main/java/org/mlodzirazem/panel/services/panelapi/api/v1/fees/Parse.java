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

package org.mlodzirazem.panel.services.panelapi.api.v1.fees;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

final class Parse {
    private static final int MAX_PAGE_SIZE = 100;

    private Parse() {
    }

    static BigDecimal money(String field, @Nullable String raw) {
        String value = required(field, raw);

        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            throw invalid(field);
        }
    }

    static YearMonth month(String field, @Nullable String raw) {
        try {
            return YearMonth.parse(required(field, raw).strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw invalid(field);
        }
    }

    static @Nullable YearMonth optionalMonth(String field, @Nullable String raw) {
        return raw == null || raw.isBlank() ? null : month(field, raw);
    }

    static LocalDate date(String field, @Nullable String raw) {
        try {
            return LocalDate.parse(required(field, raw).strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw invalid(field);
        }
    }

    static <E extends Enum<E>> E enumOf(String field, @Nullable String raw, Class<E> type) {
        try {
            return Enum.valueOf(type, required(field, raw).strip());
        } catch (IllegalArgumentException e) {
            throw invalid(field);
        }
    }

    static String required(String field, @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("FIELD_REQUIRED", field + " is required", List.of(field));
        }

        return raw;
    }

    static long requiredId(String field, @Nullable Long id) {
        if (id == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", field + " is required", List.of(field));
        }

        return id;
    }

    static int page(int page) {
        return Math.max(0, page);
    }

    static int size(int size) {
        return Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    private static ApiException invalid(String field) {
        return ApiException.badRequest("INVALID_VALUE", "Invalid value", List.of(field));
    }
}
