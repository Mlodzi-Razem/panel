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
     * Dry run of the Art. 20, 21 and 52 rules; changes nothing.
     */
    AssignmentCheck check(AssignMembership request);

    /**
     * Throws {@link AssignmentBlockedException} on violations and {@link ConfirmationRequiredException} when there
     * are conflicts or warnings and {@code confirmed} is false. When confirmed, conflicting earlier memberships
     * are ended ({@code valid_to}) first. Publishes {@link BodyMembershipChanged} for each ended and created
     * membership and audits both.
     */
    AssignmentResult assign(AssignMembership request, boolean confirmed);

    /**
     * Ends the membership on the given date (today by default).
     */
    TBodyMembership end(long membershipId, @Nullable LocalDate endDate);

    List<TBodyMembership> activeIn(long bodyId);

    List<TBodyMembership> activeOf(long memberId);

    List<TBodyMembership> historyOf(long memberId);
}
