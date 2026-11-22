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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Paged case listing for the intake board, following the idiom of {@code MemberSearch}:
 * a small scope record plus explicit WHERE fragments, so district permissions stay in SQL.
 */
@Component
@RequiredArgsConstructor
class RecruitmentCaseSearch {
    private final JdbcClient jdbc;

    record Scope(boolean global, Set<Long> readDistricts) {
        boolean empty() {
            return !global && readDistricts.isEmpty();
        }
    }

    record Filter(
        @Nullable Long district,
        @Nullable RecruitmentCaseStatus status,
        @Nullable Boolean open
    ) {
    }

    record Result(List<Long> ids, long total) {
    }

    Result search(Scope scope, Filter filter, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE TRUE");

        if (!scope.global()) {
            where.append(" AND c.district_body_id IN (:districts)");
            params.put("districts", scope.readDistricts());
        }

        if (filter.district() != null) {
            where.append(" AND c.district_body_id = :district");
            params.put("district", filter.district());
        }

        if (filter.status() != null) {
            where.append(" AND c.status = CAST(:status AS recruitment_case_status)");
            params.put("status", filter.status().name());
        }

        if (filter.open() != null) {
            where.append(" AND c.closed_at IS").append(filter.open() ? " NULL" : " NOT NULL");
        }

        long total = jdbc.sql("SELECT COUNT(*) FROM recruitment_cases c" + where)
                         .params(params)
                         .query(Long.class)
                         .single();

        params.put("limit", size);
        params.put("offset", (long) page * size);

        List<Long> ids = jdbc.sql(
                "SELECT c.id FROM recruitment_cases c" + where
                    + " ORDER BY c.created_at DESC, c.id DESC LIMIT :limit OFFSET :offset")
                             .params(params)
                             .query(Long.class)
                             .list();

        return new Result(ids, total);
    }
}
