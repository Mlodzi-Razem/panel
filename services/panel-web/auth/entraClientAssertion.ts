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

import { createHash, createPrivateKey, randomUUID, X509Certificate } from "node:crypto";
import { SignJWT } from "jose";

export const CLIENT_ASSERTION_TYPE = 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer';

/**
 * A one-time OAuth 2.0 client assertion (RFC 7523) for the tenant's v2.0 token endpoint, signed with the app
 * registration's certificate: RS256 for RSA keys, ES256 for P-256 keys. The header carries {@code x5t}, the
 * base64url SHA-1 thumbprint Entra matches against the uploaded certificate (the same form panel-api uses).
 */
export async function entraClientAssertion(
    tenantId: string,
    clientId: string,
    credential: { certificate: string; key: string },
): Promise<string> {
    const der = new X509Certificate(credential.certificate).raw;
    const x5t = createHash('sha1').update(der).digest('base64url');
    const key = createPrivateKey(credential.key);
    const alg = key.asymmetricKeyType === 'ec' ? 'ES256' : 'RS256';

    return new SignJWT({})
        .setProtectedHeader({alg, typ: 'JWT', x5t})
        .setIssuer(clientId)
        .setSubject(clientId)
        .setAudience(`https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/token`)
        .setJti(randomUUID())
        .setIssuedAt()
        .setNotBefore('0s')
        .setExpirationTime('5m')
        .sign(key);
}
