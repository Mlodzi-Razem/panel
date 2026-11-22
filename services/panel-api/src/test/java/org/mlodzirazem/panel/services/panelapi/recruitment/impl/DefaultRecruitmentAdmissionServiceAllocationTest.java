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

package org.mlodzirazem.panel.services.panelapi.recruitment.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The allocation loop in isolation: skipping taken numbers, the attempt cap and the reserved band. */
class DefaultRecruitmentAdmissionServiceAllocationTest {
    private final TMemberRepository members = mock(TMemberRepository.class);

    private DefaultRecruitmentAdmissionService service(AtomicLong sequence) {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenAnswer(call -> sequence.getAndIncrement());

        return new DefaultRecruitmentAdmissionService(null, null, members, null, null, null, null, entityManager);
    }

    @Test
    void takenNumbersAreSkippedAndTheResultIsSixDigitsPadded() {
        when(members.findByMemberNumber(anyString())).thenReturn(List.of());
        when(members.findByMemberNumber("7")).thenReturn(List.of(mock(TMember.class)));
        when(members.findByMemberNumber("8")).thenReturn(List.of(mock(TMember.class)));

        assertThat(service(new AtomicLong(7)).allocateMemberNumber()).isEqualTo("000009");
    }

    @Test
    void allocationGivesUpAfterFiftyTakenNumbers() {
        AtomicLong sequence = new AtomicLong(1);
        when(members.findByMemberNumber(anyString())).thenReturn(List.of(mock(TMember.class)));

        assertThatThrownBy(() -> service(sequence).allocateMemberNumber())
            .isInstanceOf(IllegalStateException.class);
        assertThat(sequence.get()).isEqualTo(1 + DefaultRecruitmentAdmissionService.MAX_ALLOCATION_ATTEMPTS);
    }

    @Test
    void theReservedBandIsNeverReturned() {
        when(members.findByMemberNumber(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> service(new AtomicLong(950_000)).allocateMemberNumber())
            .isInstanceOf(IllegalStateException.class);
    }
}
