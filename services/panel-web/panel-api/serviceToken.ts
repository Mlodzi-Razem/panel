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

import { createHmac, randomUUID } from "node:crypto";
import { User } from "next-auth";
import env from "@/config/env";

const ISSUER = "panel-web";
const AUDIENCE = "panel-api";
const TOKEN_TTL_SECONDS = 60;
const MIN_SECRET_BYTES = 32;

function secretKey(): Buffer {
    const key = Buffer.from(env.PANEL_API_AUTH_SECRET, "utf8");

    if (key.length < MIN_SECRET_BYTES) {
        throw new Error(`PANEL_API_AUTH_SECRET must be at least ${MIN_SECRET_BYTES} bytes long`);
    }

    return key;
}

function encode(value: object): string {
    return Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
}

export function mintServiceToken(user: User): string {
    if (!user.id) {
        throw new Error("Cannot call panel-api without an authenticated user id");
    }

    const now = Math.floor(Date.now() / 1000);
    const header = encode({ alg: "HS256", typ: "JWT" });
    const payload = encode({
        iss: ISSUER,
        aud: AUDIENCE,
        sub: user.id,
        ...(user.email ? { email: user.email } : {}),
        ...(user.name ? { name: user.name } : {}),
        iat: now,
        exp: now + TOKEN_TTL_SECONDS,
        jti: randomUUID(),
    });
    const signingInput = `${header}.${payload}`;
    const signature = createHmac("sha256", secretKey()).update(signingInput).digest("base64url");

    return `${signingInput}.${signature}`;
}
