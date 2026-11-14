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

package org.mlodzirazem.panel.services.panelapi.api.v1.changerequests;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Names;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.PersonRef;
import org.mlodzirazem.panel.services.panelapi.members.ChangeRequestDetails;
import org.mlodzirazem.panel.services.panelapi.members.ChangeRequestService;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberChangeRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class ChangeRequestViews {
    private final ChangeRequestService changeRequests;

    ChangeRequestResponse response(TMemberChangeRequest request, boolean withProposed) {
        ChangeRequestDetails details = changeRequests.details(request.getId());

        Map<String, @Nullable Object> proposed = null;

        if (withProposed) {
            proposed = new LinkedHashMap<>();

            for (var entry : details.proposed().entrySet()) {
                proposed.put(entry.getKey().propertyName(), masked(entry.getKey(), entry.getValue()));
            }
        }

        return new ChangeRequestResponse(
            request.getId(),
            request.getStatus(),
            person(request.getMember()),
            person(request.getRequestedBy()),
            request.getReason(),
            details.proposed().keySet().stream().map(MemberField::propertyName).sorted().toList(),
            proposed,
            request.getDecidedBy() == null ? null : person(request.getDecidedBy()),
            request.getCreatedAt(),
            request.getDecidedAt(),
            request.getDecisionNote()
        );
    }

    ChangeRequestDetailResponse detail(ChangeRequestDetails details, boolean revealIdentifier) {
        TMember member = details.request().getMember();
        List<ChangeRequestDetailResponse.Change> changes = details.proposed()
            .keySet()
            .stream()
            .sorted()
            .map(field -> {
                Object current = field.get(member);
                Object snapshot = details.snapshot().get(field);
                boolean stale = !Objects.equals(normalise(current), normalise(snapshot));

                return new ChangeRequestDetailResponse.Change(
                    field.propertyName(),
                    field.group(),
                    show(field, details.proposed().get(field), revealIdentifier),
                    show(field, snapshot, revealIdentifier),
                    show(field, current, revealIdentifier),
                    stale
                );
            })
            .toList();

        return new ChangeRequestDetailResponse(response(details.request(), false), changes);
    }

    private static @Nullable Object show(MemberField field, @Nullable Object value, boolean reveal) {
        return reveal ? value : masked(field, value);
    }

    private static @Nullable Object masked(MemberField field, @Nullable Object value) {
        if (field == MemberField.FORMAL_IDENTIFIER && value instanceof String identifier) {
            return Names.mask(identifier);
        }

        return value;
    }

    private static @Nullable Object normalise(@Nullable Object value) {
        return value instanceof List<?> list && list.isEmpty() ? null : value;
    }

    private static PersonRef person(TMember member) {
        return new PersonRef(member.getId(), Names.display(member));
    }
}
