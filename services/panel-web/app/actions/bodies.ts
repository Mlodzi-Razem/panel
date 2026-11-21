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

import { revalidatePath } from "next/cache";
import * as z from "zod";
import {
    dateSchema,
    denyUnlessSignedIn,
    fail,
    idSchema,
    invalid,
    optionalText,
    parseId,
    text,
} from "@/app/actions/common";
import {
    assignMembership,
    checkAssignment,
    createBody,
    endMembership,
    updateBody,
} from "@/panel-api/members/bodies";
import { ActionResult, AssignmentCheck, AssignmentResponse } from "@/panel-api/members/types";

const assignmentSchema = z.object({
    memberId: idSchema,
    memberTypeId: idSchema,
    validFrom: dateSchema.optional(),
    validTo: dateSchema.optional(),
});

function refresh() {
    revalidatePath('/bodies', 'layout');
    revalidatePath('/members', 'layout');
    revalidatePath('/user');
    revalidatePath('/teams', 'layout');
}

export async function createBodyAction(formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const templateId = parseId(formData.get('templateId'));
    if (templateId === null) {
        return invalid('templateId');
    }
    const parentRaw = formData.get('parentBodyId');
    const parentBodyId = parseId(parentRaw);
    const result = await createBody({
        templateId,
        name: text(formData, 'name'),
        ...(optionalText(formData, 'email') ? {email: optionalText(formData, 'email')} : {}),
        ...(parentBodyId !== null ? {parentBodyId} : {}),
        ...(optionalText(formData, 'code') ? {code: optionalText(formData, 'code')} : {}),
    });
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, redirectTo: `/bodies/${result.data.id}`};
}

export async function updateBodyAction(id: number, formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }
    const result = await updateBody(id, {name: text(formData, 'name'), email: text(formData, 'email')});
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true};
}

export async function checkAssignmentAction(bodyId: number, input: unknown): Promise<ActionResult<AssignmentCheck>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = assignmentSchema.safeParse(input);
    if (!idSchema.safeParse(bodyId).success || !parsed.success) {
        return invalid('memberId', 'memberTypeId');
    }
    const result = await checkAssignment(bodyId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    return {ok: true, data: result.data};
}

export async function assignMembershipAction(
    bodyId: number,
    input: unknown,
    confirmed: boolean,
): Promise<ActionResult<AssignmentResponse>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = assignmentSchema.safeParse(input);
    if (!idSchema.safeParse(bodyId).success || !parsed.success) {
        return invalid('memberId', 'memberTypeId');
    }
    const result = await assignMembership(bodyId, parsed.data, confirmed === true);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, data: result.data};
}

export async function endMembershipAction(
    bodyId: number,
    membershipId: number,
    endDate: string,
): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const date = endDate.trim();
    if (!idSchema.safeParse(bodyId).success || !idSchema.safeParse(membershipId).success
        || (date.length > 0 && !dateSchema.safeParse(date).success)) {
        return invalid('endDate');
    }
    const result = await endMembership(bodyId, membershipId, date.length > 0 ? date : undefined);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true};
}
