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

package org.mlodzirazem.panel.services.panelapi.audit;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public record AuditPayload(
    Map<String, Long> ids,
    List<String> fields,
    @Nullable String outcome
) {
    public AuditPayload {
        ids = Map.copyOf(ids);
        fields = List.copyOf(fields);
    }

    public static AuditPayload of(String idName, long id) {
        return new AuditPayload(Map.of(idName, id), List.of(), null);
    }

    public AuditPayload withId(String idName, long id) {
        Map<String, Long> copy = new LinkedHashMap<>(ids);
        copy.put(idName, id);
        return new AuditPayload(copy, fields, outcome);
    }

    public AuditPayload withFields(Collection<String> fieldNames) {
        return new AuditPayload(ids, List.copyOf(new TreeSet<>(fieldNames)), outcome);
    }

    public AuditPayload withOutcome(String newOutcome) {
        return new AuditPayload(ids, fields, newOutcome);
    }
}
