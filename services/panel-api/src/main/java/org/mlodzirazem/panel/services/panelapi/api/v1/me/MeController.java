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

package org.mlodzirazem.panel.services.panelapi.api.v1.me;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.changerequests.ChangeRequestResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.changerequests.OwnChangeRequests;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.MemberFieldParser;
import org.mlodzirazem.panel.services.panelapi.api.v1.members.MemberDetailResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.members.MemberViews;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyService;
import org.mlodzirazem.panel.services.panelapi.members.ChangeRequestService;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberFieldGroup;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.ChangeRequestStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberChangeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@Transactional
public class MeController {
    private static final Set<MemberFieldGroup> SELF_EDITABLE = EnumSet.of(MemberFieldGroup.CONTACT,
        MemberFieldGroup.NAMING);

    private final CallerResolver callers;
    private final MemberService memberService;
    private final ChangeRequestService changeRequests;
    private final BodyService bodies;
    private final MemberViews views;
    private final MemberFieldParser parser;
    private final OwnChangeRequests ownRequests;

    public record PatchMeRequest(@Nullable Map<String, JsonNode> fields) {
    }

    public record CreateChangeRequest(@Nullable Map<String, JsonNode> fields, @Nullable String reason) {
    }

    @GetMapping
    public Object me() {
        Optional<Caller> found = callers.find();

        if (found.isEmpty()) {
            return new MeResponse.Unlinked(false);
        }

        Caller caller = found.get();
        var permissions = caller.permissions();

        Map<Long, TBody> districts = new HashMap<>();
        bodies.districts().forEach(d -> districts.put(d.getId(), d));

        List<MeResponse.DistrictPermissions> perDistrict = permissions.perDistrict()
            .entrySet()
            .stream()
            .filter(e -> districts.containsKey(e.getKey()))
            .map(e -> new MeResponse.DistrictPermissions(e.getKey(), districts.get(e.getKey()).getName(),
                e.getValue().stream().sorted().toList()))
            .sorted(Comparator.comparing(MeResponse.DistrictPermissions::districtName))
            .toList();

        boolean pending = changeRequests.forMember(caller.id())
                                        .stream()
                                        .anyMatch(r -> r.getStatus() == ChangeRequestStatus.PENDING);

        return new MeResponse(
            true,
            views.detail(caller.member(), MemberViews.OWN),
            new MeResponse.Permissions(permissions.global().stream().sorted().toList(), perDistrict),
            caller.canReadMembers(),
            caller.canManageBodies(),
            pending
        );
    }

    @PatchMapping
    public MemberDetailResponse update(@RequestBody PatchMeRequest request) {
        Caller caller = callers.require();
        var changes = parser.parse(request.fields());

        List<String> notEditable = changes.keySet()
                                          .stream()
                                          .filter(f -> !SELF_EDITABLE.contains(f.group()))
                                          .map(MemberField::propertyName)
                                          .sorted()
                                          .toList();

        if (!notEditable.isEmpty()) {
            throw ApiException.badRequest("FIELD_NOT_EDITABLE", "These fields cannot be edited directly",
                notEditable);
        }

        memberService.update(caller.id(), changes);

        TMember reloaded = memberService.get(caller.id());

        return views.detail(reloaded, MemberViews.OWN);
    }

    @PostMapping("/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeRequestResponse createChangeRequest(@RequestBody CreateChangeRequest request) {
        Caller caller = callers.require();
        var proposed = parser.parse(request.fields());

        TMemberChangeRequest created = changeRequests.create(caller.id(), caller.id(), proposed, request.reason());

        return ownRequests.response(created);
    }

    @GetMapping("/change-requests")
    public List<ChangeRequestResponse> changeRequests() {
        Caller caller = callers.require();

        return changeRequests.forMember(caller.id()).stream().map(ownRequests::response).toList();
    }

    @DeleteMapping("/change-requests/{id}")
    public ChangeRequestResponse cancel(@PathVariable("id") long id) {
        Caller caller = callers.require();

        return ownRequests.response(changeRequests.cancel(id, caller.id()));
    }
}
