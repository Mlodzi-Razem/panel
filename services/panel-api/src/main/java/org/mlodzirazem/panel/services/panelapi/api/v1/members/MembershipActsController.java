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

package org.mlodzirazem.panel.services.panelapi.api.v1.members;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.MemberFieldParser;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.MembershipActService;
import org.mlodzirazem.panel.services.panelapi.members.NewMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipRemovalGround;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberMembershipAct;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/** Membership acts (statute art. 9, 11, 12): the only write path for status, membership type and removal date. */
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
@Transactional
public class MembershipActsController {
    private static final int REFERENCE_MAX_LENGTH = 200;
    private static final int REASON_MAX_LENGTH = 500;

    private final CallerResolver callers;
    private final MemberService memberService;
    private final MembershipActService acts;
    private final MemberFieldParser parser;
    private final MemberViews views;

    public record NewPersonActRequest(
        @Nullable Long primaryDistrictId,
        @Nullable Map<String, JsonNode> fields,
        @Nullable String grantedOn,
        @Nullable String decisionReference
    ) {
    }

    public record HonoraryActRequest(@Nullable String grantedOn, @Nullable String decisionReference) {
    }

    public record RemovalRequest(
        @Nullable String ground,
        @Nullable String removedOn,
        @Nullable String decisionReference
    ) {
    }

    public record ReinstatementRequest(
        @Nullable String effectiveOn,
        @Nullable String decisionReference,
        @Nullable String reason
    ) {
    }

    public record MembershipTypeChangeRequest(
        @Nullable String membershipType,
        @Nullable String effectiveOn,
        @Nullable String decisionReference,
        @Nullable String reason
    ) {
    }

    public record ActResponse(
        long id,
        String kind,
        @Nullable String removalGround,
        @Nullable String membershipTypeAfter,
        LocalDate effectiveOn,
        String decisionReference,
        @Nullable String reason,
        @Nullable ZonedDateTime createdAt
    ) {
    }

    @PostMapping("/{id}/membership-type")
    public MemberDetailResponse changeMembershipType(
        @PathVariable("id") long id,
        @RequestBody MembershipTypeChangeRequest request
    ) {
        Caller caller = callers.require();
        requireManageFormal(caller, id);

        acts.changeMembershipType(new MembershipActService.ChangeMembershipType(
            id,
            membershipType(request.membershipType()),
            date(request.effectiveOn(), "effectiveOn"),
            reference(request.decisionReference()),
            reason(request.reason())));

        return detail(caller, id);
    }

    @PostMapping("/supporting")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDetailResponse grantSupporting(@RequestBody NewPersonActRequest request) {
        Caller caller = callers.require();
        long districtId = primaryDistrict(request);

        if (!caller.permissions().canInDistrict(PermissionType.FORMAL_PERSONAL_DATA_MANAGE, districtId)) {
            throw new ForbiddenException("FORMAL_PERSONAL_DATA_MANAGE is required in the target district");
        }

        Map<MemberField, @Nullable Object> values = parser.parse(request.fields());
        TMember created = acts.grantSupporting(new MembershipActService.GrantSupporting(
            new NewMember(values, districtId),
            date(request.grantedOn(), "grantedOn"),
            reference(request.decisionReference())));

        return views.detail(created, views.visibility(caller, created, false));
    }

    @PostMapping("/honorary")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDetailResponse grantHonorary(@RequestBody NewPersonActRequest request) {
        Caller caller = callers.require();
        long districtId = primaryDistrict(request);
        requireGlobal(caller);

        Map<MemberField, @Nullable Object> values = parser.parse(request.fields());
        TMember created = acts.grantHonorary(new MembershipActService.GrantHonorary(
            new NewMember(values, districtId),
            date(request.grantedOn(), "grantedOn"),
            reference(request.decisionReference())));

        return views.detail(created, views.visibility(caller, created, false));
    }

    @PostMapping("/{id}/honorary")
    public MemberDetailResponse grantHonoraryToMember(
        @PathVariable("id") long id,
        @RequestBody HonoraryActRequest request
    ) {
        Caller caller = callers.require();
        requireGlobal(caller);

        acts.grantHonoraryToMember(new MembershipActService.GrantHonoraryToMember(
            id,
            date(request.grantedOn(), "grantedOn"),
            reference(request.decisionReference())));

        return detail(caller, id);
    }

