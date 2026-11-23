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

import { revalidatePath } from "next/cache";
import { uploadDeclarationScan } from "@/panel-api/members/recruitment";

/** The API caps uploads itself; this ceiling only spares a pointless hop for an obviously huge file. */
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

/** The data parts the API accepts next to the file — anything else is dropped here. */
const DATA_PARTS = [
    "formalFirstName", "formalLastName", "preferredFirstName", "preferredLastName", "pronouns",
    "personalId", "phone", "email", "imageConsent", "specialCategoryConsent",
] as const;

function jsonError(status: number, code: string, message = code): Response {
    return Response.json({error: {status, code, message}}, {status, headers: {"Cache-Control": "no-store"}});
}

/**
 * CSRF guard. The session cookie is SameSite=None (it must survive inside the Teams frame), so a
 * cross-site multipart form would carry it. Server actions compare Origin with the host on their
 * own; route handlers do not, so this route compares them the same way.
 */
function isSameOrigin(request: Request): boolean {
    const origin = request.headers.get("origin");
    const host = (request.headers.get("x-forwarded-host") ?? request.headers.get("host"))?.split(",")[0].trim();
    if (!origin || !host) {
        return false;
    }
    try {
        return new URL(origin).host === host;
    } catch {
        return false;
    }
}

/**
 * Authenticated BFF route for the paper-declaration scan (a route handler, not a server action:
 * server actions cap bodies at 1 MiB). The session is resolved and the service token minted by
 * the panel-api client; RECRUITMENT_MANAGE for the case and every data rule are enforced by the
 * API, so this route only forwards. Values are never logged.
 */
export async function POST(request: Request, context: { params: Promise<{ id: string }> }): Promise<Response> {
    if (!isSameOrigin(request)) {
        return jsonError(403, "FORBIDDEN");
    }

    const {id} = await context.params;
    const caseId = Number.parseInt(id, 10);
    if (!Number.isSafeInteger(caseId) || caseId <= 0) {
        return jsonError(404, "NOT_FOUND");
    }

    let incoming: FormData;
    try {
        incoming = await request.formData();
    } catch {
        return jsonError(400, "BAD_REQUEST");
    }

    const file = incoming.get("file");
    if (!(file instanceof File) || file.size === 0) {
        return jsonError(422, "FILE_MISSING");
    }
    if (file.size > MAX_UPLOAD_BYTES) {
        return jsonError(422, "FILE_TOO_LARGE");
    }

    const outgoing = new FormData();
    outgoing.set("file", file, file.name);
    for (const part of DATA_PARTS) {
        const value = incoming.get(part);
        if (typeof value === "string" && value.length > 0) {
            outgoing.set(part, value);
        }
    }

    const result = await uploadDeclarationScan(caseId, outgoing);
    if (!result.ok) {
        return Response.json({error: result.error}, {
            status: result.error.status >= 400 && result.error.status < 600 ? result.error.status : 502,
            headers: {"Cache-Control": "no-store"},
        });
    }

    revalidatePath("/rekrutacja");
    revalidatePath(`/rekrutacja/${caseId}`);
    return Response.json({ok: true}, {headers: {"Cache-Control": "no-store"}});
}
