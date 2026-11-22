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

import CredentialsProvider from "next-auth/providers/credentials";
import type { User } from "next-auth";
import { Provider } from "next-auth/providers/index";

/**
 * Provider ID of the local-development sign-in. It exists only when NODE_ENV is not production
 * AND the environment variable PANEL_DEV_LOGIN is exactly "true"; both gates are checked again
 * at every sign-in attempt (see authOptions). Local end-to-end testing then signs in with the
 * dev/seed persona's e-mail — the panel-api links the service token to the member by domain e-mail,
 * exactly like a first Google sign-in.
 */
export const DEV_LOGIN_PROVIDER = 'dev-login';

export function isDevLoginEnabled(): boolean {
    return process.env.NODE_ENV !== 'production'
        && process.env.PANEL_DEV_LOGIN === 'true';
}

/** A dev-only sign-in provider, or null when the gate is closed. Never active in production. */
export function devLoginProvider(): Provider | null {
    if (!isDevLoginEnabled()) {
        return null;
    }

    return CredentialsProvider({
        id: DEV_LOGIN_PROVIDER,
        name: 'Panel dev login',
        credentials: {email: {type: 'text'}},
        async authorize(credentials) {
            const email = (credentials?.email ?? '').trim().toLowerCase();
            if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
                return null;
            }
            const user: User = {id: `dev-${email}`, name: email, email};
            return user;
        },
    });
}
