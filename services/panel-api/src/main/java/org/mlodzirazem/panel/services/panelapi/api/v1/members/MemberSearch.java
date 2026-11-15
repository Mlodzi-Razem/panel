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

package org.mlodzirazem.panel.services.panelapi.api.v1.members;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
class MemberSearch {
    private static final String ACTIVE = " AND %1$s.valid_from <= :today AND (%1$s.valid_to IS NULL OR %1$s.valid_to > :today)";
    private static final String CONTACT_TEXT = "(COALESCE(m.preferred_name_first, '') || ' ' || COALESCE(m.preferred_name_middle, '')"
        + " || ' ' || COALESCE(m.preferred_name_last, '') || ' ' || COALESCE(m.pseudonym, ''))";

    private final JdbcClient jdbc;
    private final Clock clock;

    record Scope(boolean global, boolean globalFormal, Set<Long> contactDistricts, Set<Long> formalDistricts) {
        boolean empty() {
            return !global && contactDistricts.isEmpty() && formalDistricts.isEmpty();
        }
    }

    record Filter(
        @Nullable String q,
        @Nullable MemberStatus status,
        @Nullable MembershipType membershipType,
        @Nullable Long district
    ) {
    }

    record Result(List<Long> ids, long total) {
    }

    enum SortField {
        NAME, MEMBER_ID, STATUS, MEMBERSHIP_TYPE, DISTRICT
    }

    record Sort(SortField field, boolean descending) {
        static @Nullable Sort parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }

            String[] parts = raw.strip().split(":", 2);
            SortField field = switch (parts[0]) {
                case "name" -> SortField.NAME;
                case "memberId" -> SortField.MEMBER_ID;
                case "status" -> SortField.STATUS;
                case "membershipType" -> SortField.MEMBERSHIP_TYPE;
                case "district" -> SortField.DISTRICT;
                default -> null;
            };

            if (field == null) {
                return null;
            }

            return new Sort(field, parts.length > 1 && parts[1].equalsIgnoreCase("desc"));
        }
    }

    Result search(Scope scope, Filter filter, @Nullable Sort sort, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("today", LocalDate.now(clock));

        StringBuilder where = new StringBuilder(" WHERE TRUE");

        if (!scope.global()) {
            List<String> parts = new ArrayList<>();

            if (!scope.contactDistricts().isEmpty()) {
                parts.add("a.district_body_id IN (:contactDistricts)");
                params.put("contactDistricts", scope.contactDistricts());
            }

            if (!scope.formalDistricts().isEmpty()) {
                parts.add("(a.kind = CAST('PRIMARY' AS affiliation_kind) AND a.district_body_id IN (:formalDistricts))");
                params.put("formalDistricts", scope.formalDistricts());
            }

            where.append(" AND EXISTS (SELECT 1 FROM member_district_affiliations a WHERE a.member_id = m.id")
                 .append(ACTIVE.formatted("a"))
                 .append(" AND (").append(String.join(" OR ", parts)).append("))");
        }

        if (filter.district() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM member_district_affiliations d WHERE d.member_id = m.id")
                 .append(ACTIVE.formatted("d"))
                 .append(" AND d.district_body_id = :district)");
            params.put("district", filter.district());
        }

        if (filter.status() != null) {
            where.append(" AND m.status = CAST(:status AS member_status)");
            params.put("status", filter.status().name());
        }

        if (filter.membershipType() != null) {
            where.append(" AND m.membership_type = CAST(:membershipType AS membership_type)");
            params.put("membershipType", filter.membershipType().name());
        }

        boolean hasQuery = filter.q() != null && !filter.q().isBlank();
        String similarity = "word_similarity(unaccent(CAST(:q AS text)), unaccent(" + searchableText(scope, params) + "))";

        if (hasQuery) {
            String q = filter.q().strip();
            String text = searchableText(scope, params);
            where.append(" AND (").append(text).append(" ILIKE :pattern")
                 .append(" OR unaccent(").append(text).append(") ILIKE unaccent(CAST(:pattern AS text))")
                 .append(" OR ").append(similarity).append(" > 0.5)");
            params.put("q", q);
            params.put("pattern", "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }

        long total = jdbc.sql("SELECT COUNT(*) FROM members m" + where)
                         .params(params)
                         .query(Long.class)
                         .single();

        params.put("limit", size);
        params.put("offset", (long) page * size);

        String order = sort != null
            ? orderBy(scope, sort, params)
            : (hasQuery ? similarity + " DESC, " : "") + "m.preferred_name_last, m.preferred_name_first, m.id";

        List<Long> ids = jdbc.sql("SELECT m.id FROM members m" + where + " ORDER BY " + order
                                 + " LIMIT :limit OFFSET :offset")
                             .params(params)
                             .query(Long.class)
                             .list();

        return new Result(ids, total);
    }

    private static String orderBy(Scope scope, Sort sort, Map<String, Object> params) {
        String dir = sort.descending() ? " DESC" : " ASC";
        String byName = "m.preferred_name_last" + dir + ", m.preferred_name_first" + dir + ", m.id" + dir;

        String key = switch (sort.field()) {
            case NAME -> null;
            case MEMBER_ID -> formalOnly(scope, params,
                "CASE WHEN m.member_id ~ '^[0-9]+$' THEN LPAD(m.member_id, 20, '0') ELSE m.member_id END");
            case STATUS -> formalOnly(scope, params, "CAST(m.status AS text)");
            case MEMBERSHIP_TYPE -> formalOnly(scope, params, "CAST(m.membership_type AS text)");
            case DISTRICT -> "(SELECT b.name FROM member_district_affiliations p JOIN bodies b ON b.id = p.district_body_id"
                + " WHERE p.member_id = m.id" + ACTIVE.formatted("p")
                + " AND p.kind = CAST('PRIMARY' AS affiliation_kind) LIMIT 1)";
        };

        return key == null ? byName : key + dir + " NULLS LAST, " + byName;
    }

    private static String formalOnly(Scope scope, Map<String, Object> params, String expression) {
        return "(CASE WHEN " + formalVisible(scope, params) + " THEN " + expression + " END)";
    }

    private static String formalVisible(Scope scope, Map<String, Object> params) {
        if (scope.globalFormal()) {
            return "TRUE";
        }

        if (scope.formalDistricts().isEmpty()) {
            return "FALSE";
        }

        params.put("formalSearchDistricts", scope.formalDistricts());
        return "EXISTS (SELECT 1 FROM member_district_affiliations f WHERE f.member_id = m.id"
            + ACTIVE.formatted("f")
            + " AND f.kind = CAST('PRIMARY' AS affiliation_kind) AND f.district_body_id IN (:formalSearchDistricts))";
    }

    private static String searchableText(Scope scope, Map<String, Object> params) {
        String visible = formalVisible(scope, params);
        return switch (visible) {
            case "TRUE" -> "m.trgm_search";
            case "FALSE" -> CONTACT_TEXT;
            default -> "(CASE WHEN " + visible + " THEN m.trgm_search ELSE " + CONTACT_TEXT + " END)";
        };
    }
}
