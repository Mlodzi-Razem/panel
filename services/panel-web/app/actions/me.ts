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
import { denyUnlessSignedIn, fail, text } from "@/app/actions/common";
import { changedPayload, fieldsOfGroups, readSubmitted } from "@/panel-api/members/fields";
import {
    cancelOwnChangeRequest,
    createOwnChangeRequest,
    getMe,
    updateOwnProfile,
} from "@/panel-api/members/me";
import { ActionResult } from "@/panel-api/members/types";

const NOT_LINKED = {status: 403, code: 'NOT_LINKED', message: 'Account not linked'};
const NO_CHANGES = {status: 422, code: 'NO_CHANGES', message: 'Nothing changed'};

export async function updateOwnProfileAction(formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const me = await getMe();
    if (me.kind !== 'linked') {
        return fail(NOT_LINKED);
    }

    const defs = fieldsOfGroups(['CONTACT', 'NAMING']);
    const payload = changedPayload(readSubmitted(formData, defs), me.me.member.fields, defs);
    if (Object.keys(payload).length === 0) {
        return fail(NO_CHANGES);
    }

    const result = await updateOwnProfile(payload);
    if (!result.ok) {
        return fail(result.error);
    }
    revalidatePath('/user');
    return {ok: true};
}

export async function requestFormalChangeAction(formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const me = await getMe();
    if (me.kind !== 'linked') {
        return fail(NOT_LINKED);
    }

    const defs = fieldsOfGroups(['FORMAL']);
    const payload = changedPayload(readSubmitted(formData, defs), me.me.member.fields, defs);
    if (Object.keys(payload).length === 0) {
        return fail(NO_CHANGES);
    }

    const reason = text(formData, 'reason');
    const result = await createOwnChangeRequest(payload, reason.length > 0 ? reason : null);
    if (!result.ok) {
        return fail(result.error);
    }
    revalidatePath('/user');
    return {ok: true};
}

export async function cancelChangeRequestAction(id: number): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!Number.isInteger(id) || id <= 0) {
        return fail({status: 400, code: 'BAD_REQUEST', message: 'Invalid request id'});
    }
    const result = await cancelOwnChangeRequest(id);
    if (!result.ok) {
        return fail(result.error);
    }
    revalidatePath('/user');
    return {ok: true};
}
