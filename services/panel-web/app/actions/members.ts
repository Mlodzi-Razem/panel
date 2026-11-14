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
import { denyUnlessSignedIn, fail, idSchema, invalid, parseId } from "@/app/actions/common";
import {
    changedPayload,
    createPayload,
    FIELDS,
    fieldsOfGroups,
    FieldGroup,
    readSubmitted,
} from "@/panel-api/members/fields";
import {
    changeAffiliation,
    createMember,
    endAffiliation,
    getMember,
    listMembers,
    updateMember,
} from "@/panel-api/members/members";
import { ActionResult } from "@/panel-api/members/types";

function refresh(id?: number) {
    revalidatePath('/members');
    if (id !== undefined) {
        revalidatePath(`/members/${id}`);
    }
}

export async function updateMemberAction(id: number, formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }

    const current = await getMember(id);
    if (!current.ok) {
        return fail(current.error);
    }

    const groups: FieldGroup[] = [];
    if (current.data.access.manageContact) {
        groups.push('CONTACT', 'NAMING');
    }
    if (current.data.access.manageFormal) {
        groups.push('FORMAL', 'ORGANISATIONAL');
    }
    const defs = fieldsOfGroups(groups);
    const payload = changedPayload(readSubmitted(formData, defs), current.data.fields, defs);
    if (Object.keys(payload).length === 0) {
        return fail({status: 422, code: 'NO_CHANGES', message: 'Nothing changed'});
    }

    const result = await updateMember(id, payload);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(id);
    return {ok: true};
}

export async function createMemberAction(formData: FormData): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const districtId = parseId(formData.get('primaryDistrictId'));
    if (districtId === null) {
        return invalid('primaryDistrictId');
    }

    const payload = createPayload(readSubmitted(formData, FIELDS));
    const result = await createMember(districtId, payload);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh();
    return {ok: true, redirectTo: `/members/${result.data.id}`};
}

export async function revealFormalIdentifierAction(id: number): Promise<ActionResult<string>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid('id');
    }
    const result = await getMember(id, true);
    if (!result.ok) {
        return fail(result.error);
    }
    const value = result.data.fields['formalIdentifier'];
    if (!result.data.access.formalIdentifierRevealed || typeof value !== 'string') {
        return fail({status: 403, code: 'FORBIDDEN', message: 'The identifier cannot be revealed'});
    }
    return {ok: true, data: value};
}

const affiliationSchema = z.discriminatedUnion('action', [
    z.object({action: z.literal('ADD_SUPPORTING'), districtId: idSchema, votesHere: z.boolean()}),
    z.object({action: z.literal('SET_PRIMARY'), districtId: idSchema, keepPreviousAsSupporting: z.boolean()}),
    z.object({action: z.literal('SET_VOTING'), districtId: idSchema}),
]);

export async function changeAffiliationAction(memberId: number, input: unknown): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const parsed = affiliationSchema.safeParse(input);
    if (!idSchema.safeParse(memberId).success || !parsed.success) {
        return invalid('districtId');
    }
    const result = await changeAffiliation(memberId, parsed.data);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(memberId);
    return {ok: true};
}

export async function endAffiliationAction(memberId: number, districtId: number): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(memberId).success || !idSchema.safeParse(districtId).success) {
        return invalid('districtId');
    }
    const result = await endAffiliation(memberId, districtId);
    if (!result.ok) {
        return fail(result.error);
    }
    refresh(memberId);
    return {ok: true};
}

export type MemberPickerItem = { id: number; name: string; memberId?: string };

export async function searchMembersAction(query: string): Promise<ActionResult<MemberPickerItem[]>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const q = query.trim();
    if (q.length < 2) {
        return {ok: true, data: []};
    }
    const result = await listMembers({q: q.slice(0, 100), size: 8});
    if (!result.ok) {
        return fail(result.error);
    }
    const items = result.data.items.map(item => {
        const f = item.fields;
        const str = (key: string) => (typeof f[key] === 'string' ? (f[key] as string) : '');
        const name = [str('preferredNameFirst'), str('preferredNameLast')].filter(Boolean).join(' ')
            || str('pseudonym');
        const memberId = str('memberId');
        return {id: item.id, name, ...(memberId ? {memberId} : {})};
    });
    return {ok: true, data: items};
}
