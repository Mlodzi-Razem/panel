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
import { apiCall, apiMultipartCall } from "@/panel-api/members/client";
import { ApiResult } from "@/panel-api/members/types";

/**
 * The recruitment intake statuses. The list mirrors the API enum; the *edges* between them are NOT
 * copied here — the detail view asks GET /recruitment-cases/{id}/transitions, so the server stays
 * the single source of the state machine.
 */
export const CASE_STATUSES = [
    'NEW', 'IN_PROGRESS', 'NO_CONTACT', 'AWAITING_DECLARATION', 'DECLARATION_RECEIVED',
    'PROBATION', 'PROBATION_EXTENDED', 'ZO_RECOMMENDED', 'ZK_VOTING', 'ACCEPTED', 'REJECTED',
] as const;

export const CASE_SOURCES = ['GOOGLE_FORM', 'WEBSITE', 'MANUAL'] as const;

export const caseStatusSchema = z.enum(CASE_STATUSES);
export const caseSourceSchema = z.enum(CASE_SOURCES);
export const rejectionReasonSchema = z.enum([
    'NOT_FIT', 'NO_DECLARATION', 'NO_CONTACT', 'VOTE_FAILED', 'FALSE_ENTRY', 'ZK_BAN',
]);
export const caseEventTypeSchema = z.enum([
    'CASE_CREATED', 'STATUS_CHANGED', 'REMINDER_SENT', 'ZK_NOTIFIED_PROBATION_START',
    'DECLARATION_RECORDED', 'DECLARATION_LINK_CREATED', 'RECOMMENDATION_SENT', 'VOTE_STARTED',
    'DECISION_RECORDED', 'CASE_CLOSED', 'NOTE_ADDED', 'CANDIDATE_DATA_CORRECTED',
]);

/** Post-declaration metadata served by the M3 engine (statute clocks and collegial decisions). */
export const declarationFormSchema = z.enum(['SCAN', 'ELECTRONIC']);
export const assessmentTrackSchema = z.enum(['STANDARD', 'AFFILIATED', 'ENGAGED']);
export const decisionKindSchema = z.enum([
    'ZO_ASSESSMENT_POSITIVE', 'ZO_ASSESSMENT_NEGATIVE', 'PERIOD_EXTENSION', 'REJECTION',
    'ZK_APPROVAL', 'ZO_ADMISSION_RESOLUTION',
]);

/**
 * A collegial resolution recorded on the case. Only ZK_APPROVAL and ZO_ADMISSION_RESOLUTION
 * can be written over HTTP; the deciding body names who passed it; the collegial note
 * stays in the audit trail per the GDPR notes-only rule.
 */
export const caseDecisionSchema = z.object({
    id: z.number(),
    kind: decisionKindSchema,
    decidedOn: z.string(),
    decisionSignature: z.string(),
    decidingBodyId: z.number(),
    decidingBodyName: z.string(),
});

export const caseDistrictRefSchema = z.object({
    id: z.number(),
    name: z.string(),
    code: z.string().nullish(),
});

export const caseSummarySchema = z.object({
    id: z.number(),
    district: caseDistrictRefSchema,
    candidateFirstName: z.string().nullish(),
    candidateLastName: z.string().nullish(),
    candidateEmail: z.string().nullish(),
    candidatePhone: z.string().nullish(),
    status: caseStatusSchema,
    source: caseSourceSchema,
    createdAt: z.string(),
    updatedAt: z.string().nullish(),
    closedAt: z.string().nullish(),
    rejectionReason: rejectionReasonSchema.nullish(),
    // retention (M8): anonymized cases have no candidate name; due dates exist for rejected cases only
    anonymizedAt: z.string().nullish(),
    anonymizationDueOn: z.string().nullish(),
});

export const casePageSchema = z.object({
    items: z.array(caseSummarySchema),
    page: z.number(),
    size: z.number(),
    totalItems: z.number(),
    totalPages: z.number(),
});

export const caseEventSchema = z.object({
    id: z.number(),
    eventType: caseEventTypeSchema,
    payload: z.unknown().nullish(),
    createdAt: z.string(),
    actor: z.object({id: z.number(), displayName: z.string()}).nullish(),
});

