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

package org.mlodzirazem.panel.services.panelapi.members.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class MemberFieldCodec {
    private final JsonMapper jsonMapper;

    String toJson(Map<MemberField, @Nullable Object> values) {
        ObjectNode node = jsonMapper.createObjectNode();

        values.forEach((field, value) -> {
            Object canonical = canonical(value);

            switch (canonical) {
                case null -> node.putNull(field.propertyName());
                case String s -> node.put(field.propertyName(), s);
                case List<?> list -> {
                    ArrayNode array = node.putArray(field.propertyName());
                    list.forEach(item -> array.add((String) item));
                }
                default -> throw new RuleViolationException("INVALID_VALUE", "Unsupported value type");
            }
        });

        return jsonMapper.writeValueAsString(node);
    }

    Map<MemberField, @Nullable Object> fromJson(String json) {
        Map<MemberField, @Nullable Object> result = new EnumMap<>(MemberField.class);
        JsonNode root = jsonMapper.readTree(json);

        for (MemberField field : MemberField.values()) {
            if (root.has(field.propertyName())) {
                result.put(field, fromNode(field, root.get(field.propertyName())));
            }
        }

        return result;
    }

    @Nullable Object canonical(@Nullable Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case LocalDate d -> d.toString();
            case MemberStatus e -> e.name();
            case MembershipType e -> e.name();
            case List<?> list -> list.stream().map(String::valueOf).toList();
            default -> throw new RuleViolationException("INVALID_VALUE", "Unsupported value type");
        };
    }

    private @Nullable Object fromNode(MemberField field, JsonNode node) {
        if (node.isNull()) {
            return field.type() == List.class ? new ArrayList<String>() : null;
        }

        if (field.type() == List.class) {
            List<String> items = new ArrayList<>();
            node.forEach(item -> items.add(item.asString()));
            return items;
        }

        if (field.type() == LocalDate.class) {
            return LocalDate.parse(node.asString());
        }

        if (field.type() == MemberStatus.class) {
            return MemberStatus.valueOf(node.asString());
        }

        if (field.type() == MembershipType.class) {
            return MembershipType.valueOf(node.asString());
        }

        return node.asString();
    }
}
