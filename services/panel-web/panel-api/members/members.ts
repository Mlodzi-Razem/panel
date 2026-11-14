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
import {
    Affiliation,
    affiliationSchema,
    ApiResult,
    District,
    districtSchema,
    MemberDetail,
    memberDetailSchema,
    MemberPage,
    memberPageSchema,
    MemberSummary,
    memberSummarySchema,
} from "@/panel-api/members/types";

export type MemberSearchParams = {
    page?: number;
    size?: number;
    q?: string;
    status?: string;
    membershipType?: string;
    district?: number;
};

export function listMembers(params: MemberSearchParams): Promise<ApiResult<MemberPage>> {
    return apiCall({
        method: 'GET',
        path: '/members',
        query: {
            page: params.page ?? 0,
            size: params.size ?? 25,
            q: params.q,
            status: params.status,
            membershipType: params.membershipType,
            district: params.district,
        },
    }, memberPageSchema);
}

export function getMember(id: number, revealIdentifier = false): Promise<ApiResult<MemberDetail>> {
    return apiCall({
        method: 'GET',
        path: `/members/${id}`,
        query: revealIdentifier ? {reveal: 'formalIdentifier'} : undefined,
    }, memberDetailSchema);
}

export function createMember(
    primaryDistrictId: number,
    fields: Record<string, unknown>,
): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'POST', path: '/members', body: {primaryDistrictId, fields}}, memberDetailSchema);
}

export function updateMember(id: number, fields: Record<string, unknown>): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'PATCH', path: `/members/${id}`, body: {fields}}, memberDetailSchema);
}

export function membersEndingThisYear(): Promise<ApiResult<MemberSummary[]>> {
    return apiCall({method: 'GET', path: '/members/ending-this-year'}, z.array(memberSummarySchema));
}

export type AffiliationChange =
    | { action: 'ADD_SUPPORTING'; districtId: number; votesHere: boolean }
    | { action: 'SET_PRIMARY'; districtId: number; keepPreviousAsSupporting: boolean }
    | { action: 'SET_VOTING'; districtId: number };

export function changeAffiliation(memberId: number, change: AffiliationChange): Promise<ApiResult<Affiliation[]>> {
    return apiCall({
        method: 'POST',
        path: `/members/${memberId}/affiliations`,
        body: change,
    }, z.array(affiliationSchema));
}

export function endAffiliation(memberId: number, districtId: number): Promise<ApiResult<Affiliation[]>> {
    return apiCall({
        method: 'DELETE',
        path: `/members/${memberId}/affiliations/${districtId}`,
    }, z.array(affiliationSchema));
}

export function listDistricts(): Promise<ApiResult<District[]>> {
    return apiCall({method: 'GET', path: '/districts'}, z.array(districtSchema));
}
