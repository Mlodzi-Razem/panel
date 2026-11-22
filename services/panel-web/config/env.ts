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

const REQUIRED_ENV_VARS = {
    PANEL_APP_NAME: 'The display name of the application',
    PANEL_METRICS_AUTH_TOKEN: 'The authentication token for metrics collection',
    PANEL_GOOGLE_CLIENT_ID: 'The client ID of the Google OAuth2 application',
    PANEL_GOOGLE_CLIENT_SECRET: 'The client secret of the Google OAuth2 application',
    PANEL_GOOGLE_WORKSPACE_DOMAIN: 'The Google Workspace primary domain whose accounts are allowed to sign in (e.g. example.org)',
    PANEL_API_URL: 'The URL of the panel-api component',
    PANEL_API_AUTH_SECRET: 'The shared secret (at least 32 bytes) used to sign service tokens sent to panel-api',
    NEXTAUTH_SECRET: 'The secret used for NextAuth.js',
    NEXTAUTH_URL: 'The URL of the server',
} as const;
export type PanelEnvKeyName = keyof typeof REQUIRED_ENV_VARS;
export const REQUIRED_ENV_VARS_NAMES = Object.keys(REQUIRED_ENV_VARS) as ReadonlyArray<PanelEnvKeyName>;

const IS_NEXT_BUILD_PHASE = process.env.NEXT_PHASE === 'phase-production-build';
const BUILD_PHASE_PLACEHOLDER = '__panel_build_phase_placeholder__';

const PanelEnv: Record<PanelEnvKeyName, string> = REQUIRED_ENV_VARS_NAMES
    .map(envVarName => {
        const envValue = process.env[envVarName] || (IS_NEXT_BUILD_PHASE ? BUILD_PHASE_PLACEHOLDER : undefined);

        if (!envValue) {
            const purpose = REQUIRED_ENV_VARS[envVarName];
            throw new Error(`Required environment variable ${envVarName} is not set. Its purpose: ${purpose}`);
        }

        return [envVarName, envValue] as const;
    })
    .reduce(
        (acc, [key, value]) => ({
            ...acc,
            [key]: value,
        } as const),
        {},
    ) as Record<PanelEnvKeyName, string>;

export default PanelEnv;

const OPTIONAL_ENV_VARS = {
    PANEL_ENTRA_TENANT_ID: 'The Microsoft Entra tenant ID; enables Microsoft 365 sign-in and the Teams app',
    PANEL_ENTRA_CLIENT_ID: 'The client ID of the Panel MR app registration in Microsoft Entra',
    PANEL_ENTRA_CLIENT_SECRET: 'The client secret of the Panel MR app registration; needed only for sign-in in the browser',
} as const;
export type PanelOptionalEnvKeyName = keyof typeof OPTIONAL_ENV_VARS;

function optional(name: PanelOptionalEnvKeyName): string | undefined {
    const value = process.env[name]?.trim();
    return value ? value : undefined;
}

export type EntraConfig = {
    tenantId: string;
    clientId: string;
    /** Present only when browser sign-in with Microsoft 365 is enabled. */
    clientSecret?: string;
};

/**
 * Microsoft Entra settings, or undefined when Entra is not configured. Teams SSO needs the tenant and client IDs;
 * browser sign-in additionally needs the client secret.
 */
export function entraConfig(): EntraConfig | undefined {
    const tenantId = optional('PANEL_ENTRA_TENANT_ID');
    const clientId = optional('PANEL_ENTRA_CLIENT_ID');

    if (!tenantId || !clientId) {
        return undefined;
    }

    return {tenantId, clientId, clientSecret: optional('PANEL_ENTRA_CLIENT_SECRET')};
}

const RECRUITMENT_DECLARATION_MOCK_ENV_VAR = 'PANEL_RECRUITMENT_DECLARATION_MOCK';

/**
 * When "1", the anonymous candidate declaration form at /rekrutacja/deklaracja/[token]
 * answers from the in-panel mock adapter (panel-api/recruitment/mockDeclarationFormApi)
 * instead of the real backend. Development and demo switch only; production leaves it unset
 * and routes the contract calls to PANEL_API_URL. See the README next to the page.
 */
export function recruitmentDeclarationMockEnabled(): boolean {
    return process.env[RECRUITMENT_DECLARATION_MOCK_ENV_VAR]?.trim() === '1';
}

