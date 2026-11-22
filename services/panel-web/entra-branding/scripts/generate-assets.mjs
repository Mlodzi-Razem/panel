/**
 * Panel MR — Entra company branding asset generator.
 *
 * Rasterises the source logo SVG and crops the Kongres 2025 photo into
 * the formats Microsoft Entra ID company branding accepts
 * (limits verified 2026-11-21 against Microsoft Learn — see ../research-notes.md):
 *
 *   background       1920x1080 PNG/JPG, max 300 KB   (admin-centre cap)
 *   background       1920x1080 PNG/JPG, max 300 KB   (same cap; a programmatic
 *                     SVG -> sharp replica of the Panel MR login .brand gradient,
 *                     layered over the photo option)
 *   banner logo       245x36   PNG/JPG, max 50 KB
 *   square logo       240x240  PNG/JPG, max 50 KB     (light + dark theme)
 *   header logo       245x36   PNG/JPG, max 10 KB     (optional header strip)
 *   favicon           32x32    PNG/JPG, max 5 KB
 *
 * Run from services/panel-web (so the local node_modules is used):
 *
 *   cd services/panel-web && node entra-branding/scripts/generate-assets.mjs
 *
 * No new dependencies: sharp (already in services/panel-web/node_modules)
 * handles both the SVG (librsvg) and the JPEG. macOS qlmanage is NOT used —
 * it renders these SVGs at the wrong scale (see teams/README.md).
 * Build outputs carry no EXIF (identity/photo metadata is stripped), and
 * binaries are git-ignored via ../.gitignore.
 */
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(
  path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../package.json'),
);
const sharp = require('sharp');

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = path.join(ROOT, 'source');
const BUILD = path.join(ROOT, 'build');

const svg = path.join(SRC, 'mr-favicon-400x400.svg');
const photo = path.join(SRC, 'mr-kongres-2025-photo.jpeg');

// Microsoft limits we must satisfy (bytes).
const CAPS = {
  background: 300 * 1024,
  banner: 50 * 1024,
  square: 50 * 1024, // Graph wording says 10 KB; we aim there but the portal cap is 50 KB
  header: 10 * 1024,
  favicon: 5 * 1024,
};

async function fileSize(p) {
  const { stat } = await import('node:fs/promises');
  return (await stat(p)).size;
}

/** PNG options tuned for small flat-colour artwork (logo badge). */
const smallPng = { compressionLevel: 9, palette: true, quality: 100 };

