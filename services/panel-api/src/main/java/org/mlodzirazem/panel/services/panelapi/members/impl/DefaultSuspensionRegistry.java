/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

package org.mlodzirazem.panel.services.panelapi.members.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.members.SuspensionRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
class DefaultSuspensionRegistry implements SuspensionRegistry {
    /** LEAST ignores NULLs: the earlier of the end and the lift date, NULL while neither is set. */
    private static final String COMPETENCE_SUSPENDED = """
        SELECT suspended_user_id FROM suspensions
        WHERE status = 'ACTIVE'
          AND scope IN ('FULL', 'PASSIVE_ELECTORAL')
          AND suspension_start <= :on
          AND (LEAST(suspension_end, lifted_at) IS NULL OR LEAST(suspension_end, lifted_at) > :on)
        """;

    private final JdbcClient jdbc;

    @Override
    @Transactional(readOnly = true)
    public boolean competencesSuspended(long memberId, LocalDate on) {
        return competenceSuspendedMemberIds(on).contains(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> competenceSuspendedMemberIds(LocalDate on) {
        return new HashSet<>(jdbc.sql(COMPETENCE_SUSPENDED).param("on", on).query(Long.class).list());
    }
}
