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

package org.mlodzirazem.panel.services.panelapi.api.v1.recruitment;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCandidateField;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Binds the JSON candidate object of a create/update request into the
 * {@link RecruitmentCandidateField} map, following {@code MemberFieldParser}: unknown keys and
 * wrong value types are rejected, JSON null and blank text clear the field.
 */
@Component
public class RecruitmentCandidateParser {
    public Map<RecruitmentCandidateField, @Nullable Object> parse(@Nullable Map<String, JsonNode> raw) {
        Map<RecruitmentCandidateField, @Nullable Object> result = new EnumMap<>(RecruitmentCandidateField.class);

        if (raw == null) {
            return result;
        }

        List<String> unknown = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        raw.forEach((name, node) -> {
            RecruitmentCandidateField field = byPropertyName(name);

            if (field == null) {
                unknown.add(name);
                return;
            }

            try {
                result.put(field, convert(field, node));
            } catch (RuntimeException e) {
                invalid.add(name);
            }
        });

        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("UNKNOWN_FIELD", "Unknown fields", sorted(unknown));
        }

        if (!invalid.isEmpty()) {
            throw ApiException.badRequest("INVALID_VALUE", "Invalid values", sorted(invalid));
        }

        return result;
    }

    private static @Nullable RecruitmentCandidateField byPropertyName(String propertyName) {
        for (RecruitmentCandidateField field : RecruitmentCandidateField.values()) {
            if (field.propertyName().equals(propertyName)) {
                return field;
            }
        }

        return null;
    }

    private static List<String> sorted(List<String> names) {
        return names.stream().sorted().toList();
    }

    private static @Nullable Object convert(RecruitmentCandidateField field, @Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isString()) {
            throw new IllegalArgumentException();
        }

        String text = node.asString().strip();

        if (text.isEmpty()) {
            return null;
        }

        if (field.type() == LocalDate.class) {
            return LocalDate.parse(text);
        }

        return text;
    }
}
