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

import { createRemoteJWKSet, jwtVerify } from "jose";
import { EntraConfig } from "@/config/env";

/** The delegated scope Teams requests for Panel; see the "Expose an API" page of the app registration. */
export const TEAMS_SSO_SCOPE = "access_as_user";

export type TeamsSsoUser = {
    /** The Entra object ID: the immutable user key. */
    oid: string;
    name?: string;
    /** The UPN, shown only; never used to link accounts. */
    username?: string;
};

const keySets = new Map<string, ReturnType<typeof createRemoteJWKSet>>();

function keySet(tenantId: string) {
    let keys = keySets.get(tenantId);
    if (!keys) {
        keys = createRemoteJWKSet(new URL(`https://login.microsoftonline.com/${tenantId}/discovery/v2.0/keys`));
        keySets.set(tenantId, keys);
    }
    return keys;
}

/**
 * Verifies the token that TeamsJS `authentication.getAuthToken()` returns. It must be a v2 access token
 * for this app (audience = client ID) from this tenant, carrying the `access_as_user` scope.
 * The app registration needs `api.requestedAccessTokenVersion = 2`.
 */
export async function verifyTeamsSsoToken(token: string, config: EntraConfig): Promise<TeamsSsoUser | null> {
    try {
        const {payload} = await jwtVerify(token, keySet(config.tenantId), {
            issuer: `https://login.microsoftonline.com/${config.tenantId}/v2.0`,
            audience: config.clientId,
            algorithms: ['RS256'],
            clockTolerance: 60,
        });

        const scopes = typeof payload.scp === 'string' ? payload.scp.split(' ') : [];
        if (payload.tid !== config.tenantId || !scopes.includes(TEAMS_SSO_SCOPE)) {
            return null;
        }
        if (typeof payload.oid !== 'string' || payload.oid.length === 0) {
            return null;
        }

        return {
            oid: payload.oid,
            name: typeof payload.name === 'string' ? payload.name : undefined,
            username: typeof payload.preferred_username === 'string' ? payload.preferred_username : undefined,
        };
    } catch {
        return null;
    }
}
