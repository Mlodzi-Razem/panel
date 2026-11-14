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
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.ChangeRequestDetails;
import org.mlodzirazem.panel.services.panelapi.members.ChangeRequestService;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.ChangeRequestStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/change-requests")
@RequiredArgsConstructor
@Transactional
public class ChangeRequestsController {
    private final CallerResolver callers;
    private final ChangeRequestService changeRequests;
    private final AffiliationService affiliations;
    private final ChangeRequestViews views;
    private final AuditLog auditLog;

    public record DecisionRequest(@Nullable String note) {
    }

    @GetMapping
    public List<ChangeRequestResponse> pending(@RequestParam(name = "status", defaultValue = "PENDING") ChangeRequestStatus status) {
        Caller caller = callers.require();

        if (status != ChangeRequestStatus.PENDING) {
            throw ApiException.badRequest("INVALID_VALUE", "Only PENDING requests can be listed", List.of("status"));
        }

        return changeRequests.pendingFor(caller.id()).stream().map(r -> views.response(r, false)).toList();
    }

    @GetMapping("/{id}")
    public ChangeRequestDetailResponse get(@PathVariable("id") long id) {
        Caller caller = callers.require();
        ChangeRequestDetails details = changeRequests.details(id);
        long memberId = details.request().getMember().getId();

        boolean decider = caller.permissions().canForMember(PermissionType.FORMAL_PERSONAL_DATA_MANAGE,
            affiliations.districtsOf(memberId));
        boolean participant = caller.id() == memberId || caller.id() == details.request().getRequestedBy().getId();

        if (!decider && !participant) {
            throw new ForbiddenException("No permission to see this change request");
        }

        boolean reveal = decider && details.proposed().containsKey(MemberField.FORMAL_IDENTIFIER);

        if (reveal) {
            auditLog.record("members :: reveal-formal-identifier", AuditPayload
                .of("memberId", memberId)
                .withId("changeRequestId", id)
                .withFields(List.of(MemberField.FORMAL_IDENTIFIER.propertyName())));
        }

        return views.detail(details, reveal);
    }

    @PostMapping("/{id}/approve")
    public ChangeRequestResponse approve(@PathVariable("id") long id, @RequestBody(required = false) @Nullable DecisionRequest body) {
        Caller caller = callers.require();

        return views.response(changeRequests.approve(id, caller.id(), note(body)), false);
    }

    @PostMapping("/{id}/reject")
    public ChangeRequestResponse reject(@PathVariable("id") long id, @RequestBody(required = false) @Nullable DecisionRequest body) {
        Caller caller = callers.require();

        return views.response(changeRequests.reject(id, caller.id(), note(body)), false);
    }

    private static @Nullable String note(@Nullable DecisionRequest body) {
        return body == null ? null : body.note();
    }
}
