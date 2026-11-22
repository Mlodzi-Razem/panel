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

import {streamDeclarationFormPdf} from "@/panel-api/recruitment/declarationForm";

/**
 * Public BFF proxy: streams the declaration PDF out of the contract API (or the mock)
 * so the browser never needs the panel-api base URL. The token stays in the path and
 * is never logged. Cached never — the PDF contains personal data.
 */
export async function GET(request: Request, context: {params: Promise<{token: string}>}): Promise<Response> {
    const {token} = await context.params;
    const upstream = await streamDeclarationFormPdf(token);
    if (!upstream.ok || !upstream.body) {
        return new Response(null, {
            status: upstream.status >= 400 && upstream.status < 600 ? upstream.status : 502,
            headers: {"Cache-Control": "no-store"},
        });
    }
    return new Response(upstream.body, {
        status: 200,
        headers: {
            "Content-Type": "application/pdf",
            "Content-Disposition": 'inline; filename="deklaracja-czlonkowska.pdf"',
            "Cache-Control": "no-store",
        },
    });
}
