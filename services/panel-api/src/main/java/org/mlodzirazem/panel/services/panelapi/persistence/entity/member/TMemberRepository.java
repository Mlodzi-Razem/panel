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

import org.mlodzirazem.panel.services.panelapi.core.user.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TMemberRepository extends JpaRepository<TMember, Long> {
    @Query("select i.member from TMemberIdentity i where i.provider = :provider and i.subject = :subject")
    Optional<TMember> findByIdentity(@Param("provider") IdentityProvider provider, @Param("subject") String subject);

    Optional<TMember> findByMemberId(String memberId);

    /**
     * Members whose numeric member number equals {@code number} once leading zeros are ignored on both sides,
     * so the Entra {@code employeeId} {@code 000205} matches the register's {@code 205}.
     *
     * @param number the member number without leading zeros
     */
    @Query(
        value = "select * from members where member_id ~ '^[0-9]+$' and ltrim(member_id, '0') = :number",
        nativeQuery = true
    )
    List<TMember> findByMemberNumber(@Param("number") String number);

    Optional<TMember> findByFormalIdentifier(String formalIdentifier);

    @Query("select m from TMember m where lower(m.domainEmail) = lower(:email)")
    Optional<TMember> findByDomainEmailIgnoreCase(@Param("email") String email);

    @Query("""
        select m from TMember m
        where m.membershipType in :types and m.status in :statuses
          and m.birthDate >= :from and m.birthDate <= :to
        order by m.birthDate, m.id
        """)
    List<TMember> findByBirthDateBetween(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("types") Collection<MembershipType> types,
        @Param("statuses") Collection<MemberStatus> statuses
    );

    List<TMember> findByStatusAndMembershipType(MemberStatus status, MembershipType membershipType);
}