export const caseDetailSchema = z.object({
    id: z.number(),
    district: caseDistrictRefSchema,
    candidateFirstName: z.string().nullish(),
    candidateLastName: z.string().nullish(),
    candidateEmail: z.string().nullish(),
    candidatePhone: z.string().nullish(),
    candidateSocialProfileUrl: z.string().nullish(),
    candidateBirthDate: z.string().nullish(),
    // Formal data of the declaration. The PESEL itself is never served — only whether it is on file.
    candidateFormalFirstName: z.string().nullish(),
    candidateFormalLastName: z.string().nullish(),
    candidatePreferredFirstName: z.string().nullish(),
    candidatePreferredLastName: z.string().nullish(),
    candidatePronouns: z.string().nullish(),
    formalIdentifierPresent: z.boolean().default(false),
    status: caseStatusSchema,
    source: caseSourceSchema,
    rejectionReason: rejectionReasonSchema.nullish(),
    closedAt: z.string().nullish(),
    createdAt: z.string(),
    updatedAt: z.string().nullish(),
    declarationForm: declarationFormSchema.nullish(),
    declarationReference: z.string().nullish(),
    declarationReceivedOn: z.string().nullish(),
    assessmentTrack: assessmentTrackSchema.nullish(),
    assessmentNotBeforeOn: z.string().nullish(),
    assessmentNotLaterOn: z.string().nullish(),
    extensionCount: z.number().default(0),
    extensionDecidedOn: z.string().nullish(),
    secondAssessmentNotLaterOn: z.string().nullish(),
    zoRecommendedOn: z.string().nullish(),
    admissionResolutionNotLaterOn: z.string().nullish(),
    acceptedOn: z.string().nullish(),
    acceptedMemberId: z.number().nullish(),
    acceptedMemberNumber: z.string().nullish(),
    decisions: z.array(caseDecisionSchema),
    events: z.array(caseEventSchema),
    anonymizedAt: z.string().nullish(),
    artifactsPurgedAt: z.string().nullish(),
    artifactsDueOn: z.string().nullish(),
    anonymizationDueOn: z.string().nullish(),
});

/** One candidate move served by GET /recruitment-cases/{id}/transitions — the server's truth. */
export const caseTransitionSchema = z.object({
    target: caseStatusSchema,
    type: z.literal('STATUS_CHANGED'),
    allowed: z.boolean(),
    rejectionReasons: z.array(rejectionReasonSchema).nullish(),
    blockedReason: z.string().nullish(),
});

export const caseTransitionsSchema = z.object({
    transitions: z.array(caseTransitionSchema),
});

export type CaseStatus = z.infer<typeof caseStatusSchema>;
export type CaseSource = z.infer<typeof caseSourceSchema>;
export type RejectionReason = z.infer<typeof rejectionReasonSchema>;
export type CaseEvent = z.infer<typeof caseEventSchema>;
export type DeclarationForm = z.infer<typeof declarationFormSchema>;
export type AssessmentTrack = z.infer<typeof assessmentTrackSchema>;
export type DecisionKind = z.infer<typeof decisionKindSchema>;
export type CaseDecision = z.infer<typeof caseDecisionSchema>;
export type CaseSummary = z.infer<typeof caseSummarySchema>;
export type CasePage = z.infer<typeof casePageSchema>;
export type CaseDetail = z.infer<typeof caseDetailSchema>;
export type CaseTransition = z.infer<typeof caseTransitionSchema>;

/** Predefined list views of the recruitment procedure; the API owns the criteria. */
export const CASE_VIEWS = ['TO_CONDUCT', 'PROBABLY_NO_DECLARATION', 'RECOMMENDED', 'ZK_VOTING', 'TO_DELETE'] as const;
export type CaseView = typeof CASE_VIEWS[number];

export const caseViewCountsSchema = z.record(z.string(), z.number());

export type CaseFilters = {
    view?: CaseView;
    page?: number;
    size?: number;
    district?: number;
    status?: CaseStatus;
    open?: boolean;
};

/** Collegial decision input the post-declaration transitions and POST /decisions accept. */
export type DecisionInput = {
    decidingBodyId?: number;
    decidedOn?: string;
    signature?: string;
    note?: string;
};

/** POST /recruitment-cases/{id}/declaration-link contract (built by M4a): 201 with the link data. */
export const declarationLinkSchema = z.object({
    token: z.string(),
    formUrl: z.string(),
    expiresAt: z.string(),
    /** MailDispatch name (SCHEDULED, DISABLED, REFUSED_*, INVALID_RECIPIENT) or NO_EMAIL; never the address. */
    emailDispatch: z.string(),
});

export type DeclarationLink = z.infer<typeof declarationLinkSchema>;

/** Candidate data as the API accepts it: values are plain strings; birth date is YYYY-MM-DD. */
export type CandidateInput = {
    candidateFirstName?: string;
    candidateLastName?: string;
    candidateEmail?: string;
    candidatePhone?: string;
    candidateSocialProfileUrl?: string;
    candidateBirthDate?: string;
};