    @PostMapping("/{id}/removal")
    public MemberDetailResponse remove(@PathVariable("id") long id, @RequestBody RemovalRequest request) {
        Caller caller = callers.require();
        requireManageFormal(caller, id);

        acts.remove(new MembershipActService.Remove(
            id,
            ground(request.ground()),
            date(request.removedOn(), "removedOn"),
            reference(request.decisionReference())));

        return detail(caller, id);
    }

    @PostMapping("/{id}/reinstatement")
    public MemberDetailResponse reinstate(
        @PathVariable("id") long id,
        @RequestBody ReinstatementRequest request
    ) {
        Caller caller = callers.require();
        requireManageFormal(caller, id);

        acts.reinstate(new MembershipActService.Reinstate(
            id,
            date(request.effectiveOn(), "effectiveOn"),
            reference(request.decisionReference()),
            reason(request.reason())));

        return detail(caller, id);
    }

    @GetMapping("/{id}/membership-acts")
    public List<ActResponse> history(@PathVariable("id") long id) {
        Caller caller = callers.require();
        requireManageFormal(caller, id);

        return acts.history(id).stream().map(MembershipActsController::response).toList();
    }

    private static long primaryDistrict(NewPersonActRequest request) {
        if (request.primaryDistrictId() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "primaryDistrictId is required",
                List.of("primaryDistrictId"));
        }

        return request.primaryDistrictId();
    }

    private static void requireGlobal(Caller caller) {
        if (!caller.permissions().canGlobally(PermissionType.FORMAL_PERSONAL_DATA_MANAGE)) {
            throw new ForbiddenException("FORMAL_PERSONAL_DATA_MANAGE is required globally");
        }
    }

    private void requireManageFormal(Caller caller, long memberId) {
        TMember member = memberService.get(memberId);

        if (!views.visibility(caller, member, false).manageFormal()) {
            throw new ForbiddenException("FORMAL_PERSONAL_DATA_MANAGE is required for this member");
        }
    }

    private MemberDetailResponse detail(Caller caller, long memberId) {
        TMember member = memberService.get(memberId);

        return views.detail(member, views.visibility(caller, member, false));
    }

    private static ActResponse response(TMemberMembershipAct act) {
        return new ActResponse(
            act.getId(),
            act.getActKind().name(),
            act.getRemovalGround() == null ? null : act.getRemovalGround().name(),
            act.getMembershipTypeAfter() == null ? null : act.getMembershipTypeAfter().name(),
            act.getEffectiveOn(),
            act.getDecisionReference(),
            act.getReason(),
            act.getCreatedAt()
        );
    }

    private static String reference(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("FIELD_REQUIRED", "decisionReference is required",
                List.of("decisionReference"));
        }

        if (raw.strip().length() > REFERENCE_MAX_LENGTH) {
            throw ApiException.badRequest("INVALID_VALUE", "decisionReference is too long",
                List.of("decisionReference"));
        }

        return raw.strip();
    }

    private static String reason(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("FIELD_REQUIRED", "reason is required", List.of("reason"));
        }

        if (raw.strip().length() > REASON_MAX_LENGTH) {
            throw ApiException.badRequest("INVALID_VALUE", "reason is too long", List.of("reason"));
        }

        return raw.strip();
    }

    private static LocalDate date(@Nullable String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("FIELD_REQUIRED", field + " is required", List.of(field));
        }

        try {
            return LocalDate.parse(raw.strip());
        } catch (DateTimeException invalid) {
            throw ApiException.badRequest("INVALID_VALUE", field + " must be a date", List.of(field));
        }
    }

    private static MembershipType membershipType(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("FIELD_REQUIRED", "membershipType is required", List.of("membershipType"));
        }

        try {
            return MembershipType.valueOf(raw.strip());
        } catch (IllegalArgumentException unknown) {
            throw ApiException.badRequest("INVALID_VALUE", "membershipType is not a known type",
                List.of("membershipType"));
        }
    }

    private static MembershipRemovalGround ground(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("FIELD_REQUIRED", "ground is required", List.of("ground"));
        }

        try {
            return MembershipRemovalGround.valueOf(raw.strip());
        } catch (IllegalArgumentException unknown) {
            throw ApiException.badRequest("INVALID_VALUE", "ground is not a known removal ground", List.of("ground"));
        }
    }
}
