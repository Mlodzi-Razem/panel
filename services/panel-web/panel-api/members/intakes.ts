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
import { ApiResult } from "@/panel-api/members/types";

/** Back office of the public join form. The list carries no personal data; the detail view does. */
export const INTAKE_STATUSES = ["PENDING", "ACCEPTED", "DISCARDED", "EXPIRED"] as const;
export type IntakeStatus = typeof INTAKE_STATUSES[number];

export const INTAKE_DISCARD_REASONS = ["SPAM", "DUPLICATE", "NOT_ELIGIBLE", "NO_CONTACT", "WITHDRAWN", "OTHER"] as const;
export const intakeDiscardReasonSchema = z.enum(INTAKE_DISCARD_REASONS);
export type IntakeDiscardReason = z.infer<typeof intakeDiscardReasonSchema>;

const summarySchema = z.object({
    id: z.number(),
    receivedAt: z.string(),
    status: z.enum(INTAKE_STATUSES),
    district: z.object({id: z.number(), name: z.string()}).nullish(),
    discardReason: intakeDiscardReasonSchema.nullish(),
    closedAt: z.string().nullish(),
    caseId: z.number().nullish(),
    dataErased: z.boolean(),
});
export type IntakeSummary = z.infer<typeof summarySchema>;

const pageSchema = z.object({
    items: z.array(summarySchema),
    page: z.number(),
    size: z.number(),
    totalItems: z.number(),
    totalPages: z.number(),
});
export type IntakePage = z.infer<typeof pageSchema>;

const detailSchema = z.object({
    summary: summarySchema,
    firstName: z.string().nullish(),
    lastName: z.string().nullish(),
    email: z.string().nullish(),
    phone: z.string().nullish(),
    birthDate: z.string().nullish(),
    locality: z.string().nullish(),
    consentVersion: z.string(),
    consentedAt: z.string(),
    openCaseWithSameEmail: z.boolean(),
    actions: z.object({
        accept: z.boolean(),
        assignDistrict: z.boolean(),
        discard: z.boolean(),
        erase: z.boolean(),
    }),
});
export type IntakeDetail = z.infer<typeof detailSchema>;

export function listIntakes(status: IntakeStatus, page: number, size: number): Promise<ApiResult<IntakePage>> {
    return apiCall({method: "GET", path: "/recruitment-intakes", query: {status, page, size}}, pageSchema);
}

export function getIntake(id: number): Promise<ApiResult<IntakeDetail>> {
    return apiCall({method: "GET", path: `/recruitment-intakes/${id}`}, detailSchema);
}

export function acceptIntake(id: number): Promise<ApiResult<{ caseId: number }>> {
    return apiCall({method: "POST", path: `/recruitment-intakes/${id}/accept`}, z.object({caseId: z.number()}));
}

export function assignIntakeDistrict(id: number, districtBodyId: number): Promise<ApiResult<null>> {
    return apiCall({method: "POST", path: `/recruitment-intakes/${id}/district`, body: {districtBodyId}}, z.null());
}

export function discardIntake(id: number, reason: IntakeDiscardReason): Promise<ApiResult<null>> {
    return apiCall({method: "POST", path: `/recruitment-intakes/${id}/discard`, body: {reason}}, z.null());
}

export function eraseIntake(id: number): Promise<ApiResult<null>> {
    return apiCall({method: "POST", path: `/recruitment-intakes/${id}/erase`}, z.null());
}
