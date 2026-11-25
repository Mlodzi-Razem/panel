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

package org.mlodzirazem.panel.services.panelapi.bodies;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;

import java.time.LocalDate;
import java.util.List;

public interface BodyMembershipService {
    /**
     * Dry run of the assignment rules; changes nothing. Incompatible overlapping mandates
     * (art. 18 ust. 1 pkt 6) are reported as hard {@code INCOMPATIBLE_MEMBERSHIP} violations.
     */
    AssignmentCheck check(AssignMembership request);

    /**
     * Throws {@link AssignmentBlockedException} on violations, {@link ConfirmationRequiredException} when there
     * are warnings and {@code confirmed} is false, and {@code SELF_ASSIGNMENT_NOT_ALLOWED} when the caller is
     * the subject of the assignment. Nothing is ever auto-closed: incompatible mandates have to be ended
     * explicitly via {@link #end(long, LocalDate, String)}. Publishes {@link BodyMembershipChanged}
     * for the created membership and audits it.
     */
    AssignmentResult assign(AssignMembership request, boolean confirmed);

    /**
     * Ends (closes) the membership on the given date (today by default). A date in the past —
     * a retroactive correction of history — requires a decision reference; a closed membership can
     * only be shortened, never extended or reopened.
     */
    TBodyMembership end(long membershipId, @Nullable LocalDate endDate, @Nullable String decisionReference);

    /**
     * Voids the membership as a mistaken entry ("wpis omyłkowy"): a completely different thing from
     * {@link #end} — the mandate never happened, so it disappears from functions, permissions and
     * statutory checks. The row is never deleted: it stays in {@code body_memberships} with status
     * {@code VOIDED} and the reason, and the audit log keeps the record ({@code bodies ::
     * membership-void}). The reason is required; the resolution reference ({@code decisionReference})
     * is optional and, once given, overwrites nothing silently.
     */
    TBodyMembership voidMembership(long membershipId, String reason, @Nullable String decisionReference);

    List<TBodyMembership> activeIn(long bodyId);

    List<TBodyMembership> activeOf(long memberId);

    List<TBodyMembership> historyOf(long memberId);
}
