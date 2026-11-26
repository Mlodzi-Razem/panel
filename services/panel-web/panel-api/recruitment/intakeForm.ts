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

/**
 * Client of the PUBLIC "I want to join" form (route /dolacz). The endpoints are anonymous, so no service token is
 * minted. Submitted values are never logged and never put in a URL.
 */
import * as z from "zod";
import env from "@/config/env";

const PATH = "/public/recruitment-intake";
const CLIENT_ADDRESS_HEADER = "X-Panel-Client-Address";

/**
 * Version of the consent and privacy notice in the messages of this application (key `intakeForm`). It is sent
 * with a submission as the record of what the person agreed to, and must equal the version panel-api expects.
 * Change it together with the text.
 */
export const INTAKE_CONSENT_VERSION = "intake-2026-11-draft-1";

const formSchema = z.object({
    enabled: z.boolean(),
    formToken: z.string(),
    consentVersion: z.string(),
    districts: z.array(z.object({id: z.number(), name: z.string()})),
});
export type IntakeFormData = z.infer<typeof formSchema>;

export const intakeValuesSchema = z.object({
    firstName: z.string().max(200),
    lastName: z.string().max(200),
    email: z.string().max(300),
    phone: z.string().max(100),
    birthDate: z.string().max(20),
    districtBodyId: z.number().int().positive().nullable(),
    locality: z.string().max(200),
    consent: z.boolean(),
    formToken: z.string().max(200),
    website: z.string().max(200),
});
export type IntakeValues = z.infer<typeof intakeValuesSchema>;

export type IntakeSendResult =
    | {ok: true}
    | {ok: false; code: string; fields: string[]};

function url(): string {
    return env.PANEL_API_URL.replace(/\/+$/, "") + PATH;
}

/** The open form, or null when it is switched off, outdated against the texts of this build, or unreachable. */
export async function getIntakeForm(): Promise<IntakeFormData | null> {
    try {
        const response = await fetch(url(), {headers: {Accept: "application/json"}, cache: "no-store"});
        if (!response.ok) {
            return null;
        }
        const parsed = formSchema.safeParse(await response.json());
        return parsed.success && parsed.data.enabled && parsed.data.consentVersion === INTAKE_CONSENT_VERSION
            ? parsed.data
            : null;
    } catch {
        return null;
    }
}

const errorSchema = z.object({code: z.string(), fields: z.array(z.string()).nullish()});

export async function sendIntake(values: IntakeValues, clientAddress: string | undefined): Promise<IntakeSendResult> {
    const headers: Record<string, string> = {Accept: "application/json", "Content-Type": "application/json"};
    if (clientAddress) {
        headers[CLIENT_ADDRESS_HEADER] = clientAddress;
    }

    try {
        const response = await fetch(url(), {
            method: "POST",
            headers,
            cache: "no-store",
            body: JSON.stringify({...values, consentVersion: INTAKE_CONSENT_VERSION}),
        });
        if (response.ok) {
            return {ok: true};
        }
        const parsed = errorSchema.safeParse(await response.json().catch(() => null));
        return parsed.success
            ? {ok: false, code: parsed.data.code, fields: parsed.data.fields ?? []}
            : {ok: false, code: "UNAVAILABLE", fields: []};
    } catch {
        return {ok: false, code: "UNAVAILABLE", fields: []};
    }
}
