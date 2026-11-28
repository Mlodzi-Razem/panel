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

import * as z from "zod";
import { apiCall } from "@/panel-api/members/client";
import { ApiResult, MemberDetail, memberDetailSchema } from "@/panel-api/members/types";

export const membershipActSchema = z.object({
    id: z.number(),
    kind: z.enum(['SUPPORTING_GRANT', 'HONORARY_GRANT', 'REMOVAL', 'REINSTATEMENT', 'MEMBERSHIP_TYPE_CHANGE']),
    removalGround: z.string().nullish(),
    membershipTypeAfter: z.string().nullish(),
    effectiveOn: z.string(),
    decisionReference: z.string(),
    reason: z.string().nullish(),
    createdAt: z.string().nullish(),
});

export type MembershipAct = z.infer<typeof membershipActSchema>;

export type NewPersonActBody = {
    primaryDistrictId: number;
    fields: Record<string, unknown>;
    grantedOn: string;
    decisionReference: string;
};

export function grantSupportingMember(body: NewPersonActBody): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'POST', path: '/members/supporting', body}, memberDetailSchema);
}

export function grantHonoraryMember(body: NewPersonActBody): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'POST', path: '/members/honorary', body}, memberDetailSchema);
}

export function grantHonoraryToMember(
    memberId: number,
    body: { grantedOn: string; decisionReference: string },
): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/honorary`, body}, memberDetailSchema);
}

export function removeMember(
    memberId: number,
    body: { ground: string; removedOn: string; decisionReference: string },
): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/removal`, body}, memberDetailSchema);
}

export function reinstateMember(
    memberId: number,
    body: { effectiveOn: string; decisionReference: string; reason: string },
): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/reinstatement`, body}, memberDetailSchema);
}

export function changeMembershipType(
    memberId: number,
    body: { membershipType: string; effectiveOn: string; decisionReference: string; reason: string },
): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/membership-type`, body}, memberDetailSchema);
}

export function listMembershipActs(memberId: number): Promise<ApiResult<MembershipAct[]>> {
    return apiCall({method: 'GET', path: `/members/${memberId}/membership-acts`}, z.array(membershipActSchema));
}
