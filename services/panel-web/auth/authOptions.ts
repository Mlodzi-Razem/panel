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
import AzureADProvider from "next-auth/providers/azure-ad";
import CredentialsProvider from "next-auth/providers/credentials";
import { CookiesOptions, getServerSession, NextAuthOptions, Session } from "next-auth";
import { Provider } from "next-auth/providers/index";
import env, { entraConfig } from "@/config/env";
import { verifyTeamsSsoToken } from "@/auth/teamsSso";
import { ENTRA_PROVIDER, GOOGLE_PROVIDER, TEAMS_SSO_PROVIDER } from "@/auth/providers";
import { DEV_LOGIN_PROVIDER, devLoginProvider } from "@/auth/devLogin";

const googleClientId = env.PANEL_GOOGLE_CLIENT_ID;
const googleClientSecret = env.PANEL_GOOGLE_CLIENT_SECRET;
const workspaceDomain = env.PANEL_GOOGLE_WORKSPACE_DOMAIN.trim().toLowerCase();
const entra = entraConfig();

if (!googleClientId || !googleClientSecret || !workspaceDomain) {
    throw new Error("PANEL_GOOGLE_CLIENT_ID, PANEL_GOOGLE_CLIENT_SECRET and PANEL_GOOGLE_WORKSPACE_DOMAIN environment variables must be set")
}

export function isWorkspaceAccount(profile: Partial<GoogleProfile> | undefined): boolean {
    return !!profile
        && profile.email_verified === true
        && typeof profile.hd === 'string'
        && profile.hd.toLowerCase() === workspaceDomain;
}

export function isEntraSignInEnabled(): boolean {
    return !!entra?.clientSecret;
}

function providers(): Provider[] {
    const list: Provider[] = [
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
    ];

    if (entra?.clientSecret) {
        list.push(AzureADProvider({
            clientId: entra.clientId,
            clientSecret: entra.clientSecret,
            tenantId: entra.tenantId,
            authorization: {params: {scope: 'openid profile email', prompt: 'select_account'}},
        }));
    }

    if (entra) {
        list.push(CredentialsProvider({
            id: TEAMS_SSO_PROVIDER,
            name: 'Microsoft Teams',
            credentials: {token: {type: 'text'}},
            async authorize(credentials) {
                const token = credentials?.token;
                if (!token) {
                    return null;
                }
                const user = await verifyTeamsSsoToken(token, entra);
                return user ? {id: user.oid, name: user.name, email: user.username} : null;
            },
        }));
    }

    const devLogin = devLoginProvider();
    if (devLogin) {
        list.push(devLogin);
    }

    return list;
}

/**
 * Inside the Teams frame panel-web is a third-party site, so the cookies that carry the sign-in must be
 * `SameSite=None` and partitioned (CHIPS) by the top-level site. Plain HTTP (local development) keeps the defaults.
 */
function cookies(): Partial<CookiesOptions> | undefined {
    if (!(process.env.NEXTAUTH_URL ?? '').startsWith('https://')) {
        return undefined;
    }

    const options = {httpOnly: true, sameSite: 'none' as const, path: '/', secure: true, partitioned: true};
    return {
        sessionToken: {name: '__Secure-next-auth.session-token', options},
        callbackUrl: {name: '__Secure-next-auth.callback-url', options},
        csrfToken: {name: '__Host-next-auth.csrf-token', options},
    };
}

const authOptions = {
    providers: providers(),
    cookies: cookies(),
    session: {
        strategy: 'jwt',
        maxAge: 12 * 60 * 60,
        updateAge: 60 * 60,
    },
    callbacks: {
        async signIn({account, profile}) {
            switch (account?.provider) {
                case GOOGLE_PROVIDER:
                    return isWorkspaceAccount(profile as Partial<GoogleProfile> | undefined);
                case ENTRA_PROVIDER:
                    return !!entra && (profile as { tid?: string } | undefined)?.tid === entra.tenantId;
                case TEAMS_SSO_PROVIDER:
                    return !!entra;
                case DEV_LOGIN_PROVIDER:
                    // double gate: even if the provider somehow registers in a production image,
                    // a sign-in is refused unless the dev gate is open right now
                    return process.env.NODE_ENV !== 'production'
                        && process.env.PANEL_DEV_LOGIN === 'true';
                default:
                    return false;
            }
        },
        jwt({ token, user, account, profile }) {
            if (user) {
                if (account?.provider === ENTRA_PROVIDER) {
                    const oid = (profile as { oid?: string } | undefined)?.oid;
                    if (!oid) {
                        throw new Error('Microsoft 365 sign-in returned no object ID');
                    }
                    token.sub = oid;
                    token.idp = 'entra';
                } else if (account?.provider === TEAMS_SSO_PROVIDER) {
                    token.sub = user.id;
                    token.idp = 'entra';
                } else {
                    token.sub = profile?.sub ?? user.id ?? token.sub;
                    token.idp = 'google';
                }
                token.email = user.email ?? token.email;
                token.name = user.name ?? token.name;
            }
            return token;
        },
        session({ session, token }) {
            if (session.user && token.sub) {
                session.user.id = token.sub;
                session.user.idp = token.idp ?? 'google';
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
