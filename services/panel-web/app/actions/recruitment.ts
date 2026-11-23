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

"use server";

/*
 * Two families of server actions share this file:
 * - back-office case actions ("denyUnlessSignedIn"): the ZO/ZK operator surface;
 * - the anonymous declaration action: the candidate follows a magic link and never signs
 *   in — the token is the bearer credential, passed straight to the contract client
 *   and never logged.
 */

import { revalidatePath } from "next/cache";
import { denyUnlessSignedIn, fail, idSchema, invalid, parseId, text } from "@/app/actions/common";
import { ActionResult } from "@/panel-api/members/types";
import {
    addCaseNote,
    CandidateDataCorrectionInput,
    CandidateInput,
    CASE_SOURCES,
    CASE_STATUSES,
    CaseSource,
    CaseStatus,
    correctCandidateData,
    createRecruitmentCase,
    DecisionInput,
    DecisionKind,
    decisionKindSchema,
    DeclarationLink,
    generateDeclarationLink,
    getRecruitmentCase,
    patchRecruitmentCase,
    recordCaseDecision,
    RejectionReason,
    rejectionReasonSchema,
    sendCaseReminder,
    transitionRecruitmentCase,
} from "@/panel-api/members/recruitment";
import { isValidPesel } from "@/util/pesel";
import { submitDeclarationForm, submissionValuesSchema } from "@/panel-api/recruitment/declarationForm";

const NOTE_MAX_LENGTH = 2000;

function refresh(id?: number) {
    revalidatePath('/rekrutacja');
    revalidatePath('/rekrutacja/nowa');
    if (id !== undefined) {
        revalidatePath(`/rekrutacja/${id}`);
    }
}

function candidateFromForm(formData: FormData): { candidate: CandidateInput; errors: string[] } {
    const candidate: CandidateInput = {};
    const errors: string[] = [];

    const firstName = text(formData, 'candidateFirstName');
    const lastName = text(formData, 'candidateLastName');
    if (firstName.length === 0 && lastName.length === 0) {
        candidate.candidateFirstName = '';
        candidate.candidateLastName = '';
    } else {
        candidate.candidateFirstName = firstName;
        candidate.candidateLastName = lastName;
        if (firstName.length === 0 || lastName.length === 0) {
            errors.push('candidateFirstName', 'candidateLastName');
        }
    }

    const email = text(formData, 'candidateEmail');
    if (email.length > 0) {
        candidate.candidateEmail = email;
    }

    const phone = text(formData, 'candidatePhone');
    if (phone.length > 0) {
        candidate.candidatePhone = phone;
    }

    const profile = text(formData, 'candidateSocialProfileUrl');
    if (profile.length > 0) {
        candidate.candidateSocialProfileUrl = profile;
    }

    const birthDate = text(formData, 'candidateBirthDate');
    if (birthDate.length > 0) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) {
            errors.push('candidateBirthDate');
        } else {
            candidate.candidateBirthDate = birthDate;
        }
    }

    return {candidate, errors};
}

export async function createRecruitmentCaseAction(formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }

    const districtBodyId = parseId(formData.get('districtBodyId'));
    if (districtBodyId === null) {
        return invalid('districtBodyId');
    }

    const source = text(formData, 'source');
    if (!CASE_SOURCES.includes(source as CaseSource)) {
        return invalid('source');
    }

    const {candidate, errors} = candidateFromForm(formData);
    if (errors.length > 0) {
        return invalid(...errors);
    }

    const result = await createRecruitmentCase(districtBodyId, source as CaseSource, candidate);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(result.data.id);
    return {ok: true, redirectTo: `/rekrutacja/${result.data.id}`};
}

