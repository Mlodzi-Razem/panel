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

/* The anonymous "I want to join" form: no session, and the values are only passed on, never logged. */

import { headers } from "next/headers";
import { trustedProxyHops } from "@/config/env";
import {
    getIntakeForm,
    IntakeSendResult,
    intakeValuesSchema,
    IntakeValues,
    sendIntake,
} from "@/panel-api/recruitment/intakeForm";

/** The address the outermost trusted proxy saw; entries further left can be forged by the sender. */
async function clientAddress(): Promise<string | undefined> {
    const hops = trustedProxyHops();
    if (hops === 0) {
        return undefined;
    }
    const chain = ((await headers()).get("x-forwarded-for") ?? "").split(",").map(p => p.trim()).filter(Boolean);
    return chain[chain.length - hops];
}

export async function submitIntakeAction(values: IntakeValues): Promise<IntakeSendResult> {
    const parsed = intakeValuesSchema.safeParse(values);
    if (!parsed.success) {
        const fields = parsed.error.issues.map(issue => String(issue.path[0]));
        return {ok: false, code: "INVALID_VALUE", fields};
    }
    return sendIntake(parsed.data, await clientAddress());
}

/** A new stamp for a form that was open too long (or across a restart); null when the page must be reloaded. */
export async function renewIntakeFormTokenAction(): Promise<string | null> {
    return (await getIntakeForm())?.formToken ?? null;
}
