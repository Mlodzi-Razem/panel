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

/* Back-office actions on join-form submissions; panel-api enforces who may do what. */

import { revalidatePath } from "next/cache";
import { denyUnlessSignedIn, idSchema, invalid, parseId, text } from "@/app/actions/common";
import { ActionResult } from "@/panel-api/members/types";
import {
    acceptIntake,
    assignIntakeDistrict,
    discardIntake,
    eraseIntake,
    intakeDiscardReasonSchema,
} from "@/panel-api/members/intakes";

function refresh() {
    revalidatePath("/rekrutacja", "layout");
}

export async function acceptIntakeAction(id: number): Promise<ActionResult<{ caseId: number }>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid("id");
    }
    const result = await acceptIntake(id);
    if (!result.ok) {
        return result;
    }
    refresh();
    return {...result, redirectTo: `/rekrutacja/${result.data.caseId}`};
}

export async function assignIntakeDistrictAction(id: number, formData: FormData): Promise<ActionResult<null>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const districtBodyId = parseId(text(formData, "districtBodyId"));
    if (!idSchema.safeParse(id).success || districtBodyId === null) {
        return invalid("districtBodyId");
    }
    const result = await assignIntakeDistrict(id, districtBodyId);
    if (!result.ok) {
        return result;
    }
    refresh();
    // the submission may have left the caller's scope, so go back to the queue
    return {...result, redirectTo: "/rekrutacja/zgloszenia"};
}

export async function discardIntakeAction(id: number, formData: FormData): Promise<ActionResult<null>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    const reason = intakeDiscardReasonSchema.safeParse(text(formData, "reason"));
    if (!idSchema.safeParse(id).success || !reason.success) {
        return invalid("reason");
    }
    const result = await discardIntake(id, reason.data);
    if (result.ok) {
        refresh();
    }
    return result;
}

export async function eraseIntakeAction(id: number): Promise<ActionResult<null>> {
    const denied = await denyUnlessSignedIn();
    if (denied) {
        return denied;
    }
    if (!idSchema.safeParse(id).success) {
        return invalid("id");
    }
    const result = await eraseIntake(id);
    if (result.ok) {
        refresh();
    }
    return result;
}
