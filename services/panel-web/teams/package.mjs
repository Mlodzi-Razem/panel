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

// Builds the Microsoft Teams app package for MR Panel.
//
//   node teams/package.mjs                                  # production host
//   HOST=dev.panel.mlodzirazem.org node teams/package.mjs   # stage, separate dev app
//   HOST=my-tunnel-3000.euw.devtunnels.ms node teams/package.mjs
//   TEAMS_APP=prod HOST=dev.panel.mlodzirazem.org node teams/package.mjs  # production entry -> stage
//
// Output: teams/build/manifest.json, the two icons and teams/build/panel-mr-<host>[-<app>-app].zip

import {copyFileSync, mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {dirname, join, relative} from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import process from 'node:process';

const DEFAULT_HOST = 'panel.mlodzirazem.org';
// Teams app id of the production app. Every other host is a dev/stage copy that gets its own
// fixed id, so uploading it never replaces the production app in the org catalog.
const PROD_APP_ID = '6a34764f-e007-45ff-a767-943cafa9d6ce';
const DEV_APP_ID = '4f8bec34-7497-4d50-a534-2dbc73e1b447';
const MAX_SHORT_NAME = 30; // Teams limit for name.short
const ICONS = {'color.png': 192, 'outline.png': 32};

const teamsDir = dirname(fileURLToPath(import.meta.url));
const buildDir = join(teamsDir, 'build');

function fail(message) {
    console.error(`package.mjs: ${message}`);
    process.exit(1);
}

// Accept "host", "https://host" or "https://host/"; Teams needs a bare DNS name.
const host = (process.env.HOST || DEFAULT_HOST).trim().toLowerCase()
    .replace(/^https:\/\//, '')
    .replace(/\/+$/, '');
if (!/^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/.test(host)) {
    fail(`HOST must be a bare host name such as ${DEFAULT_HOST}, got "${process.env.HOST}"`);
}

const isProd = host === DEFAULT_HOST;
// TEAMS_APP=prod|dev picks which catalog entry the package updates; by default it follows HOST.
// Override it to repoint an already uploaded app at another host, e.g. the production entry at stage.
const defaultTeamsApp = isProd ? 'prod' : 'dev';
const teamsApp = (process.env.TEAMS_APP || defaultTeamsApp).trim().toLowerCase();
if (teamsApp !== 'prod' && teamsApp !== 'dev') {
    fail(`TEAMS_APP must be "prod" or "dev", got "${process.env.TEAMS_APP}"`);
}
const manifestText = readFileSync(join(teamsDir, 'manifest.template.json'), 'utf8')
    .replaceAll('{{HOST}}', host)
    .replaceAll('{{APP_ID}}', teamsApp === 'prod' ? PROD_APP_ID : DEV_APP_ID)
    .replaceAll('{{NAME_SUFFIX}}', isProd ? '' : ' (dev)');
if (manifestText.includes('{{')) {
    fail('manifest.template.json has a placeholder other than {{HOST}}, {{APP_ID}} and {{NAME_SUFFIX}}');
}
let manifest;
try {
    manifest = JSON.parse(manifestText);
} catch (e) {
    fail(`manifest is not valid JSON: ${e.message}`);
}
if (manifest.name.short.length > MAX_SHORT_NAME) {
    fail(`name.short "${manifest.name.short}" is ${manifest.name.short.length} characters, Teams allows ${MAX_SHORT_NAME}`);
}

// Teams rejects icons of any other size; read width and height from the PNG header.
for (const [icon, size] of Object.entries(ICONS)) {
    const png = readFileSync(join(teamsDir, icon));
    const width = png.readUInt32BE(16);
    const height = png.readUInt32BE(20);
    if (width !== size || height !== size) {
        fail(`${icon} is ${width}x${height}, Teams needs ${size}x${size}`);
    }
}

mkdirSync(buildDir, {recursive: true});
writeFileSync(join(buildDir, 'manifest.json'), manifestText);
for (const icon of Object.keys(ICONS)) {
    copyFileSync(join(teamsDir, icon), join(buildDir, icon));
}

// zip updates an existing archive in place, so start from scratch.
const zipName = teamsApp === defaultTeamsApp ? `panel-mr-${host}.zip` : `panel-mr-${host}-${teamsApp}-app.zip`;
const zipPath = join(buildDir, zipName);
rmSync(zipPath, {force: true});
execFileSync('zip', ['-q', '-X', '-j', zipPath, 'manifest.json', ...Object.keys(ICONS)], {
    cwd: buildDir,
    stdio: 'inherit',
});

console.log(relative(process.cwd(), zipPath) || zipPath);
