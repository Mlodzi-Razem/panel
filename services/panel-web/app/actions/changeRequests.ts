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
import { denyUnlessSignedIn, fail, idSchema, invalid } from "@/app/actions/common";
import { decideChangeRequest } from "@/panel-api/members/changeRequests";
import { ActionResult } from "@/panel-api/members/types";

export async function decideChangeRequestAction(
    id: number,
    decision: 'approve' | 'reject',
    note: string,
): Promise<ActionResult> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success || (decision !== 'approve' && decision !== 'reject')) {
        return invalid('id');
    }
    const trimmed = note.trim().slice(0, 2000);
    const result = await decideChangeRequest(id, decision, trimmed.length > 0 ? trimmed : null);
    if (!result.ok) {
        return fail(result.error);
    }
    revalidatePath('/members', 'layout');
    revalidatePath('/user');
    return {ok: true, redirectTo: '/members/change-requests'};
}
