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
import { UNAUTHENTICATED } from "@/panel-api/members/client";
import { ActionResult, ApiError } from "@/panel-api/members/types";

export function fail(error: ApiError): ActionResult<never> {
    return {ok: false, error};
}

export function invalid(...fields: string[]): ActionResult<never> {
    return fail({status: 400, code: 'INVALID_VALUE', message: 'Invalid value', fields});
}

export async function denyUnlessSignedIn(): Promise<ActionResult<never> | null> {
    const session = await getPanelServerSession();
    if (!session?.user?.id) {
        return fail(UNAUTHENTICATED);
    }
    return null;
}

export const idSchema = z.number().int().positive();
export const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export function text(formData: FormData, name: string): string {
    const value = formData.get(name);
    return typeof value === 'string' ? value.trim() : '';
}

export function optionalText(formData: FormData, name: string): string | undefined {
    const value = text(formData, name);
    return value.length > 0 ? value : undefined;
}

export function parseId(value: FormDataEntryValue | null | undefined): number | null {
    if (typeof value !== 'string' || !/^\d+$/.test(value)) {
        return null;
    }
    const parsed = Number(value);
    return parsed > 0 ? parsed : null;
}
