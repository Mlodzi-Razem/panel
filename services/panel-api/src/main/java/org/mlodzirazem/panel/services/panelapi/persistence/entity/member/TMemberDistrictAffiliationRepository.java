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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface TMemberDistrictAffiliationRepository extends JpaRepository<TMemberDistrictAffiliation, Long> {
    @Query("""
        select a from TMemberDistrictAffiliation a
          join fetch a.district
        where a.member.id = :memberId
          and a.validFrom <= :today and (a.validTo is null or a.validTo > :today)
        order by a.kind, a.id
        """)
    List<TMemberDistrictAffiliation> findActiveByMemberId(
        @Param("memberId") Long memberId,
        @Param("today") LocalDate today
    );

    @Query("""
        select a from TMemberDistrictAffiliation a
          join fetch a.district
        where a.member.id in :memberIds
          and a.validFrom <= :today and (a.validTo is null or a.validTo > :today)
        """)
    List<TMemberDistrictAffiliation> findActiveByMemberIdIn(
        @Param("memberIds") Collection<Long> memberIds,
        @Param("today") LocalDate today
    );

    @Query("""
        select a from TMemberDistrictAffiliation a
          join fetch a.member m
        where a.district.id = :districtId
          and a.validFrom <= :today and (a.validTo is null or a.validTo > :today)
        """)
    List<TMemberDistrictAffiliation> findActiveByDistrictId(
        @Param("districtId") Long districtId,
        @Param("today") LocalDate today
    );

    @Query("""
        select a from TMemberDistrictAffiliation a
        where a.member.id = :memberId and a.validTo is null
        """)
    List<TMemberDistrictAffiliation> findOpenByMemberId(@Param("memberId") Long memberId);
}
