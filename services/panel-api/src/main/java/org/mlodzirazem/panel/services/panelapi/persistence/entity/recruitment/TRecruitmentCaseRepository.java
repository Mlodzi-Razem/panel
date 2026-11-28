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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TRecruitmentCaseRepository extends JpaRepository<TRecruitmentCase, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TRecruitmentCase c where c.id = :id")
    Optional<TRecruitmentCase> findByIdForUpdate(@Param("id") Long id);

    /**
     * Open-case duplicate of a candidate identifier (e-declaration block, art. 9 ust. 5 hygiene);
     * the partial unique index of the schema stays the race-safe backstop.
     */
    @Query("select count(c) > 0 from TRecruitmentCase c"
        + " where c.candidateFormalIdentifier = :formalIdentifier and c.closedAt is null"
        + " and c.id <> :caseId")
    boolean existsAnotherOpenCaseByFormalIdentifier(
        @Param("formalIdentifier") String formalIdentifier,
        @Param("caseId") Long caseId
    );

    @Query("select count(c) > 0 from TRecruitmentCase c"
        + " where lower(c.candidateEmail) = lower(:email) and c.closedAt is null")
    boolean existsOpenByEmail(@Param("email") String email);

    @Query("select count(c) > 0 from TRecruitmentCase c"
        + " where lower(c.candidateEmail) = lower(:email) and c.closedAt is null and c.district.id in :districtIds")
    boolean existsOpenByEmailInDistricts(@Param("email") String email,
                                         @Param("districtIds") java.util.Collection<Long> districtIds);

    /** Retention (M8): rejected, not anonymized cases whose artifacts are still stored; keyset by id. */
    @Query("select c.id from TRecruitmentCase c where c.status = 'REJECTED' and c.anonymizedAt is null"
        + " and c.artifactsPurgedAt is null and c.closedAt <= :closedOnOrBefore and c.id > :afterId"
        + " order by c.id")
    List<Long> findIdsDueForArtifactPurge(
        @Param("closedOnOrBefore") LocalDate closedOnOrBefore,
        @Param("afterId") long afterId,
        Pageable page
    );

    /** Retention (M8): rejected cases whose personal data is due to be wiped; keyset by id. */
    @Query("select c.id from TRecruitmentCase c where c.status = 'REJECTED' and c.anonymizedAt is null"
        + " and c.closedAt <= :closedOnOrBefore and c.id > :afterId order by c.id")
    List<Long> findIdsDueForAnonymization(
        @Param("closedOnOrBefore") LocalDate closedOnOrBefore,
        @Param("afterId") long afterId,
        Pageable page
    );
}
