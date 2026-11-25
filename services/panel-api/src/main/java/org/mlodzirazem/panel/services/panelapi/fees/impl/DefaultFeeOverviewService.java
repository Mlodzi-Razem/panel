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

package org.mlodzirazem.panel.services.panelapi.fees.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.fees.DistrictScope;
import org.mlodzirazem.panel.services.panelapi.fees.FeeOverviewService;
import org.mlodzirazem.panel.services.panelapi.fees.MonthOverview;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class DefaultFeeOverviewService implements FeeOverviewService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final JdbcClient jdbc;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<MonthOverview> overview(int months, DistrictScope scope) {
        int count = Math.max(1, months);
        YearMonth last = YearMonth.now(clock);
        YearMonth first = last.minusMonths(count - 1L);
        Map<YearMonth, MonthOverview> byMonth = load(first, last, scope);
        List<MonthOverview> result = new ArrayList<>();

        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            result.add(byMonth.getOrDefault(month, new MonthOverview(month, ZERO, ZERO, ZERO, 0, 0, 0)));
        }

        return result;
    }

    private Map<YearMonth, MonthOverview> load(YearMonth first, YearMonth last, DistrictScope scope) {
        Map<YearMonth, MonthOverview> result = new HashMap<>();

        if (scope.districtIds() != null && scope.districtIds().isEmpty()) {
            return result;
        }

        LocalDate today = LocalDate.now(clock);
        String filter = scope.districtIds() == null ? "" : """
            AND EXISTS (
                SELECT 1 FROM member_district_affiliations f
                WHERE f.member_id = d.member_id AND f.kind = 'PRIMARY'
                  AND f.valid_from <= :today AND (f.valid_to IS NULL OR f.valid_to > :today)
                  AND f.district_body_id IN (:districts)
            )
            """;

        var statement = jdbc.sql("""
            SELECT due_month, SUM(amount) AS expected, SUM(paid) AS collected, COUNT(*) AS members,
                   COUNT(*) FILTER (WHERE paid >= amount) AS members_paid
            FROM (
                SELECT d.due_month, d.amount,
                       COALESCE((SELECT SUM(a.amount)
                                 FROM fee_payment_allocations a
                                          JOIN fee_payments p ON p.id = a.payment_id
                                 WHERE a.due_id = d.id AND p.reversed_at IS NULL), 0) AS paid
                FROM fee_dues d
                WHERE d.status <> 'CANCELLED' AND d.due_month BETWEEN :from AND :to
            """ + filter + """
            ) due
            GROUP BY due_month
            """)
            .param("from", first.atDay(1))
            .param("to", last.atDay(1));

        if (scope.districtIds() != null) {
            statement = statement.param("today", today).param("districts", scope.districtIds());
        }

        statement.query((rs, n) -> {
            YearMonth month = YearMonth.from(rs.getDate("due_month").toLocalDate());
            BigDecimal expected = rs.getBigDecimal("expected").setScale(2);
            BigDecimal collected = rs.getBigDecimal("collected").setScale(2);
            int members = rs.getInt("members");
            int paid = rs.getInt("members_paid");

            result.put(month, new MonthOverview(month, expected, collected, expected.subtract(collected), members, paid,
                members - paid));

            return month;
        }).list();

        return result;
    }
}