async function main() {
  const report = [];

  // ---------------------------------------------------------------- background
  // "cover", centred: scale up 1620x1080 -> 1920-wide, centre-crop to 1920x1080.
  // Quality is lowered step by step until the file fits the 300 KB cap.
  // No dark overlay: Entra renders all task text inside opaque surfaces
  // (lightbox / split panel), so the photo stays recognisable and untouched.
  let bgQuality = 88;
  const bgTarget = path.join(BUILD, 'background-1920x1080.jpg');
  for (;;) {
    await sharp(photo)
      .resize(1920, 1080, { fit: 'cover', position: 'centre' })
      .jpeg({ quality: bgQuality, progressive: true, mozjpeg: true })
      .toFile(bgTarget);
    const size = await fileSize(bgTarget);
    if (size <= CAPS.background || bgQuality <= 70) break;
    bgQuality -= 4;
  }

  // ------------------------------------------------------- badge (square logo)
  // The provided favicon SVG is a full-bleed rounded square — the organisation's
  // designated main logo mark. Same badge serves the light and dark theme slots
  // ("If your logo looks good on light and dark backgrounds, there's no need to
  // add a dark theme logo.") — we still ship an explicit dark-slot file.
  const squareBuffer = await sharp(svg).resize(240, 240, { fit: 'fill' }).png(smallPng).toBuffer();
  const squareLight = path.join(BUILD, 'square-logo-light-240x240.png');
  const squareDark = path.join(BUILD, 'square-logo-dark-240x240.png');
  await sharp(squareBuffer).toFile(squareLight);
  await sharp(squareBuffer).toFile(squareDark); // identical mark on purpose

  // ------------------------------------------------------------------- favicon
  const faviconPng = path.join(BUILD, 'favicon-32x32.png');
  await sharp(svg).resize(32, 32, { fit: 'fill' }).png(smallPng).toFile(faviconPng);

  // ------------------------------------------------------- banner / header logo
  // 245x36 transparent strip. First incarnation used the 36x36 badge — too
  // small to read ("banner is really invisible"). Instead we use the "mr"
  // letterform monogram: the same letterforms the Teams app icon set uses
  // (teams/outline.svg), extracted from the favicon SVG (background path
  // removed, remaining paths flattened to a single plum-700 ink), so the
  // mark fills the full 36px height of the Entra slot (~82x36) instead of
  // riding inside a 36px badge. Microsoft recommends transparent PNG
  // without padding. Same artwork for both slots; the header slot has the
  // tighter 10 KB cap.
  const { readFile } = await import('node:fs/promises');
  const faviconSource = (await readFile(svg)).toString();
  const lettersSvg = faviconSource
    .split('\n')
    .filter((line) => !line.includes('fill="#6F1046"')) // drop the badge background path
    .map((line) => line.replace(/fill="#[0-9A-Fa-f]{6}"/g, 'fill="#720546"')) // single plum ink
    .join('\n');
  const letterSpacingPx = 0; // no padding around the logo (Microsoft's own recommendation)
  const letters = await sharp(Buffer.from(lettersSvg))
    .resize(1000, 1000, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer();
  const trimmed = await sharp(letters).trim({ threshold: 1 }).toBuffer({ resolveWithObject: true });
  const inkW = trimmed.info.width;
  const inkH = trimmed.info.height;
  // scale ink to fit the 245x36 slot edge to edge
  const fitW = 245 - 2 * letterSpacingPx;
  const fitH = 36 - 2 * letterSpacingPx;
  const scale = Math.min(fitW / inkW, fitH / inkH);
  const scaled = await sharp(trimmed.data)
    .resize(Math.round(inkW * scale), Math.round(inkH * scale), { fit: 'fill' })
    .png()
    .toBuffer();
  const compositeLeft = Math.round((245 - Math.round(inkW * scale)) / 2);
  const compositeTop = Math.round((36 - Math.round(inkH * scale)) / 2);
  async function wideLogo(file, cap,_pngOpts) {
    let buffer = await sharp({
      create: { width: 245, height: 36, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
    })
      .composite([{ input: scaled, left: compositeLeft, top: compositeTop }])
      .png(smallPng)
      .toBuffer();
    if (buffer.length > cap) {
      // fall back to full-colour (non-palette) PNG which compresses cleaner here
      buffer = await sharp({
        create: { width: 245, height: 36, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
      })
        .composite([{ input: scaled, left: compositeLeft, top: compositeTop }])
        .png({ compressionLevel: 9 })
        .toBuffer();
    }
    const { writeFile: wf } = await import('node:fs/promises');
    await wf(file, buffer);
  }
  await wideLogo(path.join(BUILD, 'banner-logo-245x36.png'), CAPS.banner);
  await wideLogo(path.join(BUILD, 'header-logo-245x36.png'), CAPS.header);

  // --------------------------------------- Panel MR brand gradient background
  // Faithful replica of the Panel browser login .brand panel
  // (app/(public)/login/LoginPage.module.scss):
  //   radial-gradient(circle at 20% 15%, var(--plum-500) 0%, transparent 55%),
  //   linear-gradient(145deg, var(--plum-700) 0%, var(--plum-900) 70%, var(--plum-950) 100%);
  // transposed from CSS gradient math onto the 1920x1080 canvas:
  //   145deg axis (screen coords, y down): centre (960,540) + unit (sin145,-cos145)
  //     = (0.5736, 0.8192); half-length = 960*0.5736 + 540*0.8192 = 993
  //     -> start (391,-273), end (1529,1353)  (CSS "to" direction matches 145deg)
  //   radial "circle at 20% 15%" -> centre (384,162); farthest-corner radius
  //     = |(1920,1080)-(384,162)| = ~1789px; "transparent at 55%" -> r ~= 984.
  // "transparent" in CSS premultiplies, so browsers keep the plum-500 hue while
  // alpha falls; librsvg lerps non-premultiplied, so fading to black-transparent
  // (the literal CSS value) would darken the falloff. Fading to plum-transparent
  // reproduces the exact browser appearance (constant hue, falling alpha).
  const gradientSvg = `<?xml version="1.0" encoding="UTF-8"?>
<svg width="1920" height="1080" viewBox="0 0 1920 1080" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="lg" gradientUnits="userSpaceOnUse" x1="391" y1="-273" x2="1529" y2="1353">
      <stop offset="0" stop-color="#720546"/>
      <stop offset="0.7" stop-color="#3d0a27"/>
      <stop offset="1" stop-color="#2a0619"/>
    </linearGradient>
    <radialGradient id="rg" gradientUnits="userSpaceOnUse" cx="384" cy="162" r="984">
      <stop offset="0" stop-color="#a8216f"/>
      <stop offset="1" stop-color="#a8216f" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="1920" height="1080" fill="url(#lg)"/>
  <rect width="1920" height="1080" fill="url(#rg)"/>
</svg>`;
  let gradientName = 'background-plum-gradient-1920x1080.png';
  let gradientQ = null;
  let gradientPath = path.join(BUILD, gradientName);
  let gradientBytes = await sharp(Buffer.from(gradientSvg)).png({ compressionLevel: 9 }).toBuffer();
  if (gradientBytes.length > CAPS.background) {
    // lossless PNG did not fit — fall back to high-quality JPEG (gradient stays smooth)
    gradientName = 'background-plum-gradient-1920x1080.jpg';
    gradientPath = path.join(BUILD, gradientName);
    gradientQ = 95;
    for (;;) {
      gradientBytes = await sharp(Buffer.from(gradientSvg)).jpeg({ quality: gradientQ, progressive: true }).toBuffer();
      if (gradientBytes.length <= CAPS.background || gradientQ <= 80) break;
      gradientQ -= 5;
    }
    const { writeFile } = await import('node:fs/promises');
    await writeFile(gradientPath, gradientBytes);
    // remove an earlier PNG attempt so build/ stays exact
    await (await import('node:fs/promises')).unlink(path.join(BUILD, 'background-plum-gradient-1920x1080.png')).catch(() => {});
  } else {
    const { writeFile } = await import('node:fs/promises');
    await writeFile(gradientPath, gradientBytes);
  }

  // -------------------------------------------------------------------- report
  const outputs = [
    ['background-1920x1080.jpg', bgQuality, CAPS.background, 'Background image option A — Kongres 2025 photo'],
    [gradientName, gradientQ, CAPS.background, 'Background image option B — Panel brand gradient (default)'],
    ['square-logo-light-240x240.png', null, CAPS.square, 'Square logo · light theme'],
    ['square-logo-dark-240x240.png', null, CAPS.square, 'Square logo · dark theme'],
    ['banner-logo-245x36.png', null, CAPS.banner, 'Banner logo (Sign-in form)'],
    ['header-logo-245x36.png', null, CAPS.header, 'Header logo (optional Header strip)'],
    ['favicon-32x32.png', null, CAPS.favicon, 'Favicon (Basics)'],
  ];
  for (const [name, q, cap, label] of outputs) {
    const p = path.join(BUILD, name);
    const size = await fileSize(p);
    const meta = await sharp(p).metadata();
    const kb = (size / 1024).toFixed(1);
    const ok = size <= cap && meta.width && meta.height;
    if (!ok) throw new Error(`CAP/DIM FAIL: ${name} (${meta.width}x${meta.height}, ${kb} KB)`);
    report.push(
      `${name}  ${meta.width}x${meta.height}  ${kb} KB (cap ${(cap / 1024)} KB)${q ? ` q=${q}` : ''}  — ${label}`,
    );
  }
  console.log(report.join('\n'));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