export async function updateRecruitmentCaseAction(id: number, formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }

    const current = await getRecruitmentCase(id);
    if (!current.ok) {
        return fail(current.error);
    }

    const {candidate, errors} = candidateFromForm(formData);
    if (errors.length > 0) {
        return invalid(...errors);
    }

    // Send only what really changed: a blank optional field is an explicit clear, an untouched
    // field must stay as it is (PATCH blanks clear optional fields, absent fields stay).
    const currentValues: Record<string, string | null> = {
        candidateFirstName: current.data.candidateFirstName ?? '',
        candidateLastName: current.data.candidateLastName ?? '',
        candidateEmail: current.data.candidateEmail ?? null,
        candidatePhone: current.data.candidatePhone ?? null,
        candidateSocialProfileUrl: current.data.candidateSocialProfileUrl ?? null,
        candidateBirthDate: current.data.candidateBirthDate ?? null,
    };
    const changes: CandidateInput = {};
    const optionalFields = ['candidateEmail', 'candidatePhone', 'candidateSocialProfileUrl', 'candidateBirthDate'];
    for (const [field, value] of Object.entries(candidate)) {
        const normalized = optionalFields.includes(field) && (value === '' || value === undefined)
            ? null
            : value;
        const before = currentValues[field];
        const after = normalized === undefined ? before : normalized;
        if (after !== before) {
            (changes as Record<string, string | null>)[field] = after as string;
        }
    }

    if (Object.keys(changes).length === 0) {
        return fail({status: 422, code: 'NO_CHANGES', message: 'Nothing changed'});
    }

    const result = await patchRecruitmentCase(id, changes);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(id);
    return {ok: true};
}

const CORRECTION_REASON_MAX_LENGTH = 500;

/**
 * Reasoned correction of the candidate's formal data. The form is prefilled with the current
 * values, so only what differs is sent (the API changes only what it receives); the PESEL field
 * is empty by design — the value is never served — and is sent only when filled in.
 */
export async function correctCandidateDataAction(id: number, formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }

    const reason = text(formData, 'reason');
    if (reason.length === 0 || reason.length > CORRECTION_REASON_MAX_LENGTH) {
        return fail({
            status: 422, code: 'CORRECTION_REQUIRES_REASON', message: 'A correction needs a reason', fields: ['reason'],
        });
    }

    const current = await getRecruitmentCase(id);
    if (!current.ok) {
        return fail(current.error);
    }

    const correction: CandidateDataCorrectionInput = {reason};

    const formalFirst = text(formData, 'formalFirstName');
    const formalLast = text(formData, 'formalLastName');
    if (formalFirst !== (current.data.candidateFormalFirstName ?? '')
        || formalLast !== (current.data.candidateFormalLastName ?? '')) {
        correction.formalFirstName = formalFirst;
        correction.formalLastName = formalLast;
    }

    const preferredFirst = text(formData, 'preferredFirstName');
    const preferredLast = text(formData, 'preferredLastName');
    if (preferredFirst !== (current.data.candidatePreferredFirstName ?? '')
        || preferredLast !== (current.data.candidatePreferredLastName ?? '')) {
        correction.preferredFirstName = preferredFirst;
        correction.preferredLastName = preferredLast;
    }

    const pronouns = text(formData, 'pronouns');
    if (pronouns !== (current.data.candidatePronouns ?? '')) {
        correction.pronouns = pronouns;
    }

    const personalId = text(formData, 'personalId');
    if (personalId.length > 0) {
        if (!isValidPesel(personalId)) {
            return fail({
                status: 422, code: 'PERSONAL_ID_INVALID', message: 'Invalid personal identifier',
                fields: ['personalId'],
            });
        }
        correction.personalId = personalId;
    }

    const result = await correctCandidateData(id, correction);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(id);
    return {ok: true};
}

export async function transitionCaseAction(
    id: number,
    target: string,
    rejectionReason?: string,
    decision?: DecisionInput,
): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success || !CASE_STATUSES.includes(target as CaseStatus)) {
        return invalid('target');
    }

    const statusTarget = target as CaseStatus;
    const reason = rejectionReason === undefined || rejectionReason.length === 0
        ? undefined
        : rejectionReasonSchema.safeParse(rejectionReason).success
            ? rejectionReason as RejectionReason
            : null;
    if (reason === null) {
        return invalid('rejectionReason');
    }

    const result = await transitionRecruitmentCase(id, statusTarget, reason, decision);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(id);
    return {ok: true};
}

/**
 * Form variant of the transition with the collegial decision input for the post-declaration
 * cluster: ZO_RECOMMENDED/PROBATION_EXTENDED/rejected-after-declaration carry the deciding
 * body, the resolution date and the protocol signature, exactly as POST /transitions takes them.
 */
