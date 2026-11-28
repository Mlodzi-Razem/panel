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
import { denyUnlessSignedIn, dateSchema, fail, idSchema, invalid, parseId, text } from "@/app/actions/common";
import { createPayload, FIELDS, readSubmitted } from "@/panel-api/members/fields";
import {
    changeMembershipType,
    grantHonoraryMember,
    grantHonoraryToMember,
    grantSupportingMember,
    reinstateMember,
    removeMember,
} from "@/panel-api/members/membershipActs";
import { ActionResult } from "@/panel-api/members/types";

const referenceSchema = z.string().trim().min(1).max(200);
const reasonSchema = z.string().trim().min(1).max(500);
const removalGrounds = [
    'DEATH', 'INCAPACITATION', 'LOSS_OF_PUBLIC_RIGHTS', 'RESIGNATION', 'AGE_26', 'EXCLUSION', 'HONORARY_REVOKED',
] as const;

const typeChangeSchema = z.object({
    membershipType: z.enum(['ORDINARY', 'SUPPORTING']),
    effectiveOn: dateSchema,
    decisionReference: referenceSchema,
    reason: reasonSchema,
});
const grantedOnSchema = z.object({grantedOn: dateSchema, decisionReference: referenceSchema});
const removalSchema = z.object({
    ground: z.enum(removalGrounds),
    removedOn: dateSchema,
    decisionReference: referenceSchema,
});
const reinstatementSchema = z.object({
    effectiveOn: dateSchema,
    decisionReference: referenceSchema,
    reason: reasonSchema,
});

function refresh(id?: number) {
    revalidatePath('/members');
    if (id !== undefined) {
        revalidatePath(`/members/${id}`);
    }
}

export async function createActMemberAction(
    kind: 'SUPPORTING' | 'HONORARY',
    formData: FormData,
): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const districtId = parseId(formData.get('primaryDistrictId'));
    if (districtId === null) {
        return invalid('primaryDistrictId');
    }
    const act = grantedOnSchema.safeParse({
        grantedOn: text(formData, 'grantedOn'),
        decisionReference: text(formData, 'decisionReference'),
    });
    if (!act.success) {
        return invalid(act.error.issues[0]?.path[0]?.toString() ?? 'decisionReference');
    }

    const body = {
        primaryDistrictId: districtId,
        fields: createPayload(readSubmitted(formData, FIELDS)),
        grantedOn: act.data.grantedOn,
        decisionReference: act.data.decisionReference,
    };
    const result = kind === 'SUPPORTING'
        ? await grantSupportingMember(body)
        : await grantHonoraryMember(body);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, redirectTo: `/members/${result.data.id}`};
}

export async function grantHonoraryToMemberAction(memberId: number, input: unknown): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = grantedOnSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !parsed.success) {
        return invalid('grantedOn');
    }
    const result = await grantHonoraryToMember(memberId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(memberId);
    return {ok: true};
}

export async function removeMemberAction(memberId: number, input: unknown): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = removalSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !parsed.success) {
        return invalid('ground');
    }
    const result = await removeMember(memberId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(memberId);
    return {ok: true};
}

export async function reinstateMemberAction(memberId: number, input: unknown): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = reinstatementSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !parsed.success) {
        return invalid('reason');
    }
    const result = await reinstateMember(memberId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(memberId);
    return {ok: true};
}

export async function changeMembershipTypeAction(memberId: number, input: unknown): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = typeChangeSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !parsed.success) {
        return invalid('membershipType');
    }
    const result = await changeMembershipType(memberId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(memberId);
    return {ok: true};
}