export function listRecruitmentCases(filters: CaseFilters): Promise<ApiResult<CasePage>> {
    return apiCall({
        method: 'GET',
        path: '/recruitment-cases',
        query: {
            page: filters.page,
            size: filters.size,
            district: filters.district,
            status: filters.status,
            open: filters.open === undefined ? undefined : String(filters.open),
            view: filters.view,
        },
    }, casePageSchema);
}

export function getRecruitmentViewCounts(district?: number): Promise<ApiResult<Record<string, number>>> {
    return apiCall({
        method: 'GET',
        path: '/recruitment-cases/views',
        query: {district},
    }, caseViewCountsSchema);
}

export function getRecruitmentCase(id: number): Promise<ApiResult<CaseDetail>> {
    return apiCall({method: 'GET', path: `/recruitment-cases/${id}`}, caseDetailSchema);
}

export function getCaseTransitions(id: number): Promise<ApiResult<{ transitions: CaseTransition[] }>> {
    return apiCall({method: 'GET', path: `/recruitment-cases/${id}/transitions`}, caseTransitionsSchema);
}

export function createRecruitmentCase(
    districtBodyId: number,
    source: CaseSource,
    candidate: CandidateInput,
): Promise<ApiResult<CaseDetail>> {
    return apiCall({
        method: 'POST',
        path: '/recruitment-cases',
        body: {districtBodyId, source, candidate},
    }, caseDetailSchema);
}

export function patchRecruitmentCase(id: number, candidate: CandidateInput): Promise<ApiResult<CaseDetail>> {
    return apiCall({
        method: 'PATCH',
        path: `/recruitment-cases/${id}`,
        body: {candidate},
    }, caseDetailSchema);
}

export function transitionRecruitmentCase(
    id: number,
    target: CaseStatus,
    rejectionReason?: RejectionReason,
    decision?: DecisionInput,
): Promise<ApiResult<CaseDetail>> {
    return apiCall({
        method: 'POST',
        path: `/recruitment-cases/${id}/transitions`,
        body: {
            target,
            ...(rejectionReason ? {rejectionReason} : {}),
            ...(decision?.decidingBodyId ? {decidingBodyId: decision.decidingBodyId} : {}),
            ...(decision?.decidedOn ? {decidedOn: decision.decidedOn} : {}),
            ...(decision?.signature ? {signature: decision.signature} : {}),
            ...(decision?.note ? {note: decision.note} : {}),
        },
    }, caseDetailSchema);
}

export function recordCaseDecision(
    id: number,
    kind: DecisionKind,
    decidedOn: string,
    signature: string,
    note?: string,
): Promise<ApiResult<CaseDetail>> {
    return apiCall({
        method: 'POST',
        path: `/recruitment-cases/${id}/decisions`,
        body: {kind, decidedOn, signature, ...(note ? {note} : {})},
    }, caseDetailSchema);
}

export function generateDeclarationLink(id: number): Promise<ApiResult<DeclarationLink>> {
    return apiCall({method: 'POST', path: `/recruitment-cases/${id}/declaration-link`, body: {}},
        declarationLinkSchema);
}

/** POST /recruitment-cases/{id}/declaration-scan: the file part plus the declaration data as text parts. */
export const declarationScanResultSchema = z.object({status: z.string()});

export function uploadDeclarationScan(id: number, body: FormData): Promise<ApiResult<{ status: string }>> {
    return apiMultipartCall(`/recruitment-cases/${id}/declaration-scan`, body, declarationScanResultSchema);
}

/** Candidate data correction: only the given fields change; blank preferred names / pronouns clear them. */
export type CandidateDataCorrectionInput = {
    formalFirstName?: string;
    formalLastName?: string;
    preferredFirstName?: string;
    preferredLastName?: string;
    pronouns?: string;
    personalId?: string;
    reason: string;
};

export function correctCandidateData(
    id: number,
    correction: CandidateDataCorrectionInput,
): Promise<ApiResult<CaseDetail>> {
    return apiCall({
        method: 'POST',
        path: `/recruitment-cases/${id}/candidate-data-corrections`,
        body: correction,
    }, caseDetailSchema);
}

export function addCaseNote(id: number, note: string): Promise<ApiResult<CaseDetail>> {
    return apiCall({method: 'POST', path: `/recruitment-cases/${id}/notes`, body: {note}}, caseDetailSchema);
}

export function sendCaseReminder(id: number): Promise<ApiResult<CaseDetail>> {
    return apiCall({method: 'POST', path: `/recruitment-cases/${id}/reminders`, body: {}}, caseDetailSchema);
}
