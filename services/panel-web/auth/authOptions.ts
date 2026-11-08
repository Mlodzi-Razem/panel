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

import GoogleProvider, { GoogleProfile } from "next-auth/providers/google";
import { getServerSession, NextAuthOptions, Session } from "next-auth";
import env from "@/config/env";

const googleClientId = env.PANEL_GOOGLE_CLIENT_ID;
const googleClientSecret = env.PANEL_GOOGLE_CLIENT_SECRET;
const workspaceDomain = env.PANEL_GOOGLE_WORKSPACE_DOMAIN.trim().toLowerCase();

if (!googleClientId || !googleClientSecret || !workspaceDomain) {
    throw new Error("PANEL_GOOGLE_CLIENT_ID, PANEL_GOOGLE_CLIENT_SECRET and PANEL_GOOGLE_WORKSPACE_DOMAIN environment variables must be set")
}

export function isWorkspaceAccount(profile: Partial<GoogleProfile> | undefined): boolean {
    return !!profile
        && profile.email_verified === true
        && typeof profile.hd === 'string'
        && profile.hd.toLowerCase() === workspaceDomain;
}

const authOptions = {
    providers: [
        GoogleProvider({
            clientId: googleClientId,
            clientSecret: googleClientSecret,
            authorization: {
                params: {
                    hd: workspaceDomain,
                    prompt: 'select_account',
                },
            },
        }),
    ],
    session: {
        strategy: 'jwt',
        maxAge: 12 * 60 * 60,
        updateAge: 60 * 60,
    },
    callbacks: {
        async signIn({account, profile}) {
            return account?.provider === 'google'
                && isWorkspaceAccount(profile as Partial<GoogleProfile> | undefined);
        },
        jwt({ token, user, profile }) {
            if (user) {
                token.sub = profile?.sub ?? user.id ?? token.sub;
                token.email = user.email ?? token.email;
                token.name = user.name ?? token.name;
            }
            return token;
        },
        session({ session, token }) {
            if (session.user && token.sub) {
                session.user.id = token.sub;
            }
            return session;
        },
    },
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