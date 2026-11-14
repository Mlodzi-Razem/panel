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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.body;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface TBodyMembershipRepository extends JpaRepository<TBodyMembership, Long> {
    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.body b
          join fetch b.bodyTemplate
          join fetch bm.memberType mt
          join fetch mt.permissionGroup
        where bm.member.id = :memberId
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findAllByMemberId(@Param("memberId") Long memberId);

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.body b
          join fetch b.bodyTemplate
          join fetch bm.memberType mt
          join fetch mt.permissionGroup
        where bm.member.id = :memberId
          and bm.validFrom <= :today and (bm.validTo is null or bm.validTo > :today)
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findActiveByMemberId(
        @Param("memberId") Long memberId,
        @Param("today") LocalDate today
    );

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.member
          join fetch bm.memberType
        where bm.body.id = :bodyId and (bm.validTo is null or bm.validTo > :today)
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findCurrentAndFutureByBodyId(
        @Param("bodyId") Long bodyId,
        @Param("today") LocalDate today
    );

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.member
          join fetch bm.memberType
        where bm.body.id = :bodyId
          and bm.validFrom <= :today and (bm.validTo is null or bm.validTo > :today)
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findActiveByBodyId(@Param("bodyId") Long bodyId, @Param("today") LocalDate today);

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.body b
          join fetch b.bodyTemplate t
          join fetch bm.memberType
        where bm.member.id = :memberId and b.id = :bodyId
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findAllByMemberIdAndBodyId(
        @Param("memberId") Long memberId,
        @Param("bodyId") Long bodyId
    );

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.body b
          join fetch b.bodyTemplate t
          join fetch bm.memberType
        where bm.member.id = :memberId and t.kind in :kinds
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findAllByMemberIdAndKindIn(
        @Param("memberId") Long memberId,
        @Param("kinds") Collection<BodyKind> kinds
    );

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.member
          join fetch bm.body b
          join fetch b.bodyTemplate t
          join fetch bm.memberType
        where t.kind in :kinds
          and bm.validFrom <= :today and (bm.validTo is null or bm.validTo > :today)
        """)
    List<TBodyMembership> findActiveByKindIn(
        @Param("kinds") Collection<BodyKind> kinds,
        @Param("today") LocalDate today
    );

    @Query("""
        select count(distinct bm.member.id) from TBodyMembership bm
        where bm.body.id = :bodyId and bm.member.id <> :excludedMemberId
          and bm.validFrom <= :date and (bm.validTo is null or bm.validTo > :date)
        """)
    long countOtherMembersActiveOn(
        @Param("bodyId") Long bodyId,
        @Param("excludedMemberId") Long excludedMemberId,
        @Param("date") LocalDate date
    );
}
