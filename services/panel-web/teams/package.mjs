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
//   HOST=my-tunnel-3000.euw.devtunnels.ms node teams/package.mjs
//
// Output: teams/build/manifest.json, the two icons and teams/build/panel-mr-<host>.zip

import {copyFileSync, mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {dirname, join, relative} from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import process from 'node:process';

const DEFAULT_HOST = 'panel.mlodzirazem.org';
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

const manifestText = readFileSync(join(teamsDir, 'manifest.template.json'), 'utf8')
    .replaceAll('{{HOST}}', host);
if (manifestText.includes('{{')) {
    fail('manifest.template.json has a placeholder other than {{HOST}}');
}
try {
    JSON.parse(manifestText);
} catch (e) {
    fail(`manifest is not valid JSON: ${e.message}`);
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
const zipPath = join(buildDir, `panel-mr-${host}.zip`);
rmSync(zipPath, {force: true});
execFileSync('zip', ['-q', '-X', '-j', zipPath, 'manifest.json', ...Object.keys(ICONS)], {
    cwd: buildDir,
    stdio: 'inherit',
});

console.log(relative(process.cwd(), zipPath) || zipPath);
