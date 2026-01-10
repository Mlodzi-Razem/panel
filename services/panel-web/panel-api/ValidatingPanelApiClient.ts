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

import IPanelApiClient, { HttpMethod, PanelApiRequest, PanelApiRequestConfig } from "@/panel-api/IPanelApiClient";
import * as z from 'zod';

export default class ValidatingPanelApiClient implements IPanelApiClient {
    constructor(
        private readonly _apiClient: IPanelApiClient,
        /** endpoint url -> method -> schemas */
        private readonly _schemas: Record<string, Record<HttpMethod, {
            requestSchema?: z.Schema,
            responseSchema?: z.Schema
        }>>,
    ) {
    }

    async callApi(req: PanelApiRequest, config?: PanelApiRequestConfig): Promise<Response> {
        const schemas = this._schemas[req.url]?.[req.method];
        const requestSchema = schemas?.requestSchema;
        const responseSchema = schemas?.responseSchema;

        if (requestSchema) {
            await requestSchema.parseAsync(req);
        }

        const result = await this._apiClient.callApi(req, config);

        if (responseSchema) {
            const json = await result.json();
            await responseSchema.parseAsync(json);
            return {
                ...result,
                json() {
                    return Promise.resolve(json);
                },
            }
        }

        return result;
    }
}