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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.fee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TFeeDueRepository extends JpaRepository<TFeeDue, Long> {
    List<TFeeDue> findByMemberIdOrderByDueMonthAsc(Long memberId);

    List<TFeeDue> findByMemberIdAndStatusOrderByDueMonthAsc(Long memberId, DueStatus status);

    boolean existsByTierIdAndDueMonthGreaterThanEqual(Long tierId, LocalDate dueMonth);

    @Query("select distinct d.member.id from TFeeDue d where d.status = :status and d.dueMonth <= :maxMonth")
    List<Long> findMemberIdsWithDuesUpTo(@Param("status") DueStatus status, @Param("maxMonth") LocalDate maxMonth);
}
