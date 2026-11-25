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
public interface TFeePaymentAllocationRepository extends JpaRepository<TFeePaymentAllocation, Long> {
    @Query("select a from TFeePaymentAllocation a join fetch a.payment p join fetch a.due where p.member.id = :memberId")
    List<TFeePaymentAllocation> findByMemberId(@Param("memberId") Long memberId);

    @Query("select a.due.id, sum(a.amount) from TFeePaymentAllocation a where a.due.member.id = :memberId group by a.due.id")
    List<Object[]> sumByDueForMember(@Param("memberId") Long memberId);

    @Query("select a.payment.id, sum(a.amount) from TFeePaymentAllocation a where a.payment.member.id = :memberId group by a.payment.id")
    List<Object[]> sumByPaymentForMember(@Param("memberId") Long memberId);
}
