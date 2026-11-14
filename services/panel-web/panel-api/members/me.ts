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

import { cache } from "react";
import * as z from "zod";
import { apiCall } from "@/panel-api/members/client";
import {
    ApiError,
    ApiResult,
    ChangeRequest,
    changeRequestSchema,
    LinkedMe,
    MemberDetail,
    memberDetailSchema,
    meSchema,
    PERMISSIONS,
} from "@/panel-api/members/types";

export type MeResult =
    | { kind: 'unauthenticated' }
    | { kind: 'unlinked' }
    | { kind: 'error'; error: ApiError }
    | { kind: 'linked'; me: LinkedMe };

export const getMe = cache(async (): Promise<MeResult> => {
    const result = await apiCall({method: 'GET', path: '/me'}, meSchema);
    if (!result.ok) {
        if (result.error.code === 'UNAUTHENTICATED') {
            return {kind: 'unauthenticated'};
        }
        if (result.error.code === 'NOT_LINKED') {
            return {kind: 'unlinked'};
        }
        return {kind: 'error', error: result.error};
    }
    if (!result.data.linked) {
        return {kind: 'unlinked'};
    }
    return {kind: 'linked', me: result.data};
});

export function updateOwnProfile(fields: Record<string, unknown>): Promise<ApiResult<MemberDetail>> {
    return apiCall({method: 'PATCH', path: '/me', body: {fields}}, memberDetailSchema);
}

export function createOwnChangeRequest(
    fields: Record<string, unknown>,
    reason: string | null,
): Promise<ApiResult<ChangeRequest>> {
    return apiCall({
        method: 'POST',
        path: '/me/change-requests',
        body: {fields, ...(reason ? {reason} : {})},
    }, changeRequestSchema);
}

export function listOwnChangeRequests(): Promise<ApiResult<ChangeRequest[]>> {
    return apiCall({method: 'GET', path: '/me/change-requests'}, z.array(changeRequestSchema));
}

export function cancelOwnChangeRequest(id: number): Promise<ApiResult<ChangeRequest>> {
    return apiCall({method: 'DELETE', path: `/me/change-requests/${id}`}, changeRequestSchema);
}

export function hasPermission(me: LinkedMe, permission: string): boolean {
    return me.permissions.global.includes(permission)
        || me.permissions.perDistrict.some(d => d.permissions.includes(permission));
}

export function canManageFormalAnywhere(me: LinkedMe): boolean {
    return hasPermission(me, PERMISSIONS.formalManage);
}

export function canReadFormalAnywhere(me: LinkedMe): boolean {
    return hasPermission(me, PERMISSIONS.formalRead);
}
