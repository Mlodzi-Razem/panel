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

// Rasterises "Start" Teams app icons from the source SVGs into the exact
// pixel sizes Microsoft Teams requires (same rules as teams/README.md):
//
//   color.png    192x192  plum #720546 rounded badge, white house glyph
//   outline.png   32x32   the same house glyph, white on transparency —
//                        only white pixels with varying opacity are allowed
//
// Run from services/panel-web (so the local node_modules is used):
//
//   cd services/panel-web && node teams-start/scripts/generate-assets.mjs
//
// No new dependencies: sharp (already in services/panel-web/node_modules)
// renders the SVGs. macOS qlmanage is NOT used — it renders these SVGs at
// the wrong scale (see ../README.md).
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(
  path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../package.json'),
);
const sharp = require('sharp');

const DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const TARGETS = [
  { svg: 'color.svg', png: 'color.png', size: 192 },
  { svg: 'outline.svg', png: 'outline.png', size: 32 },
];

/** PNG options tuned for small flat-colour artwork (entra-branding pattern). */
const smallPng = { compressionLevel: 9, palette: true, quality: 100 };

async function main() {
  const report = [];

  for (const { svg, png, size } of TARGETS) {
    const pngPath = path.join(DIR, png);
    await sharp(path.join(DIR, svg))
      .resize(size, size, { fit: 'fill' })
      .png(smallPng)
      .toFile(pngPath);

    // Teams rejects icons of any other size; re-read dimensions from the file.
    const meta = await sharp(pngPath).metadata();
    if (meta.width !== size || meta.height !== size) {
      throw new Error(`${png} is ${meta.width}x${meta.height}, Teams needs ${size}x${size}`);
    }

    const { data, info } = await sharp(pngPath).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    const bytes = info.width * info.height * info.channels;
    if (png === 'outline.png') {
      // The outline icon may contain only white pixels (with transparency).
      for (let i = 0; i < bytes; i += info.channels) {
        if (data[i + 3] > 0 && (data[i] < 250 || data[i + 1] < 250 || data[i + 2] < 250)) {
          throw new Error(`${png} has a non-white pixel — only white with transparency is allowed`);
        }
      }
    } else {
      // The colour badge is a plum rounded square with a white house, like
      // teams/color.png — its corners are transparent by design. Truth-probe
      // the geometry at points deep inside flat regions, so exact colours are
      // expected within anti-aliasing tolerance.
      const px = (x, y) => {
        const i = (y * info.width + x) * info.channels;
        return [data[i], data[i + 1], data[i + 2], data[i + 3]];
      };
      const plum = [114, 5, 70];  // #720546
      const white = [255, 255, 255];
      const probes = [
        { x: 96, y: 4, rgb: plum, a: 255, what: 'badge top edge' },
        { x: 4, y: 4, rgb: null, a: 0, what: 'transparent rounded corner' },
        { x: 96, y: 96, rgb: white, a: 255, what: 'white roof mass' },
        { x: 30, y: 140, rgb: white, a: 255, what: 'white wall beside the door' },
        { x: 96, y: 132, rgb: plum, a: 255, what: 'plum through the door hole' },
      ];
      for (const { x, y, rgb, a, what } of probes) {
        const [pr, pg, pb, pa] = px(x, y);
        const rgbOk = rgb === null || (Math.abs(pr - rgb[0]) <= 8 && Math.abs(pg - rgb[1]) <= 8 && Math.abs(pb - rgb[2]) <= 8);
        if (!rgbOk || Math.abs(pa - a) > 15) {
          throw new Error(`${png}: probe (${x},${y}) — ${what} failed, got rgba(${pr},${pg},${pb},${pa})`);
        }
      }
    }

    const kb = (Math.round((await (await import('node:fs/promises')).stat(pngPath)).size / 10.24) / 100).toFixed(2);
    report.push(`${png}  ${meta.width}x${meta.height}  ${kb} KB  ok`);
  }

  console.log(report.join('\n'));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
