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

package org.mlodzirazem.panel.services.panelapi.api.v1.members;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.MemberFieldParser;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.PageResponse;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.NewMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliation;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
@Transactional
public class MembersController {
    private static final int MAX_PAGE_SIZE = 100;

    private final CallerResolver callers;
    private final MemberService memberService;
    private final TMemberRepository memberRepository;
    private final MemberSearch search;
    private final MemberViews views;
    private final MemberFieldParser parser;
    private final Clock clock;

    public record CreateMemberRequest(@Nullable Long primaryDistrictId, @Nullable Map<String, JsonNode> fields) {
    }

    public record PatchMemberRequest(@Nullable Map<String, JsonNode> fields) {
    }

    @GetMapping
    public PageResponse<MemberSummaryResponse> list(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size,
        @RequestParam(name = "q", required = false) @Nullable String q,
        @RequestParam(name = "status", required = false) @Nullable MemberStatus status,
        @RequestParam(name = "membershipType", required = false) @Nullable MembershipType membershipType,
        @RequestParam(name = "district", required = false) @Nullable Long district,
        @RequestParam(name = "sort", required = false) @Nullable String sort
    ) {
        Caller caller = callers.require();
        var p = caller.permissions();

        MemberSearch.Scope scope = new MemberSearch.Scope(
            p.canGlobally(PermissionType.CONTACT_PERSONAL_DATA_READ)
                || p.canGlobally(PermissionType.FORMAL_PERSONAL_DATA_READ),
            p.canGlobally(PermissionType.FORMAL_PERSONAL_DATA_READ),
            p.districtsWith(PermissionType.CONTACT_PERSONAL_DATA_READ),
            p.districtsWith(PermissionType.FORMAL_PERSONAL_DATA_READ)
        );

        if (scope.empty()) {
            throw new ForbiddenException("No permission to read members");
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        MemberSearch.Result result = search.search(scope, new MemberSearch.Filter(q, status, membershipType, district),
            MemberSearch.Sort.parse(sort), safePage, safeSize);

        return PageResponse.of(summaries(result.ids(), caller, false), safePage, safeSize, result.total());
    }

    @GetMapping("/ending-this-year")
    public List<MemberSummaryResponse> endingThisYear(@RequestParam(name = "year", required = false) @Nullable Integer year) {
        Caller caller = callers.require();

        if (!caller.permissions().can(PermissionType.FORMAL_PERSONAL_DATA_READ)) {
            throw new ForbiddenException("FORMAL_PERSONAL_DATA_READ is required");
        }

        int effectiveYear = year != null ? year : Year.now(clock).getValue();
        List<TMember> ending = memberService.membersEndingIn(effectiveYear);
        Map<Long, List<TMemberDistrictAffiliation>> active = views.activeAffiliations(
            ending.stream().map(TMember::getId).toList());

        List<MemberSummaryResponse> result = new ArrayList<>();

        for (TMember member : ending) {
            List<TMemberDistrictAffiliation> memberAffiliations = active.getOrDefault(member.getId(), List.of());

            if (caller.permissions().canForMember(PermissionType.FORMAL_PERSONAL_DATA_READ,
                MemberViews.districtsOf(memberAffiliations))) {
                result.add(views.summary(member, memberAffiliations, caller, true));
            }
        }

        return result;
    }

    @GetMapping("/{id}")
    public MemberDetailResponse get(
        @PathVariable("id") long id,
        @RequestParam(name = "reveal", required = false) @Nullable String reveal
    ) {
        Caller caller = callers.require();
        boolean revealRequested = parseReveal(reveal);
        TMember member = memberService.get(id);

        MemberViews.Visibility visibility = views.visibility(caller, member, revealRequested);

        if (!visibility.canRead()) {
            throw new ForbiddenException("No permission to read this member");
        }

        return views.detail(member, visibility);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDetailResponse create(@RequestBody CreateMemberRequest request) {
        Caller caller = callers.require();

        if (request.primaryDistrictId() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "primaryDistrictId is required",
                List.of("primaryDistrictId"));
        }

        if (!caller.permissions().canInDistrict(PermissionType.FORMAL_PERSONAL_DATA_MANAGE,
            request.primaryDistrictId())) {
            throw new ForbiddenException("FORMAL_PERSONAL_DATA_MANAGE is required in the target district");
        }

        Map<MemberField, @Nullable Object> values = parser.parse(request.fields());
        TMember created = memberService.create(new NewMember(values, request.primaryDistrictId()));

        return views.detail(created, views.visibility(caller, created, false));
    }

    @PatchMapping("/{id}")
    public MemberDetailResponse update(@PathVariable("id") long id, @RequestBody PatchMemberRequest request) {
        Caller caller = callers.require();
        TMember member = memberService.get(id);
        Map<MemberField, @Nullable Object> changes = parser.parse(request.fields());
        MemberViews.Visibility visibility = views.visibility(caller, member, false);

        List<String> forbidden = changes.keySet()
                                        .stream()
                                        .filter(field -> !canManage(visibility, field))
                                        .map(MemberField::propertyName)
                                        .sorted()
                                        .toList();

        if (!forbidden.isEmpty()) {
            throw ApiException.forbiddenFields(forbidden);
        }

        memberService.update(id, changes);

        return views.detail(memberService.get(id), views.visibility(caller, member, false));
    }

    private static boolean canManage(MemberViews.Visibility visibility, MemberField field) {
        return switch (field.group()) {
            case CONTACT, NAMING -> visibility.manageContact();
            case FORMAL, ORGANISATIONAL -> visibility.manageFormal();
        };
    }

    private static boolean parseReveal(@Nullable String reveal) {
        if (reveal == null || reveal.isBlank()) {
            return false;
        }

        if (!Objects.equals(reveal, MemberField.FORMAL_IDENTIFIER.propertyName())) {
            throw ApiException.badRequest("INVALID_VALUE", "Only formalIdentifier can be revealed", List.of("reveal"));
        }

        return true;
    }

    private List<MemberSummaryResponse> summaries(List<Long> ids, Caller caller, boolean withEnding) {
        Map<Long, TMember> byId = new HashMap<>();
        memberRepository.findAllById(ids).forEach(m -> byId.put(m.getId(), m));
        Map<Long, List<TMemberDistrictAffiliation>> active = views.activeAffiliations(ids);

        return ids.stream()
                  .map(byId::get)
                  .filter(Objects::nonNull)
                  .map(m -> views.summary(m, active.getOrDefault(m.getId(), List.of()), caller, withEnding))
                  .toList();
    }
}
