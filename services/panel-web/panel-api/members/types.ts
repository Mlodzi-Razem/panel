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

export const memberFieldsSchema = z.record(z.string(), z.unknown());

export const accessSchema = z.object({
    readContact: z.boolean(),
    readFormal: z.boolean(),
    manageContact: z.boolean(),
    manageFormal: z.boolean(),
    formalIdentifierRevealed: z.boolean(),
});

export const affiliationSchema = z.object({
    districtId: z.number(),
    districtName: z.string(),
    kind: z.enum(['PRIMARY', 'SUPPORTING']),
    votesHere: z.boolean(),
    validFrom: z.string(),
});

export const bodyFunctionSchema = z.object({
    membershipId: z.number(),
    bodyId: z.number(),
    bodyName: z.string(),
    bodyKind: z.string().nullish(),
    memberTypeId: z.number(),
    memberTypeCode: z.string().nullish(),
    memberTypeName: z.string(),
    validFrom: z.string(),
    validTo: z.string().nullish(),
});

export const electoralRightsSchema = z.object({
    activeOrdinaryMember: z.boolean(),
    suspended: z.boolean(),
    feeArrears: z.boolean(),
    eligible: z.boolean(),
    canVoteInNationalElection: z.boolean(),
    primaryDistrictId: z.number().nullish(),
    votingDistrictId: z.number().nullish(),
});

export const memberDetailSchema = z.object({
    id: z.number(),
    fields: memberFieldsSchema,
    access: accessSchema,
    affiliations: z.array(affiliationSchema).nullish(),
    bodyFunctions: z.array(bodyFunctionSchema).default([]),
    membershipEndsOn: z.string().nullish(),
    electoralRights: electoralRightsSchema.nullish(),
});

const districtRefSchema = z.object({id: z.number(), name: z.string()});

export const memberSummarySchema = z.object({
    id: z.number(),
    fields: memberFieldsSchema,
    primaryDistrict: districtRefSchema.nullish(),
    supportingDistricts: z.array(districtRefSchema).default([]),
    membershipEndsOn: z.string().nullish(),
});

export const memberPageSchema = z.object({
    items: z.array(memberSummarySchema),
    page: z.number(),
    size: z.number(),
    totalItems: z.number(),
    totalPages: z.number(),
});

export const personRefSchema = z.object({id: z.number(), displayName: z.string()});

export const changeRequestStatusSchema = z.enum(['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']);

export const changeRequestSchema = z.object({
    id: z.number(),
    status: changeRequestStatusSchema,
    member: personRefSchema,
    requestedBy: personRefSchema,
    reason: z.string().nullish(),
    fields: z.array(z.string()),
    proposed: memberFieldsSchema.nullish(),
    decidedBy: personRefSchema.nullish(),
    createdAt: z.string().nullish(),
    decidedAt: z.string().nullish(),
    decisionNote: z.string().nullish(),
});

export const changeRequestDetailSchema = z.object({
    request: changeRequestSchema,
    changes: z.array(z.object({
        field: z.string(),
        group: z.string(),
        proposed: z.unknown().nullish(),
        snapshot: z.unknown().nullish(),
        current: z.unknown().nullish(),
        stale: z.boolean(),
    })),
});

export const districtSchema = z.object({
    id: z.number(),
    name: z.string(),
    code: z.string().nullish(),
    canAct: z.boolean(),
    permissions: z.array(z.string()),
});

export const bodySchema = z.object({
    id: z.number(),
    name: z.string(),
    code: z.string().nullish(),
    email: z.string().nullish(),
    kind: z.string().nullish(),
    scope: z.string().nullish(),
    templateId: z.number().nullish(),
    templateName: z.string().nullish(),
    parentBodyId: z.number().nullish(),
    // True when the migrations seeded the body: the panel never deletes it (A3).
    seeded: z.boolean().default(false),
});

export const memberTypeSchema = z.object({
    id: z.number(),
    name: z.string(),
    code: z.string().nullish(),
    isDefault: z.boolean(),
});

export const compositionEntrySchema = z.object({
    membershipId: z.number(),
    memberId: z.number(),
    memberName: z.string(),
    memberTypeId: z.number(),
    memberTypeCode: z.string().nullish(),
    memberTypeName: z.string(),
    validFrom: z.string(),
    validTo: z.string().nullish(),
});

export const bodyDetailSchema = z.object({
    body: bodySchema,
    composition: z.array(compositionEntrySchema),
    memberTypes: z.array(memberTypeSchema),
    advisories: z.array(z.object({
        code: z.string(),
        message: z.string(),
        current: z.number(),
        expected: z.string().nullish(),
    })).default([]),
});

export const templateSchema = z.object({
    id: z.number(),
    name: z.string(),
    kind: z.string(),
    scope: z.string(),
    parentTemplateId: z.number().nullish(),
    maxConsecutiveTerms: z.number().nullish(),
    termLengthMonths: z.number().nullish(),
    maxTermMonths: z.number().nullish(),
    maxExtensions: z.number().nullish(),
    creatable: z.boolean().default(true),
    unavailableParentIds: z.array(z.number()).default([]),
    memberTypes: z.array(memberTypeSchema).default([]),
});

