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

import { NextResponse } from "next/dist/server/web/spec-extension/response";
import { collectDefaultMetrics, register } from "prom-client";
import env from "@/config/env";
import { NextRequest } from "next/dist/server/web/spec-extension/request";

declare const globalThis: {
    metricsRegistered?: boolean;
}

function panelWebPrefixFromEnv() {
    return (env.PANEL_APP_NAME + '_')
        .replace(' ', '')
        .replace('-', '_');
}

if (!globalThis.metricsRegistered) {
    const prefix = env.PANEL_APP_NAME ? panelWebPrefixFromEnv() : 'panel_web_';
    collectDefaultMetrics({prefix});
    globalThis.metricsRegistered = true;
}

const UNAUTHORIZED_RES = new NextResponse(JSON.stringify({
    status: 'error',
    reason: 'unauthorized',
}), {status: 401});

export async function GET(req: NextRequest) {
    const authHeader = req.headers.get('Authorization');
    const expectedAuthToken = env.PANEL_METRICS_AUTH_TOKEN;
    if (!expectedAuthToken) {
        console.error("PANEL_METRICS_AUTH_TOKEN is not set, metrics endpoint is not be available.")
        return UNAUTHORIZED_RES;
    }

    const expectedHeader = `Bearer ${expectedAuthToken}`;

    if (authHeader !== expectedHeader) {
        return UNAUTHORIZED_RES;
    }

    const metricsResponseBody = await register.metrics();
    return new NextResponse(
        metricsResponseBody,
        {
            headers: {
                'Content-Type': register.contentType,
            },
            status: 200,
        },
    );
}