/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
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

import IPanelApiClient, {
    BodyHttpMethod,
    BodyHttpMethods,
    PanelApiRequest,
    PanelApiRequestConfig,
} from "@/panel-api/IPanelApiClient";
import env from "@/config/env";

function requestInit(req: PanelApiRequest, config: PanelApiRequestConfig) {
    return {
        method: req.method,
        headers: headers(req, config),
        body: body(req),
        signal: config.signal,
        cache: config.cache,
        opentelemetry: {
            propagateContext: true,
        },
    } satisfies RequestInit;
}

export default class FetchPanelApiClient implements IPanelApiClient {
    callApi(req: PanelApiRequest, config: PanelApiRequestConfig): Promise<Response> {
        return fetch(
            url(req.url),
            requestInit(req, config),
        );
    }
}

function url(endpointUrl: string) {
    const baseUrl = env.PANEL_API_URL;

    if (!baseUrl.endsWith('/') && !endpointUrl.startsWith('/')) {
        return baseUrl + '/' + endpointUrl;
    }

    return baseUrl + endpointUrl;
}

function headers(req: PanelApiRequest, config: PanelApiRequestConfig) {
    return {
        ...req.headers,
        ['X-Auth-User-Id']: config.user.id,
        ...(config.user.email ? {['X-Auth-User-Email']: config.user.email} : {}),
        ...(config.user.name ? {['X-Auth-User-Name']: config.user.name} : {}),
    } satisfies HeadersInit;
}

function body(req: PanelApiRequest): string | Blob | undefined {
    if (!BodyHttpMethods.includes(req.method as unknown as BodyHttpMethod)) {
        if ('body' in req) {
            throw new Error(`Method ${req.method} does not support body`);
        }

        return undefined;
    }

    if (!('body' in req)) {
        return undefined;
    }

    if (req.body instanceof Blob || typeof req.body === 'string') {
        return req.body;
    }

    return JSON.stringify(req.body);
}