export async function transitionCaseFormAction(id: number, formData: FormData): Promise<ActionResult> {
    const target = text(formData, 'target');
    if (!CASE_STATUSES.includes(target as CaseStatus)) {
        return invalid('target');
    }

    const decision = decisionInputFromForm(formData);
    if ('error' in decision) {
        return fail(decision.error);
    }

    return transitionCaseAction(
        id,
        target,
        text(formData, 'rejectionReason') || undefined,
        decision.input,
    );
}

function decisionInputFromForm(formData: FormData):
    {input: DecisionInput} | {error: {status: number, code: string, message: string, fields?: string[]}} {
    const decidingBodyId = parseId(formData.get('decidingBodyId'));
    const decidedOn = text(formData, 'decidedOn');
    const signature = text(formData, 'signature');
    const note = text(formData, 'note');

    const carriesDecision = decidingBodyId !== null || decidedOn.length > 0 || signature.length > 0;
    if (!carriesDecision) {
        return {input: {}};
    }

    if (decidingBodyId === null) {
        return {error: {status: 400, code: 'FIELD_REQUIRED', message: 'The deciding body is required',
            fields: ['decidingBodyId']}};
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(decidedOn)) {
        return {error: {status: 400, code: 'FIELD_REQUIRED', message: 'The resolution date is required',
            fields: ['decidedOn']}};
    }
    if (signature.trim().length === 0) {
        return {error: {status: 400, code: 'FIELD_REQUIRED', message: 'The protocol signature is required',
            fields: ['signature']}};
    }

    return {input: {
        decidingBodyId,
        decidedOn,
        signature,
        ...(note.length > 0 ? {note} : {}),
    }};
}

/** POST /recruitment-cases/{id}/decisions: a collegial resolution logged without a status flip. */
export async function recordCaseDecisionAction(id: number, formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }

    const kind = text(formData, 'kind');
    if (!decisionKindSchema.safeParse(kind).success) {
        return invalid('kind');
    }

    const decidedOn = text(formData, 'decidedOn');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(decidedOn)) {
        return invalid('decidedOn');
    }

    const signature = text(formData, 'signature');
    if (signature.trim().length === 0) {
        return invalid('signature');
    }

    const result = await recordCaseDecision(id, kind as DecisionKind, decidedOn, signature,
        text(formData, 'note') || undefined);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(id);
    return {ok: true};
}

/**
 * POST /recruitment-cases/{id}/declaration-link: the M4a endpoint serves the declaration form URL.
 * Served to the dialog as data — the dialog copies the URL, it does not navigate anywhere.
 */
export async function generateDeclarationLinkAction(id: number): Promise<ActionResult<DeclarationLink>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }

    const result = await generateDeclarationLink(id);
    if (!result.ok) {
        return fail(result.error);
    }
    return {ok: true, data: result.data};
}

export async function addCaseNoteAction(id: number, formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }

    const note = text(formData, 'note');
    if (note.length === 0) {
        return invalid('note');
    }
    if (note.length > NOTE_MAX_LENGTH) {
        return fail({status: 422, code: 'NOTE_TOO_LONG', message: 'The note is too long'});
    }

    const result = await addCaseNote(id, note);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(id);
    return {ok: true};
}

export async function sendCaseReminderAction(id: number): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }

    const result = await sendCaseReminder(id);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(id);
    return {ok: true};
}

/** Public (anonymous) candidate submit: guarded only by the magic-link token. */
export async function submitDeclarationFormAction(token: string, values: unknown): Promise<ActionResult<"FILLED">> {
    const normalizedToken = token.trim();
    if (normalizedToken.length < 8 || normalizedToken.length > 128) {
        return fail({status: 400, code: "LINK_UNAVAILABLE", message: "Invalid link"});
    }
    const parsed = submissionValuesSchema.safeParse(values);
    if (!parsed.success) {
        return fail({
            status: 400,
            code: "INVALID_VALUE",
            message: "Invalid declaration values",
            fields: parsed.error.issues
                .map(issue => String(issue.path[0] ?? ""))
                .filter(name => name.length > 0),
        });
    }
    return submitDeclarationForm(normalizedToken, parsed.data);
}
