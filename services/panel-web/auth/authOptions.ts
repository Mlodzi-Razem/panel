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

import GoogleProvider from "next-auth/providers/google";
import { getServerSession, NextAuthOptions, Session } from "next-auth";
import env from "@/config/env";

const googleClientId = env.PANEL_GOOGLE_CLIENT_ID;
const googleClientSecret = env.PANEL_GOOGLE_CLIENT_SECRET;

if (!googleClientId || !googleClientSecret) {
    throw new Error("PANEL_GOOGLE_CLIENT_ID and PANEL_GOOGLE_CLIENT_SECRET environment variables must be set")
}


const authOptions = {
    providers: [
        GoogleProvider({
            clientId: googleClientId,
            clientSecret: googleClientSecret,
        }),
    ],
    pages: {
        signIn: '/login',
        signOut: '/'
    }
} satisfies NextAuthOptions;

export default authOptions;

export async function getPanelServerSession(): Promise<Session | null> {
    const s = await getServerSession(authOptions);
    return s ?? null;
}

export async function isAuthenticated(): Promise<boolean> {
    const session = await getPanelServerSession();
    return !!session;
}