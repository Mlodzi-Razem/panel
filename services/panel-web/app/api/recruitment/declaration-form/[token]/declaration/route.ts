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

import {uploadSignedDeclaration} from "@/panel-api/recruitment/declarationForm";

/** Signed declarations are small vector PDFs; the 10 MiB ceiling leaves a margin over
 *  the ~5 MiB output of the mObywatel/eDO signing flows. */
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

function jsonError(status: number, code: string): Response {
    return Response.json({code}, {status, headers: {"Cache-Control": "no-store"}});
}

/**
 * Public BFF proxy for the signed-declaration upload (a route handler, not a server action:
 *  server actions cap bodies at 1 MiB). Validates the obvious client mistakes itself, then
 *  forwards to the contract API whose signature verification decides. The token is never logged.
 */
export async function POST(request: Request, context: {params: Promise<{token: string}>}): Promise<Response> {
    const {token} = await context.params;

    let formData: FormData;
    try {
        formData = await request.formData();
    } catch {
        return jsonError(400, "BAD_REQUEST");
    }
    const file = formData.get("file");
    if (!(file instanceof File) || file.size === 0) {
        return jsonError(422, "FILE_MISSING");
    }
    const looksPdf = file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
    if (!looksPdf) {
        return jsonError(422, "INVALID_FILE_TYPE");
    }
    if (file.size > MAX_UPLOAD_BYTES) {
        return jsonError(422, "FILE_TOO_LARGE");
    }

    const result = await uploadSignedDeclaration(token, file);
    switch (result.kind) {
        case "signed":
            // The wire carries no timestamps (contract: `{status:"SIGNED"}`) — the final
            // screen is content-free by design (anti-enumeration, review fix-pass).
            return Response.json(
                {status: "SIGNED"},
                {status: 200, headers: {"Cache-Control": "no-store"}},
            );
        case "invalidSignature":
            return jsonError(422, "SIGNATURE_INVALID");
        case "unavailable":
            return jsonError(404, "LINK_UNAVAILABLE");
        case "expired":
            return jsonError(422, "TOKEN_EXPIRED");
        case "closed":
            return jsonError(409, "CASE_CLOSED");
        case "error":
            return jsonError(502, "API_UNREACHABLE");
    }
}
