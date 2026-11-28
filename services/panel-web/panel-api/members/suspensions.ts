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
import { ApiResult, mandateRefSchema, suspensionCheckSchema, SuspensionCheck } from "@/panel-api/members/types";

export const positionLossSchema = z.object({
    applicable: z.boolean(),
    dueOn: z.string().nullish(),
    due: z.boolean(),
    outstanding: z.array(mandateRefSchema).default([]),
    conflicting: z.array(mandateRefSchema).default([]),
    endedCount: z.number().default(0),
});

export const suspensionSchema = z.object({
    id: z.number(),
    memberId: z.number(),
    kind: z.string(),
    scope: z.string(),
    scopeDescription: z.string().nullish(),
    suspendingBodyId: z.number(),
    suspendingBodyName: z.string().nullish(),
    start: z.string(),
    end: z.string().nullish(),
    decisionDate: z.string().nullish(),
    state: z.string(),
    decisionSignature: z.string().nullish(),
    lifted: z.object({
        on: z.string(),
        reason: z.string(),
        bodyId: z.number(),
        bodyName: z.string().nullish(),
        decisionSignature: z.string().nullish(),
    }).nullish(),
    voided: z.object({
        at: z.string(),
        reason: z.string(),
        decisionReference: z.string().nullish(),
    }).nullish(),
    appealEnded: z.object({on: z.string(), reference: z.string()}).nullish(),
    positionLoss: positionLossSchema,
    arrearsHint: z.object({months: z.number(), settled: z.boolean()}).nullish(),
    createdAt: z.string(),
});

export const overviewSchema = z.object({
    items: z.array(suspensionSchema),
    effects: z.object({
        votingSuspended: z.boolean(),
        passiveRightSuspended: z.boolean(),
        competencesSuspended: z.boolean(),
    }),
});

export const optionsSchema = z.object({
    appealHintDays: z.number(),
    bodies: z.array(z.object({
        id: z.number(),
        name: z.string(),
        kind: z.string().nullish(),
        allowedFor: z.array(z.string()),
    })),
});

export const rowSchema = z.object({
    id: z.number(),
    memberId: z.number(),
    memberName: z.string().nullish(),
    districtId: z.number().nullish(),
    kind: z.string(),
    scope: z.string(),
    state: z.string(),
    start: z.string(),
    end: z.string().nullish(),
    suspendingBodyName: z.string().nullish(),
    needsAction: z.boolean(),
});

export const rowPageSchema = z.object({
    items: z.array(rowSchema),
    page: z.number(),
    size: z.number(),
    totalItems: z.number(),
    totalPages: z.number(),
});

export const arrearsCandidateSchema = z.object({
    memberId: z.number(),
    memberName: z.string().nullish(),
    districtId: z.number().nullish(),
    months: z.number(),
    since: z.string().nullish(),
    overdueDues: z.number(),
});

export type Suspension = z.infer<typeof suspensionSchema>;
export type SuspensionOverview = z.infer<typeof overviewSchema>;
export type SuspensionOptions = z.infer<typeof optionsSchema>;
export type SuspensionRow = z.infer<typeof rowSchema>;
export type SuspensionRowPage = z.infer<typeof rowPageSchema>;
export type ArrearsCandidate = z.infer<typeof arrearsCandidateSchema>;
export type PositionLossView = z.infer<typeof positionLossSchema>;

export type SuspendInput = {
    kind: string;
    scope: string;
    scopeDescription?: string;
    suspendingBodyId: number;
    start?: string;
    end?: string;
    decisionDate?: string;
    decisionSignature: string;
    confirmedWarnings?: string[];
};

export function getSuspensionOverview(memberId: number): Promise<ApiResult<SuspensionOverview>> {
    return apiCall({method: 'GET', path: `/members/${memberId}/suspensions`}, overviewSchema);
}

export function getSuspensionOptions(memberId: number): Promise<ApiResult<SuspensionOptions>> {
    return apiCall({method: 'GET', path: `/members/${memberId}/suspension-options`}, optionsSchema);
}

export function checkSuspension(memberId: number, input: SuspendInput): Promise<ApiResult<SuspensionCheck>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/suspensions/check`, body: input},
        suspensionCheckSchema);
}

export function createSuspension(memberId: number, input: SuspendInput): Promise<ApiResult<Suspension>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/suspensions`, body: input}, suspensionSchema);
}

export function liftSuspension(memberId: number, suspensionId: number, body: {
    liftedOn?: string;
    reason: string;
    liftingBodyId: number;
    decisionSignature: string;
    confirmedWarnings?: string[];
}): Promise<ApiResult<Suspension>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/suspensions/${suspensionId}/lift`, body},
        suspensionSchema);
}

export function recordAppealEnded(memberId: number, suspensionId: number, body: {
    endedOn: string;
    decisionReference: string;
}): Promise<ApiResult<Suspension>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/suspensions/${suspensionId}/appeal-ended`, body},
        suspensionSchema);
}

export function voidSuspension(memberId: number, suspensionId: number, body: {
    reason: string;
    decisionReference?: string;
}): Promise<ApiResult<Suspension>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/suspensions/${suspensionId}/void`, body},
        suspensionSchema);
}

export function getPositionLoss(memberId: number, suspensionId: number): Promise<ApiResult<PositionLossView>> {
    return apiCall({method: 'GET', path: `/members/${memberId}/suspensions/${suspensionId}/position-loss`},
        positionLossSchema);
}

export function endSuspendedMandates(memberId: number, suspensionId: number, body: {
    membershipIds: number[];
    decisionReference: string;
}): Promise<ApiResult<PositionLossView>> {
    return apiCall({method: 'POST', path: `/members/${memberId}/suspensions/${suspensionId}/end-mandates`, body},
        positionLossSchema);
}

export function listSuspensions(query: {
    state?: string;
    kind?: string;
    districtId?: number;
    needsAction?: boolean;
    page?: number;
    size?: number;
}): Promise<ApiResult<SuspensionRowPage>> {
    return apiCall({
        method: 'GET',
        path: '/suspensions',
        query: {
            state: query.state,
            kind: query.kind,
            districtId: query.districtId,
            needsAction: query.needsAction ? 'true' : undefined,
            page: query.page,
            size: query.size,
        },
    }, rowPageSchema);
}

export function listArrearsCandidates(districtId?: number): Promise<ApiResult<ArrearsCandidate[]>> {
    return apiCall({method: 'GET', path: '/suspensions/arrears-candidates', query: {districtId}},
        z.array(arrearsCandidateSchema));
}
