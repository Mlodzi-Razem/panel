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

package org.mlodzirazem.panel.services.panelapi.api.v1.common;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MemberFieldParser {
    public Map<MemberField, @Nullable Object> parse(@Nullable Map<String, JsonNode> raw) {
        Map<MemberField, @Nullable Object> result = new EnumMap<>(MemberField.class);

        if (raw == null) {
            return result;
        }

        List<String> unknown = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        raw.forEach((name, node) -> {
            MemberField field = MemberField.byPropertyName(name).orElse(null);

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

    private static List<String> sorted(List<String> names) {
        return names.stream().sorted().toList();
    }

    private static @Nullable Object convert(MemberField field, @Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        Class<?> type = field.type();

        if (type == List.class) {
            if (!node.isArray()) {
                throw new IllegalArgumentException();
            }

            List<String> items = new ArrayList<>();

            for (JsonNode item : node) {
                if (!item.isString()) {
                    throw new IllegalArgumentException();
                }

                String value = item.asString().strip();

                if (!value.isEmpty()) {
                    items.add(value);
                }
            }

            return items;
        }

        if (!node.isString()) {
            throw new IllegalArgumentException();
        }

        String text = node.asString().strip();

        if (text.isEmpty()) {
            return null;
        }

        if (type == LocalDate.class) {
            return LocalDate.parse(text);
        }

        if (type == MemberStatus.class) {
            return MemberStatus.valueOf(text);
        }

        if (type == MembershipType.class) {
            return MembershipType.valueOf(text);
        }

        return text;
    }
}
