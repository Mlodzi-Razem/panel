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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Set;

/**
 * Paged case listing for the intake board, following the idiom of {@code MemberSearch}:
 * a small scope record plus explicit WHERE fragments, so district permissions stay in SQL.
 */
@Component
@RequiredArgsConstructor
@org.springframework.boot.context.properties.EnableConfigurationProperties(RecruitmentViewProperties.class)
class RecruitmentCaseSearch {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final RecruitmentViewProperties viewProperties;

    /** Owned by the anonymisation job (C1): the retention period of rejected cases. */
    @org.springframework.beans.factory.annotation.Value("${panel.recruitment.retention.rejected-case-days:365}")
    private int rejectedCaseDays = 365;

    record Scope(boolean global, Set<Long> readDistricts) {
        boolean empty() {
            return !global && readDistricts.isEmpty();
        }
    }

    record Filter(
        @Nullable Long district,
        @Nullable RecruitmentCaseStatus status,
        @Nullable Boolean open,
        @Nullable RecruitmentCaseView view
    ) {
        Filter(@Nullable Long district, @Nullable RecruitmentCaseStatus status, @Nullable Boolean open) {
            this(district, status, open, null);
        }

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

        if (filter.view() != null) {
            where.append(" AND ").append(viewCondition(filter.view(), params));
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

    /** Case count per predefined view within the caller's scope (and the optional district). */
    Map<RecruitmentCaseView, Long> viewCounts(Scope scope, @Nullable Long district) {
        Map<RecruitmentCaseView, Long> counts = new EnumMap<>(RecruitmentCaseView.class);

        for (RecruitmentCaseView view : RecruitmentCaseView.values()) {
            counts.put(view, search(scope, new Filter(district, null, null, view), 0, 1).total());
        }

        return counts;
    }

    /** SQL condition of one view; the cutoffs come from the single Clock bean. */
    private String viewCondition(RecruitmentCaseView view, Map<String, Object> params) {
        Instant now = clock.instant();

        return switch (view) {
            case TO_CONDUCT -> "c.closed_at IS NULL";
            case PROBABLY_NO_DECLARATION -> {
                params.put("noDeclarationCutoff", Timestamp.from(
                    now.minusSeconds(86_400L * viewProperties.getAwaitingDeclarationDays())));
                yield "c.status = 'AWAITING_DECLARATION' AND c.closed_at IS NULL AND COALESCE("
                    + "(SELECT MAX(e.created_at) FROM recruitment_case_events e"
                    + " WHERE e.case_id = c.id AND e.event_type = 'STATUS_CHANGED'), c.created_at)"
                    + " <= :noDeclarationCutoff";
            }
            case RECOMMENDED -> "c.status = 'ZO_RECOMMENDED'";
            case ZK_VOTING -> "c.status = 'ZK_VOTING'";
            case TO_DELETE -> {
                params.put("toDeleteBefore", LocalDate.ofInstant(now, ZoneOffset.UTC)
                    .minusDays(rejectedCaseDays));
                yield "c.status = 'REJECTED' AND c.anonymized_at IS NULL AND c.closed_at <= :toDeleteBefore";
            }
        };
    }
}
