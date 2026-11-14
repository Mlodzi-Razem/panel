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
    ChangeRequest,
    ChangeRequestDetail,
    changeRequestDetailSchema,
    changeRequestSchema,
} from "@/panel-api/members/types";

export function listPendingChangeRequests(): Promise<ApiResult<ChangeRequest[]>> {
    return apiCall({
        method: 'GET',
        path: '/change-requests',
        query: {status: 'PENDING'},
    }, z.array(changeRequestSchema));
}

export function getChangeRequest(id: number): Promise<ApiResult<ChangeRequestDetail>> {
    return apiCall({method: 'GET', path: `/change-requests/${id}`}, changeRequestDetailSchema);
}

export function decideChangeRequest(
    id: number,
    decision: 'approve' | 'reject',
    note: string | null,
): Promise<ApiResult<ChangeRequest>> {
    return apiCall({
        method: 'POST',
        path: `/change-requests/${id}/${decision}`,
        body: note ? {note} : {},
    }, changeRequestSchema);
}
