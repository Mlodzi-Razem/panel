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
    PANEL_ENTRA_CLIENT_SECRET: 'The client secret of the Panel MR app registration; one way to enable sign-in in the browser',
    PANEL_ENTRA_CLIENT_CERTIFICATE: 'The PEM certificate of the Panel MR app registration; with its key, enables sign-in in the browser without a client secret',
    PANEL_ENTRA_CLIENT_CERTIFICATE_KEY: 'The PKCS#8 PEM private key of PANEL_ENTRA_CLIENT_CERTIFICATE',
} as const;
export type PanelOptionalEnvKeyName = keyof typeof OPTIONAL_ENV_VARS;

function optional(name: PanelOptionalEnvKeyName): string | undefined {
    const value = process.env[name]?.trim();
    return value ? value : undefined;
}

export type EntraConfig = {
    tenantId: string;
    clientId: string;
    /** Browser sign-in credential (client secret); used when no certificate is configured. */
    clientSecret?: string;
    /**
     * Browser sign-in credential (certificate + PKCS#8 key, PEM); signs an OAuth 2.0 client assertion and takes
     * precedence over the client secret, as in panel-api.
     */
    clientCertificate?: { certificate: string; key: string };
};

/** PEM from an env value: Kubernetes passes real newlines, .env files often carry literal "\n". */
function pem(name: PanelOptionalEnvKeyName): string | undefined {
    return optional(name)?.replace(/\\n/g, '\n');
}

/**
 * Microsoft Entra settings, or undefined when Entra is not configured. Teams SSO needs the tenant and client IDs;
 * browser sign-in additionally needs a credential: a certificate with its key, or a client secret.
 */
export function entraConfig(): EntraConfig | undefined {
    const tenantId = optional('PANEL_ENTRA_TENANT_ID');
    const clientId = optional('PANEL_ENTRA_CLIENT_ID');

    if (!tenantId || !clientId) {
        return undefined;
    }

    const certificate = pem('PANEL_ENTRA_CLIENT_CERTIFICATE');
    const key = pem('PANEL_ENTRA_CLIENT_CERTIFICATE_KEY');

    return {
        tenantId,
        clientId,
        clientSecret: optional('PANEL_ENTRA_CLIENT_SECRET'),
        clientCertificate: certificate && key ? {certificate, key} : undefined,
    };
}

const TRUSTED_PROXY_HOPS_ENV_VAR = 'PANEL_TRUSTED_PROXY_HOPS';

/**
 * How many trusted proxies in front of panel-web append to X-Forwarded-For. 0 (the default, and whenever the
 * chain does not preserve the client address, e.g. behind a plain TCP forwarder) disables the per-client limit
 * of the public join form; the global limits of panel-api still apply.
 */
export function trustedProxyHops(): number {
    const hops = Number(process.env[TRUSTED_PROXY_HOPS_ENV_VAR]?.trim() || '0');
    return Number.isInteger(hops) && hops > 0 ? hops : 0;
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