export const membershipSchema = z.object({
    id: z.number(),
    bodyId: z.number(),
    memberId: z.number(),
    memberName: z.string(),
    memberTypeId: z.number(),
    memberTypeName: z.string(),
    validFrom: z.string(),
    validTo: z.string().nullish(),
});

export const mandateRefSchema = z.object({
    membershipId: z.number(),
    bodyId: z.number(),
    bodyName: z.string(),
    bodyKind: z.string().nullish(),
    memberTypeCode: z.string().nullish(),
    memberTypeName: z.string(),
    validFrom: z.string(),
    validTo: z.string().nullish(),
});

export const assignmentCheckSchema = z.object({
    conflicts: z.array(z.object({
        membershipId: z.number(),
        bodyId: z.number(),
        bodyName: z.string(),
        bodyKind: z.string().nullish(),
        memberTypeId: z.number(),
        memberTypeName: z.string(),
        validFrom: z.string(),
        validTo: z.string().nullish(),
        endsOn: z.string(),
    })).default([]),
    warnings: z.array(z.object({
        code: z.string(),
        message: z.string(),
        consecutiveTerms: z.number().nullish(),
        maxConsecutiveTerms: z.number().nullish(),
        affectedMembershipId: z.number().nullish(),
        affectedMemberId: z.number().nullish(),
        affectedMemberName: z.string().nullish(),
        affectedBodyName: z.string().nullish(),
        confirmed: z.boolean().default(false),
    })).default([]),
    violations: z.array(z.object({code: z.string(), message: z.string()})).default([]),
});

export const assignmentResponseSchema = z.object({
    membership: membershipSchema,
    endedMemberships: z.array(membershipSchema).default([]),
    check: assignmentCheckSchema.nullish(),
});

export const meLinkedSchema = z.object({
    linked: z.literal(true),
    member: memberDetailSchema,
    permissions: z.object({
        global: z.array(z.string()),
        perDistrict: z.array(z.object({
            districtId: z.number(),
            districtName: z.string(),
            permissions: z.array(z.string()),
        })),
    }),
    canSeeMembers: z.boolean(),
    canManageBodies: z.boolean(),
    pendingChangeRequest: z.boolean(),
});

export const meSchema = z.union([z.object({linked: z.literal(false)}), meLinkedSchema]);

export type MemberDetail = z.infer<typeof memberDetailSchema>;
export type MemberSummary = z.infer<typeof memberSummarySchema>;
export type MemberPage = z.infer<typeof memberPageSchema>;
export type Affiliation = z.infer<typeof affiliationSchema>;
export type BodyFunction = z.infer<typeof bodyFunctionSchema>;
export type ChangeRequest = z.infer<typeof changeRequestSchema>;
export type ChangeRequestDetail = z.infer<typeof changeRequestDetailSchema>;
export type ChangeRequestStatus = z.infer<typeof changeRequestStatusSchema>;
export type District = z.infer<typeof districtSchema>;
export type Body = z.infer<typeof bodySchema>;
export type MemberType = z.infer<typeof memberTypeSchema>;
export type CompositionEntry = z.infer<typeof compositionEntrySchema>;
export type BodyDetail = z.infer<typeof bodyDetailSchema>;
export type BodyTemplate = z.infer<typeof templateSchema>;
export type Membership = z.infer<typeof membershipSchema>;
export type AssignmentCheck = z.infer<typeof assignmentCheckSchema>;
export type AssignmentResponse = z.infer<typeof assignmentResponseSchema>;
export type LinkedMe = z.infer<typeof meLinkedSchema>;

export const suspensionCheckSchema = z.object({
    violations: z.array(z.object({code: z.string(), message: z.string()})).default([]),
    warnings: z.array(z.object({
        code: z.string(),
        message: z.string(),
        confirmed: z.boolean().default(false),
    })).default([]),
    mandatesAtStake: z.array(mandateRefSchema).default([]),
});

export type SuspensionCheck = z.infer<typeof suspensionCheckSchema>;
export type MandateRef = z.infer<typeof mandateRefSchema>;

export type ApiError = {
    status: number;
    code: string;
    message: string;
    fields?: string[];
    check?: AssignmentCheck | SuspensionCheck;
};

export type ApiResult<T> = { ok: true; data: T } | { ok: false; error: ApiError };

export type ActionResult<T = undefined> =
    | { ok: true; data?: T; redirectTo?: string }
    | { ok: false; error: ApiError };

export const PERMISSIONS = {
    contactRead: 'CONTACT_PERSONAL_DATA_READ',
    contactManage: 'CONTACT_PERSONAL_DATA_MANAGE',
    formalRead: 'FORMAL_PERSONAL_DATA_READ',
    formalManage: 'FORMAL_PERSONAL_DATA_MANAGE',
    bodiesManage: 'BODIES_MANAGE',
    recruitmentRead: 'RECRUITMENT_READ',
    recruitmentManage: 'RECRUITMENT_MANAGE',
    recruitmentApprove: 'RECRUITMENT_APPROVE',
    feesRead: 'FEES_READ',
    feesManage: 'FEES_MANAGE',
    suspensionsRead: 'SUSPENSIONS_READ',
    suspensionsManage: 'SUSPENSIONS_MANAGE',
} as const;
