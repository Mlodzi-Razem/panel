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
} from "@/app/actions/common";
import {
    checkSuspension,
    createSuspension,
    endSuspendedMandates,
    liftSuspension,
    recordAppealEnded,
    Suspension,
    SuspendInput,
    voidSuspension,
    PositionLossView,
} from "@/panel-api/members/suspensions";
import { ActionResult, SuspensionCheck } from "@/panel-api/members/types";

const confirmedSchema = z.array(z.string().max(64)).max(16).optional();

const suspendSchema = z.object({
    kind: z.enum(['ARREARS', 'VOLUNTARY', 'BY_ORGAN']),
    scope: z.enum(['FULL', 'PASSIVE_ELECTORAL', 'PARTIAL_OTHER']),
    scopeDescription: z.string().trim().max(500).optional(),
    suspendingBodyId: idSchema,
    start: dateSchema.optional(),
    end: dateSchema.optional(),
    decisionDate: dateSchema.optional(),
    decisionSignature: z.string().trim().min(1).max(200),
    confirmedWarnings: confirmedSchema,
});

const liftSchema = z.object({
    liftedOn: dateSchema.optional(),
    reason: z.string().trim().min(1).max(500),
    liftingBodyId: idSchema,
    decisionSignature: z.string().trim().min(1).max(200),
    confirmedWarnings: confirmedSchema,
});

const appealSchema = z.object({
    endedOn: dateSchema,
    decisionReference: z.string().trim().min(1).max(200),
});

const voidSchema = z.object({
    reason: z.string().trim().min(1).max(500),
    decisionReference: z.string().trim().max(200).optional(),
});

const endMandatesSchema = z.object({
    membershipIds: z.array(idSchema).min(1).max(200),
    decisionReference: z.string().trim().min(1).max(200),
});

function refresh() {
    revalidatePath('/members', 'layout');
    revalidatePath('/suspensions');
    revalidatePath('/bodies', 'layout');
    revalidatePath('/user');
}

export async function checkSuspensionAction(memberId: number, input: unknown): Promise<ActionResult<SuspensionCheck>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = suspendSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !parsed.success) {
        return invalid('kind', 'scope', 'suspendingBodyId');
    }
    const result = await checkSuspension(memberId, parsed.data as SuspendInput);
    if (!result.ok) {
        return fail(result.error);
    }
    return {ok: true, data: result.data};
}

export async function createSuspensionAction(memberId: number, input: unknown): Promise<ActionResult<Suspension>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = suspendSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !parsed.success) {
        return invalid('kind', 'scope', 'suspendingBodyId');
    }
    const result = await createSuspension(memberId, parsed.data as SuspendInput);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, data: result.data};
}

export async function liftSuspensionAction(
    memberId: number,
    suspensionId: number,
    input: unknown,
): Promise<ActionResult<Suspension>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = liftSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !idSchema.safeParse(suspensionId).success || !parsed.success) {
        return invalid('reason', 'liftingBodyId', 'decisionSignature');
    }
    const result = await liftSuspension(memberId, suspensionId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, data: result.data};
}

export async function appealEndedAction(
    memberId: number,
    suspensionId: number,
    input: unknown,
): Promise<ActionResult<Suspension>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = appealSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !idSchema.safeParse(suspensionId).success || !parsed.success) {
        return invalid('endedOn', 'decisionReference');
    }
    const result = await recordAppealEnded(memberId, suspensionId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, data: result.data};
}

export async function voidSuspensionAction(
    memberId: number,
    suspensionId: number,
    input: unknown,
): Promise<ActionResult<Suspension>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = voidSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !idSchema.safeParse(suspensionId).success || !parsed.success) {
        return invalid('reason');
    }
    const result = await voidSuspension(memberId, suspensionId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, data: result.data};
}

export async function endSuspendedMandatesAction(
    memberId: number,
    suspensionId: number,
    input: unknown,
): Promise<ActionResult<PositionLossView>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = endMandatesSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !idSchema.safeParse(suspensionId).success || !parsed.success) {
        return invalid('membershipIds', 'decisionReference');
    }
    const result = await endSuspendedMandates(memberId, suspensionId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, data: result.data};
}
