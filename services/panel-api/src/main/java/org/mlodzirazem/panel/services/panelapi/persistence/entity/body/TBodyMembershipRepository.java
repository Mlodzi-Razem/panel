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

import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionScope;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * A mandate that was voided (status VOIDED, panel A3) never counts as a function: every query
 * that describes what a member or a body looks like — history, composition, permissions, statutory
 * checks — reads only live rows. The status is always a bound parameter: Hibernate 7 turns a
 * fully-qualified enum literal in the query text into a bogus SQL type name.
 */
@Repository
public interface TBodyMembershipRepository extends JpaRepository<TBodyMembership, Long> {
    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.body b
          join fetch b.bodyTemplate
          join fetch bm.memberType mt
          join fetch mt.permissionGroup
        where bm.member.id = :memberId and bm.status <> :notStatus
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findByMemberIdAndStatusNot(
        @Param("memberId") Long memberId,
        @Param("notStatus") BodyMembershipStatus notStatus
    );

    default List<TBodyMembership> findAllByMemberId(Long memberId) {
        return findByMemberIdAndStatusNot(memberId, BodyMembershipStatus.VOIDED);
    }

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.body b
          join fetch b.bodyTemplate
          join fetch bm.memberType mt
          join fetch mt.permissionGroup
        where bm.member.id = :memberId
          and bm.status = :status
          and bm.validFrom <= :today and (bm.validTo is null or bm.validTo > :today)
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findActiveByMemberIdAndStatus(
        @Param("memberId") Long memberId,
        @Param("today") LocalDate today,
        @Param("status") BodyMembershipStatus status
    );

    default List<TBodyMembership> findActiveByMemberId(Long memberId, LocalDate today) {
        return findActiveByMemberIdAndStatus(memberId, today, BodyMembershipStatus.ACTIVE);
    }

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.member
          join fetch bm.memberType
        where bm.body.id = :bodyId
          and bm.status = :status
          and (bm.validTo is null or bm.validTo > :today)
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findCurrentAndFutureByBodyIdAndStatus(
        @Param("bodyId") Long bodyId,
        @Param("today") LocalDate today,
        @Param("status") BodyMembershipStatus status
    );

    default List<TBodyMembership> findCurrentAndFutureByBodyId(Long bodyId, LocalDate today) {
        return findCurrentAndFutureByBodyIdAndStatus(bodyId, today, BodyMembershipStatus.ACTIVE);
    }

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.member
          join fetch bm.memberType
        where bm.body.id = :bodyId
          and bm.status = :status
          and bm.validFrom <= :today and (bm.validTo is null or bm.validTo > :today)
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findActiveByBodyIdAndStatus(
        @Param("bodyId") Long bodyId,
        @Param("today") LocalDate today,
        @Param("status") BodyMembershipStatus status
    );

    default List<TBodyMembership> findActiveByBodyId(Long bodyId, LocalDate today) {
        return findActiveByBodyIdAndStatus(bodyId, today, BodyMembershipStatus.ACTIVE);
    }

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.body b
          join fetch b.bodyTemplate t
          join fetch bm.memberType
        where bm.member.id = :memberId and b.id = :bodyId
          and bm.status <> :notStatus
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findByMemberIdAndBodyIdAndStatusNot(
        @Param("memberId") Long memberId,
        @Param("bodyId") Long bodyId,
        @Param("notStatus") BodyMembershipStatus notStatus
    );

