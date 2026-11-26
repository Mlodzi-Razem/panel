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

import type { NextConfig } from "next";
import createNextIntlPlugin from 'next-intl/plugin';

/**
 * Hosts allowed to frame the Teams tabs: Teams, Outlook and the Microsoft 365 app.
 * Everything else may be framed only by Panel itself.
 */
const MICROSOFT_365_FRAME_ANCESTORS = [
    "'self'",
    'https://teams.microsoft.com',
    'https://*.teams.microsoft.com',
    'https://*.cloud.microsoft',
    'https://*.office.com',
    'https://outlook.office.com',
    'https://outlook.office365.com',
    'https://*.microsoft365.com',
].join(' ');

const nextConfig: NextConfig = {
    reactCompiler: true,
    async headers() {
        return [
            {
                source: '/teams/:path*',
                headers: [{key: 'Content-Security-Policy', value: `frame-ancestors ${MICROSOFT_365_FRAME_ANCESTORS}`}],
            },
            {
                source: '/((?!teams/).*)',
                headers: [{key: 'Content-Security-Policy', value: "frame-ancestors 'self'"}],
            },
        ];
    },
    output: 'standalone',
    transpilePackages: ['next-intl'],
    experimental: {
        optimizePackageImports: ["@phosphor-icons/react"],
        // bank statements are uploaded through a server action; the API accepts files of up to 10 MB
        serverActions: {bodySizeLimit: '12mb'},
    }
};

const withNextIntl = createNextIntlPlugin();
export default withNextIntl(nextConfig);
