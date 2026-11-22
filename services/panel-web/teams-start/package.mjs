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

// Builds the Microsoft Teams app package for "Start" — the single-tab wrapper
// around the MR SharePoint home site.
//
//   node teams-start/package.mjs
//
// Unlike teams/package.mjs there are no placeholders and no HOST variable:
// the tab URL is fixed (https://mloderazemki.sharepoint.com/).
// Output: teams-start/build/manifest.json, the two icons and teams-start/build/mr-start.zip

import {copyFileSync, mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {dirname, join, relative} from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import process from 'node:process';

const ICONS = {'color.png': 192, 'outline.png': 32};
const ZIP_NAME = 'mr-start.zip';
const HOST = 'mloderazemki.sharepoint.com'; // the MR SharePoint home-site host
const SPO_PRINCIPAL_ID = '00000003-0000-0ff1-ce00-000000000000'; // Office SharePoint Online, first-party
const LOGON_URL = `https://${HOST}/_layouts/15/teamslogon.aspx?SPFX=true&dest=`;

const startDir = dirname(fileURLToPath(import.meta.url));
const buildDir = join(startDir, 'build');

function fail(message) {
    console.error(`package.mjs: ${message}`);
    process.exit(1);
}

const manifestText = readFileSync(join(startDir, 'manifest.template.json'), 'utf8');
if (manifestText.includes('{{')) {
    fail('manifest.template.json contains a {{...}} placeholder — Start has a fixed URL and expects none');
}
let manifest;
try {
    manifest = JSON.parse(manifestText);
} catch (e) {
    fail(`manifest is not valid JSON: ${e.message}`);
}

// Validate the fixed wrapper shape: one personal tab that boots the SharePoint
// session through teamslogon.aspx with SSO (see README "Why teamslogon"),
// and nothing outside the SharePoint host.
if (manifest.staticTabs?.length !== 1) {
    fail('manifest must have exactly one staticTab');
}
const tab = manifest.staticTabs[0];
if (!tab.contentUrl?.startsWith(LOGON_URL) || tab.contentUrl.length === LOGON_URL.length) {
    fail(`the tab contentUrl must be ${LOGON_URL}<encoded server-relative page>`);
}
if (tab.websiteUrl !== `https://${HOST}/`) {
    fail(`the tab websiteUrl must be https://${HOST}/ so "open in browser" skips the logon shim`);
}
if (JSON.stringify(manifest.validDomains) !== JSON.stringify([HOST])) {
    fail(`validDomains must be ["${HOST}"]`);
}
const webApp = manifest.webApplicationInfo;
if (webApp?.id !== SPO_PRINCIPAL_ID || webApp?.resource !== `https://${HOST}`) {
    fail(`webApplicationInfo must declare the SharePoint Online principal: ` +
        `{ "id": "${SPO_PRINCIPAL_ID}", "resource": "https://${HOST}" }`);
}
if (JSON.stringify(manifest.permissions) !== JSON.stringify(['identity'])) {
    fail('permissions must be ["identity"] (Teams SSO for the teamslogon bootstrap)');
}

// Teams rejects icons of any other size; read width and height from the PNG header.
for (const [icon, size] of Object.entries(ICONS)) {
    const png = readFileSync(join(startDir, icon));
    const width = png.readUInt32BE(16);
    const height = png.readUInt32BE(20);
    if (width !== size || height !== size) {
        fail(`${icon} is ${width}x${height}, Teams needs ${size}x${size}`);
    }
}

mkdirSync(buildDir, {recursive: true});
writeFileSync(join(buildDir, 'manifest.json'), manifestText);
for (const icon of Object.keys(ICONS)) {
    copyFileSync(join(startDir, icon), join(buildDir, icon));
}

// zip updates an existing archive in place, so start from scratch.
const zipPath = join(buildDir, ZIP_NAME);
rmSync(zipPath, {force: true});
execFileSync('zip', ['-q', '-X', '-j', zipPath, 'manifest.json', ...Object.keys(ICONS)], {
    cwd: buildDir,
    stdio: 'inherit',
});

console.log(relative(process.cwd(), zipPath) || zipPath);