    default List<TBodyMembership> findAllByMemberIdAndBodyId(Long memberId, Long bodyId) {
        return findByMemberIdAndBodyIdAndStatusNot(memberId, bodyId, BodyMembershipStatus.VOIDED);
    }

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.body b
          join fetch b.bodyTemplate t
          join fetch bm.memberType
        where bm.member.id = :memberId and t.kind in :kinds
          and bm.status <> :notStatus
        order by bm.validFrom, bm.id
        """)
    List<TBodyMembership> findByMemberIdAndKindInAndStatusNot(
        @Param("memberId") Long memberId,
        @Param("kinds") Collection<BodyKind> kinds,
        @Param("notStatus") BodyMembershipStatus notStatus
    );

    default List<TBodyMembership> findAllByMemberIdAndKindIn(Long memberId, Collection<BodyKind> kinds) {
        return findByMemberIdAndKindInAndStatusNot(memberId, kinds, BodyMembershipStatus.VOIDED);
    }

    @Query("""
        select bm from TBodyMembership bm
          join fetch bm.member
          join fetch bm.body b
          join fetch b.bodyTemplate t
          join fetch bm.memberType
        where t.kind in :kinds
          and bm.status = :status
          and bm.validFrom <= :today and (bm.validTo is null or bm.validTo > :today)
        """)
    List<TBodyMembership> findActiveByKindInAndStatus(
        @Param("kinds") Collection<BodyKind> kinds,
        @Param("today") LocalDate today,
        @Param("status") BodyMembershipStatus status
    );

    default List<TBodyMembership> findActiveByKindIn(Collection<BodyKind> kinds, LocalDate today) {
        return findActiveByKindInAndStatus(kinds, today, BodyMembershipStatus.ACTIVE);
    }

    @Query("""
        select count(distinct bm.member.id) from TBodyMembership bm
        where bm.body.id = :bodyId and bm.member.id <> :excludedMemberId
          and bm.status = :status
          and bm.validFrom <= :date and (bm.validTo is null or bm.validTo > :date)
        """)
    long countOtherMembersActiveOnAndStatus(
        @Param("bodyId") Long bodyId,
        @Param("excludedMemberId") Long excludedMemberId,
        @Param("date") LocalDate date,
        @Param("status") BodyMembershipStatus status
    );

    default long countOtherMembersActiveOn(Long bodyId, Long excludedMemberId, LocalDate date) {
        return countOtherMembersActiveOnAndStatus(bodyId, excludedMemberId, date, BodyMembershipStatus.ACTIVE);
    }

    @Query("""
        select count(bm) from TBodyMembership bm
          join bm.member m
          join bm.memberType mt
          join mt.permissionGroup g
        where g.scope = :scope and :permission member of g.permissions
          and m.status <> :excludedStatus and m.id <> :excludedMemberId
          and bm.id not in :excludedMembershipIds
          and bm.status = :membershipStatus
          and bm.validFrom <= :today and bm.validTo is null
        """)
    long countOpenEndedHoldersOfStatus(
        @Param("scope") PermissionScope scope,
        @Param("permission") PermissionType permission,
        @Param("excludedStatus") MemberStatus excludedStatus,
        @Param("excludedMemberId") Long excludedMemberId,
        @Param("excludedMembershipIds") Collection<Long> excludedMembershipIds,
        @Param("today") LocalDate today,
        @Param("membershipStatus") BodyMembershipStatus membershipStatus
    );

    default long countOpenEndedHolders(
        PermissionScope scope,
        PermissionType permission,
        MemberStatus excludedStatus,
        Long excludedMemberId,
        Collection<Long> excludedMembershipIds,
        LocalDate today
    ) {
        return countOpenEndedHoldersOfStatus(scope, permission, excludedStatus, excludedMemberId,
            excludedMembershipIds, today, BodyMembershipStatus.ACTIVE);
    }

    @Query("""
        select count(bm) from TBodyMembership bm
        where bm.body.id = :bodyId and bm.status <> :status
        """)
    long countByBodyIdAndStatusNot(@Param("bodyId") Long bodyId, @Param("status") BodyMembershipStatus status);

    @Query("""
        select count(bm) from TBodyMembership bm
        where bm.body.id = :bodyId
        """)
    long countByBodyId(@Param("bodyId") Long bodyId);

    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) l", nativeQuery = true)
    int lockTransactionally(@Param("key") long key);
}
