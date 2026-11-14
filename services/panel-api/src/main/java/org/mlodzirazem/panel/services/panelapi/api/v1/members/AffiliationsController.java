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
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/members/{memberId}/affiliations")
@RequiredArgsConstructor
@Transactional
public class AffiliationsController {
    private final CallerResolver callers;
    private final MemberService memberService;
    private final AffiliationService affiliationService;

    public enum Action {
        ADD_SUPPORTING,
        SET_PRIMARY,
        SET_VOTING
    }

    public record AffiliationRequest(
        @Nullable Action action,
        @Nullable Long districtId,
        @Nullable Boolean votesHere,
        @Nullable Boolean keepPreviousAsSupporting
    ) {
    }

    @PostMapping
    public List<MemberDetailResponse.AffiliationView> change(
        @PathVariable("memberId") long memberId,
        @RequestBody AffiliationRequest request
    ) {
        Caller caller = callers.require();

        if (request.action() == null || request.districtId() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "action and districtId are required",
                List.of("action", "districtId"));
        }

        memberService.get(memberId);
        long districtId = request.districtId();
        boolean inTarget = caller.permissions().canInDistrict(PermissionType.FORMAL_PERSONAL_DATA_MANAGE, districtId);

        switch (request.action()) {
            case ADD_SUPPORTING -> {
                requireTarget(inTarget);
                affiliationService.addSupportingDistrict(memberId, districtId, Boolean.TRUE.equals(request.votesHere()));
            }
            case SET_PRIMARY -> {
                requireTarget(inTarget);
                affiliationService.setPrimaryDistrict(memberId, districtId, Boolean.TRUE.equals(request.keepPreviousAsSupporting()));
            }
            case SET_VOTING -> {
                if (!inTarget && !manageMember(caller, memberId)) {
                    throw new ForbiddenException("FORMAL_PERSONAL_DATA_MANAGE is required");
                }

                affiliationService.setVotingDistrict(memberId, districtId);
            }
        }

        return current(memberId);
    }

    @DeleteMapping("/{districtId}")
    public List<MemberDetailResponse.AffiliationView> end(@PathVariable("memberId") long memberId, @PathVariable("districtId") long districtId) {
        Caller caller = callers.require();
        memberService.get(memberId);

        if (!caller.permissions().canInDistrict(PermissionType.FORMAL_PERSONAL_DATA_MANAGE, districtId)
            && !manageMember(caller, memberId)) {
            throw new ForbiddenException("FORMAL_PERSONAL_DATA_MANAGE is required");
        }

        affiliationService.endSupportingDistrict(memberId, districtId);

        return current(memberId);
    }

    private boolean manageMember(Caller caller, long memberId) {
        return caller.permissions().canForMember(PermissionType.FORMAL_PERSONAL_DATA_MANAGE,
            affiliationService.districtsOf(memberId));
    }

    private static void requireTarget(boolean inTarget) {
        if (!inTarget) {
            throw new ForbiddenException("FORMAL_PERSONAL_DATA_MANAGE is required in the target district");
        }
    }

    private List<MemberDetailResponse.AffiliationView> current(long memberId) {
        return affiliationService.active(memberId)
                                 .stream()
                                 .map(a -> new MemberDetailResponse.AffiliationView(
                                     a.getDistrict().getId(), a.getDistrict().getName(), a.getKind(),
                                     a.isVotesHere(), a.getValidFrom()))
                                 .toList();
    }
}
