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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** Writes the append-only history of a submission and the audit row of the same change: names and codes only. */
@Component
@RequiredArgsConstructor
class IntakeJournal {
    private final JdbcTemplate jdbc;
    private final AuditLog auditLog;

    /**
     * @param fields names of the touched fields, never their values
     * @param outcome a code from a closed vocabulary
     */
    void record(String event, long intakeId, String operation, List<String> fields, String outcome,
                @Nullable Long actorMemberId) {
        String payload = fields.stream().map(name -> "\"" + name + "\"")
            .collect(Collectors.joining(", ", "{\"fields\": [", "]}"));

        jdbc.update("INSERT INTO recruitment_intake_events (intake_id, event_type, payload, created_by_user_id) "
            + "VALUES (?, ?, ?::jsonb, ?)", intakeId, event, payload, actorMemberId);

        auditLog.record("recruitment :: " + operation,
            AuditPayload.of("intakeId", intakeId).withFields(fields).withOutcome(outcome));
    }
}
