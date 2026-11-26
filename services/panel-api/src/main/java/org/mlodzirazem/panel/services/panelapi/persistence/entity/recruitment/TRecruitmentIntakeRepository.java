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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TRecruitmentIntakeRepository extends JpaRepository<TRecruitmentIntake, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TRecruitmentIntake i where i.id = :id")
    Optional<TRecruitmentIntake> findForUpdate(@Param("id") long id);

    @Query("select count(i) > 0 from TRecruitmentIntake i where i.status = 'PENDING' and lower(i.email) = lower(:email)")
    boolean existsPendingByEmail(@Param("email") String email);

    Page<TRecruitmentIntake> findByStatus(RecruitmentIntakeStatus status, Pageable page);

    Page<TRecruitmentIntake> findByStatusAndDistrictIdIn(RecruitmentIntakeStatus status, Collection<Long> districtIds,
                                                        Pageable page);

    @Query("select i.id from TRecruitmentIntake i where i.status = 'PENDING' and i.createdAt < :before order by i.id")
    List<Long> pendingCreatedBefore(@Param("before") ZonedDateTime before);

    @Query("select i.id from TRecruitmentIntake i where i.status = 'DISCARDED' and i.dataErasedAt is null "
        + "and i.closedAt < :before order by i.id")
    List<Long> discardedNotErasedClosedBefore(@Param("before") ZonedDateTime before);
}
