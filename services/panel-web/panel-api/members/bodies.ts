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
    ApiResult,
    AssignmentCheck,
    assignmentCheckSchema,
    AssignmentResponse,
    assignmentResponseSchema,
    Body,
    BodyDetail,
    bodyDetailSchema,
    bodySchema,
    BodyTemplate,
    Membership,
    membershipSchema,
    templateSchema,
} from "@/panel-api/members/types";

export function listBodies(): Promise<ApiResult<Body[]>> {
    return apiCall({method: 'GET', path: '/bodies'}, z.array(bodySchema));
}

export function getBody(id: number): Promise<ApiResult<BodyDetail>> {
    return apiCall({method: 'GET', path: `/bodies/${id}`}, bodyDetailSchema);
}

export function listBodyTemplates(): Promise<ApiResult<BodyTemplate[]>> {
    return apiCall({method: 'GET', path: '/bodies/templates'}, z.array(templateSchema));
}

export type NewBody = {
    templateId: number;
    name: string;
    email?: string;
    parentBodyId?: number;
    code?: string;
};

export function createBody(body: NewBody): Promise<ApiResult<Body>> {
    return apiCall({method: 'POST', path: '/bodies', body}, bodySchema);
}

export function updateBody(id: number, body: { name?: string; email?: string }): Promise<ApiResult<Body>> {
    return apiCall({method: 'PATCH', path: `/bodies/${id}`, body}, bodySchema);
}

export type AssignmentInput = {
    memberId: number;
    memberTypeId: number;
    validFrom?: string;
    validTo?: string;
    decisionReference?: string;
    confirmedWarnings?: string[];
};

export type SelfRemovalConfirmation = 'SELF_REMOVAL' | 'SELF_REMOVAL_FINAL';

export function checkAssignment(bodyId: number, input: AssignmentInput): Promise<ApiResult<AssignmentCheck>> {
    return apiCall({
        method: 'POST',
        path: `/bodies/${bodyId}/memberships/check`,
        body: input,
    }, assignmentCheckSchema);
}

export function assignMembership(bodyId: number, input: AssignmentInput): Promise<ApiResult<AssignmentResponse>> {
    return apiCall({
        method: 'POST',
        path: `/bodies/${bodyId}/memberships`,
        body: input,
    }, assignmentResponseSchema);
}

export function endMembership(
    bodyId: number,
    membershipId: number,
    endDate?: string,
    decisionReference?: string,
    selfRemovalConfirmations: SelfRemovalConfirmation[] = [],
): Promise<ApiResult<Membership>> {
    return apiCall({
        method: 'DELETE',
        path: `/bodies/${bodyId}/memberships/${membershipId}`,
        query: {endDate, decisionReference, selfRemovalConfirmations: selfRemovalConfirmations.join(',')},
    }, membershipSchema);
}

export function voidMembership(
    bodyId: number,
    membershipId: number,
    reason: string,
    decisionReference?: string,
    selfRemovalConfirmations: SelfRemovalConfirmation[] = [],
): Promise<ApiResult<Membership>> {
    return apiCall({
        method: 'POST',
        path: `/bodies/${bodyId}/memberships/${membershipId}/void`,
        body: {reason, ...(decisionReference ? {decisionReference} : {}), selfRemovalConfirmations},
    }, membershipSchema);
}

export function deleteBody(id: number): Promise<ApiResult<unknown>> {
    return apiCall({method: 'DELETE', path: `/bodies/${id}`}, z.unknown());
}
