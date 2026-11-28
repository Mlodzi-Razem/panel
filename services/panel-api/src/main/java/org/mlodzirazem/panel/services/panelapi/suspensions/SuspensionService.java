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

package org.mlodzirazem.panel.services.panelapi.suspensions;

import org.mlodzirazem.panel.services.panelapi.fees.DistrictScope;

import java.time.LocalDate;
import java.util.List;

/**
 * Register of suspensions. Nothing is ever deleted, and nothing closes a mandate by itself: the positions are lost
 * as a proposal, and they end only through {@link #endMandates}, with a resolution.
 */
public interface SuspensionService {
    /** Dry run of the rules; writes nothing. */
    SuspensionCheck check(SuspendMember request);

    SuspensionResult suspend(SuspendMember request);

    Suspension lift(long suspensionId, LiftSuspension request);

    Suspension recordAppealEnded(long suspensionId, LocalDate endedOn, String decisionReference);

    Suspension voidSuspension(long suspensionId, String reason, String decisionReference);

    PositionLoss positionLoss(long suspensionId);

    PositionLoss endMandates(long suspensionId, EndSuspendedMandates request);

    /** Every entry of the member, scheduled and voided ones included, latest start first. */
    List<Suspension> historyOf(long memberId);

    Suspension get(long suspensionId);

    SuspensionOptions optionsFor(long memberId);

    SuspensionRowsPage list(SuspensionFilter filter, DistrictScope scope, int page, int size);

    List<ArrearsCandidate> arrearsCandidates(DistrictScope scope);
}
