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

import * as z from "zod";
import { getPanelServerSession } from "@/auth/authOptions";
import FetchPanelApiClient from "@/panel-api/FetchPanelApiClient";
import type { PanelApiRequest } from "@/panel-api/IPanelApiClient";
import { ApiError, ApiResult, assignmentCheckSchema } from "@/panel-api/members/types";

const client = new FetchPanelApiClient();

const errorBodySchema = z.object({
    code: z.string(),
    message: z.string().nullish(),
    fields: z.array(z.string()).nullish(),
    check: z.unknown().nullish(),
});

export type ApiRequest = {
    method: 'GET' | 'POST' | 'PATCH' | 'DELETE';
    path: string;
    query?: Record<string, string | number | undefined | null>;
    body?: object;
};

export function failure<T = never>(error: ApiError): ApiResult<T> {
    return {ok: false, error};
}

export const UNAUTHENTICATED: ApiError = {
    status: 401,
    code: 'UNAUTHENTICATED',
    message: 'Not signed in',
};

function buildPath(path: string, query: ApiRequest['query']): string {
    if (!query) {
        return path;
    }
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
        if (value !== undefined && value !== null && String(value).length > 0) {
            params.set(key, String(value));
        }
    }
    const qs = params.toString();
    return qs ? `${path}?${qs}` : path;
}

async function parseError(response: Response): Promise<ApiError> {
    let json: unknown = null;
    try {
        json = await response.json();
    } catch {
        json = null;
    }
    const parsed = errorBodySchema.safeParse(json);
    if (!parsed.success) {
        return {
            status: response.status,
            code: response.status === 401 ? 'UNAUTHENTICATED' : `HTTP_${response.status}`,
            message: response.statusText || 'Request failed',
        };
    }
    const check = assignmentCheckSchema.safeParse(parsed.data.check);
    return {
        status: response.status,
        code: parsed.data.code,
        message: parsed.data.message ?? parsed.data.code,
        ...(parsed.data.fields ? {fields: parsed.data.fields} : {}),
        ...(check.success && parsed.data.check ? {check: check.data} : {}),
    };
}

export async function apiCall<T>(request: ApiRequest, schema: z.ZodType<T>): Promise<ApiResult<T>> {
    const session = await getPanelServerSession();
    if (!session?.user?.id) {
        return failure(UNAUTHENTICATED);
    }

    const url = buildPath(request.path, request.query);
    const headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
    };
    const req: PanelApiRequest = request.method === 'GET'
        ? {url, method: 'GET', headers}
        : {url, method: request.method, headers, ...(request.body ? {body: request.body} : {})};

    let response: Response;
    try {
        response = await client.callApi(req, {user: session.user, cache: 'no-store'});
    } catch {
        return failure({status: 503, code: 'UNAVAILABLE', message: 'The panel API is unavailable'});
    }

    if (!response.ok) {
        return failure(await parseError(response));
    }

    let json: unknown = null;
    try {
        const text = await response.text();
        json = text.length > 0 ? JSON.parse(text) : null;
    } catch {
        return failure({status: 502, code: 'INVALID_RESPONSE', message: 'Unexpected response from the panel API'});
    }

    const parsed = schema.safeParse(json);
    if (!parsed.success) {
        console.error(`panel-api ${request.method} ${request.path}: unexpected response shape at ${
            parsed.error.issues.map(i => i.path.join('.')).join(', ')}`);
        return failure({status: 502, code: 'INVALID_RESPONSE', message: 'Unexpected response from the panel API'});
    }
    return {ok: true, data: parsed.data};
}
