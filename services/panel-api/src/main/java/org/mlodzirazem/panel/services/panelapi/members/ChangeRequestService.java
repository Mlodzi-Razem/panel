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

package org.mlodzirazem.panel.services.panelapi.members;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberChangeRequest;

import java.util.List;
import java.util.Map;

/**
 * All mutating operations are audited with the ids and the names of the affected fields.
 */
public interface ChangeRequestService {
    /**
     * Only FORMAL fields are allowed; at most one PENDING request per member
     * ({@code ConflictException} code {@code PENDING_EXISTS}); the snapshot holds the current values of the
     * proposed fields. Fields equal to the current value are dropped; nothing left is a
     * {@code RuleViolationException} with code {@code NO_CHANGES}.
     */
    TMemberChangeRequest create(
        long memberId,
        long requestedById,
        Map<MemberField, @Nullable Object> proposed,
        @Nullable String reason
    );

    /**
     * Only the requester ({@code ForbiddenException} otherwise) and only while PENDING.
     */
    TMemberChangeRequest cancel(long requestId, long actorMemberId);

    /**
     * The decider needs FORMAL_PERSONAL_DATA_MANAGE for the member. Applies the values in the same transaction;
     * fails with {@code ConflictException} code {@code STALE_SNAPSHOT} (listing the field names) when a proposed
     * field changed since the snapshot.
     */
    TMemberChangeRequest approve(long requestId, long deciderMemberId, @Nullable String note);

    TMemberChangeRequest reject(long requestId, long deciderMemberId, @Nullable String note);

    ChangeRequestDetails details(long requestId);

    List<TMemberChangeRequest> forMember(long memberId);

    /**
     * PENDING requests of members the actor may decide about (FORMAL_PERSONAL_DATA_MANAGE for the member).
     */
    List<TMemberChangeRequest> pendingFor(long actorMemberId);
}
