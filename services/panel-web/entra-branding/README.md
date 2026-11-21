# Panel MR — Microsoft Entra ID company branding package

Everything needed to make the tenant sign-in pages (login.microsoftonline.com for
Stowarzyszenie Młodzi Razem) look like the Panel MR browser login page: the plum
brand gradient (or the Kongres 2025 photo as an alternative), the "mr" badge,
plum accents and member-facing help texts.

> **Who does what** — this folder was prepared by the design agent (texts, assets,
> CSS, documentation only). **All tenant changes below are performed by you, the
> tenant owner, with your own admin clicks** — the agent never signs in to the
> tenant, never calls Microsoft Graph against it, never uploads anything, and
> never runs the PowerShell script or `git commit`/`git push` for this folder.

All limits referenced here were verified against Microsoft Learn on **2026-11-21**
(sources listed in [`research-notes.md`](research-notes.md)) — not from memory.

## Package contents

| Path | What it is | Committable? |
|---|---|---|
| `source/mr-favicon-400x400.svg` | Copy of the organisation's main logo mark (favicon SVG, plum #6F1046 badge, white "mr") | yes (logo, no personal data) |
| `source/mr-kongres-2025-photo.jpeg` | Copy of the Kongres 2025 group photo (**GDPR — identifiable people**) | **no — git-ignored** |
| `build/…` | All generated tenant-upload assets (see table below) | no — git-ignored, regenerable |
| `scripts/generate-assets.mjs` | Regenerates `build/` from `source/` using the repo's `sharp` | yes |
| [`texts.md`](texts.md) | Sign-in page texts (pl default + en localization) with exact character counts | yes |
| [`custom.css`](custom.css) | Optional sign-in page CSS within the doc-allowed selectors | yes |
| [`research-notes.md`](research-notes.md) | Verified Microsoft-learn facts + URLs (STEP 1) | yes |

Regenerate assets any time: `cd services/panel-web && node entra-branding/scripts/generate-assets.mjs`
(uses the existing `node_modules` — notably `sharp`; macOS `qlmanage` is **not** used
because it renders these SVGs at the wrong scale — see `teams/README.md`).

### Asset inventory — verified dimensions/sizes (sips-checked 2026-11-21)

| Upload slot (admin-centre section) | File | Dimensions | Size | Cap (Learn) |
|---|---|---|---|---|
| Basics → **Favicon** | `build/favicon-32x32.png` | 32×32 | 0.9 KB | ≤ 5 KB |
| Basics → **Background image** — **option B, default (Panel login replica)** | `build/background-plum-gradient-1920x1080.png` | 1920×1080 | 251.3 KB | ≤ 300 KB |
| Basics → **Background image** — option A (alternative, warmth) | `build/background-1920x1080.jpg` | 1920×1080 | 259.3 KB | ≤ 300 KB |
| Basics → **Page background color** | — value: `#3d0a27` (gradient) / `#720546` (photo) — | — | — | hex color |
| Sign-in form → **Banner logo** | `build/banner-logo-245x36.png` | 245×36 | 1.1 KB | ≤ 50 KB |
| Sign-in form → **Square logo (light theme)** | `build/square-logo-light-240x240.png` | 240×240 | 8.0 KB | ≤ 50 KB |
| Sign-in form → **Square logo (dark theme)** | `build/square-logo-dark-240x240.png` | 240×240 | 8.0 KB | ≤ 50 KB |
| Header (optional strip) → **Header logo** | `build/header-logo-245x36.png` | 245×36 | 1.1 KB | ≤ 10 KB |
| Layout → **Custom CSS** (optional — see below) | `custom.css` | — | 11.6 KB | ≤ 25 KB, `.css` only |

**Background choice — the two options.** The default **`background-plum-gradient-1920x1080.png`**
is a faithful, pixel-verified replica of the Panel login `.brand` panel
(`app/(public)/login/LoginPage.module.scss`): a radial plum-500 (`#a8216f`) highlight
at 20%/15% fading out at 55%, over the 145° linear ramp plum-700 → plum-900 (70%) →
plum-950 (`#720546` → `#3d0a27` → `#2a0619`), full bleed, no text/logo/noise (the white
mark comes from the logo slots). It is regenerated from an SVG definition in
`scripts/generate-assets.mjs` — CSS gradient math (145° axis, farthest-corner radius)
transposed to the 1920×1080 canvas — and exported losslessly (PNG, 251.3 KB, no
banding). Choose it when the sign-in page must look like the Panel MR login.
The alternative **`background-1920x1080.jpg`** is the centred "cover" crop of the
Kongres 2025 photo (1620×1080 → 1920×1080, slight upscale, q88, EXIF stripped, no
dark overlay — task text sits on opaque surfaces, so the photo stays fully
recognisable). Choose it if you later prefer warmth over fidelity (GDPR note §D
applies only to this option).

The remaining assets are rasterised from the favicon SVG as-is (identical mark for
light and dark theme slots — the docs explicitly allow one logo for both:
"If your logo looks good on light and dark backgrounds, there's no need to add a
dark theme logo") and the banner/header logos are the badge centred on a
transparent 245×36 PNG (Microsoft recommends transparent PNGs without padding).

## A. Upload path — Microsoft Entra admin centre (recommended)

Prerequisites (per Learn): an **Organizational Branding Administrator** (or Global
Admin) and a licence: Entra ID P1/P2, M365 Business Standard, or SharePoint Plan 1.
If branding already exists you will see **Edit** instead of the initial creation flow.

1. Sign in at <https://entra.microsoft.com> → **Entra ID → Custom Branding** → create the **default** experience.
2. **Basics** — upload, in order:
   - Favicon → `build/favicon-32x32.png`
   - Background image → **`build/background-plum-gradient-1920x1080.png`** (default — makes the page look like the Panel MR login; the Kongres photo `build/background-1920x1080.jpg` is a one-slot swap if you ever prefer warmth)
   - Page background color → `#3d0a27` (fallback for slow connections — plum-900, the gradient's dominant tone; use `#720546` if you swap to the photo)
3. **Layout**:
   - Visual template: choose the **partial-screen background** (vertical-split/ADFS-style) —
     per Learn: "the full-screen background could obscure your background image, so
     choose the partial-screen background if your background image is important."
     The Graph value is `layoutTemplateType: verticalSplit`.
     With the gradient this mirrors the Panel login composition: brand panel
     (gradient) on the split side, calm form side with the card.
   - **Show header** = **off** (recommended — the Panel login has no top strip;
     closest fidelity. If you want the strip anyway, set it on and upload
     `build/header-logo-245x36.png` in the Header section.)
   - **Show footer** = on (default on; keep it for Microsoft's own low-key links).
   - **Custom CSS**: see §C before deciding.
4. **Header** (only if you kept the strip on): upload `build/header-logo-245x36.png`.
   Note: Entra has **no free-text header field** in any case — the "header" is a
   logo slot plus the tenant name; see [`texts.md`](texts.md).
5. **Footer**: leave **Privacy & Cookies** and **Terms of Use** as the Microsoft
   defaults (any custom URL renders as unclickable text, and we deliberately did not
   invent MR policy URLs). Uncheck either only if you want it hidden.
6. **Sign-in form**:
   - Banner logo → `build/banner-logo-245x36.png`
   - Square logo (light theme) → `build/square-logo-light-240x240.png`
   - Square logo (dark theme) → `build/square-logo-dark-240x240.png`
   - Username hint text → **leave empty** (guests share the page; UPN formats are
     ambiguous — rationale and an optional 26/27-char hint are in `texts.md` §2)
   - Sign-in page text → paste the **Polish** two-paragraph block from
     `texts.md` §1 (169 characters, limit 1024 — also reproduced below)
   - SSPR section → leave defaults
   - But first, optionally check the tenant display name (**Entra ID → Overview**) —
     it is the large title rendered in the sign-in box; it should read
     *Stowarzyszenie Młodzi Razem*.
7. **Review → Create.** Changes propagate within minutes (tenant geography/CDN).
8. **Add the English localization**: back on Custom Branding → **Add browser language**
   → pick **English**. Only change **Sign-in page text** there (paste the English block
   from `texts.md` §1, 182 characters). Every element you leave untouched falls back to
   the default experience automatically — do not re-upload assets.
   (A separate `pl` localization is unnecessary because the default is already Polish;
   Polish browsers show the default experience.)
9. **Verify** the branding appears: tenant-specific URLs show it immediately, e.g.
   `https://myapps.microsoft.com/?whr=mlodzirazem.org`;
   on the generic `https://login.microsoftonline.com` start page it appears after the
   user types their e-mail and selects Next (Home Realm Discovery). B2B guests see
   their own tenant's branding; personal Microsoft accounts never see tenant branding.

## B. The texts to paste (exact, character-counted — details in [`texts.md`](texts.md))

**Default (Polish) — Sign-in page text — 169 characters:**

```
**Panel MR** — system członkowy Stowarzyszenia Młodzi Razem.

Pomoc IT: społeczność Viva Engage — engage.cloud.microsoft. Zgłoś incydent: bezpieczenstwo@mlodzirazem.org.
```

**Localization `en` (English) — Sign-in page text — 182 characters:**

```
**Panel MR** — the membership system of Stowarzyszenie Młodzi Razem.

IT Help: the Viva Engage community — engage.cloud.microsoft. Report an incident: bezpieczenstwo@mlodzirazem.org.
```

Paragraph break = **Enter pressed twice** (Learn-documented behaviour). Both contact
points already exist in the Panel MR app copy; nothing new was invented. Keep the
**Username hint text** empty (limit 64 chars — see `texts.md` §2 for the full reasoning
and optional strings). Do not put sensitive information in these fields — the sign-in
page is public.

## C. Custom CSS — availability status (read before using)

`custom.css` (11.6 KB) reproduces the Panel MR **login form side** on the sign-in
page: the `.card` (white surface, hairline gray-200 border, 16px radius, the
two-layer `--shadow-2`), title/subtitle typography (gray-950 / gray-600, Panel
heading rhythm), plum-700 links with plum-800 hover, the theme focus recipe
(3px `plum-600 @ 35%` ring), inputs per `components/form/Form.module.scss`
(gray-300 border, 8px radius, plum-600 focus border + ring, danger/danger-bg
error), the primary button as the plum pill with white text and its soft plum
shadow and plum-800 hover, and the note text (13px, muted, centered) —
`components/button/Button.module.scss` and `LoginPage.module.scss` token by token.
It uses **only** the selectors Microsoft enumerates for company branding and
**none** of the 58 deprecated layout/positioning properties (no `position`,
`margin`, `transform`, `opacity`, ...), no `::before/::after`
content insertion and no external fonts. Where Entra's fixed layout prevents
true fidelity (no element reordering, no brand-side text), the file favours
restraint — the metronome of the design is its calm proportions, single accent
colour and whitespace, not clever tricks.

However, per Microsoft Learn (verified 2026-11-21): tenants created after
**2026-01-05** never receive custom CSS, and after **2026-07-21** tenants created
before 2026-01-06 that **didn't already use custom CSS can no longer configure it**;
the feature is on a path to full retirement (Secure Future Initiative / anti-phishing).
So before touching it: open **Layout** and check whether the *Custom CSS* upload option
still exists for this tenant, and only upload `custom.css` if it does. It is a
non-load-bearing bonus: the assets, texts and template choice above already carry the
identity. If CSS is available, upload the same file to the default experience (and to
the `en` localization if you want it guaranteed there — unset elements fall back to
default).

## D. GDPR note

The Kongres 2025 background photo contains identifiable people — relevant only if you
choose it as the background (option A). In this package it exists **only on this
machine**: both the source copy and the generated `background-1920x1080.jpg` are
git-ignored ("never upload" applies to this agent — the one legitimate destination
for the background asset is your own Entra tenant, uploaded by you as tenant owner).
The default gradient background contains no imagery whatsoever. The build photo asset
carries no EXIF/camera metadata, no personal names appear in any file of this package,
and no personal data beyond the already-public product/help addresses is included in
any text field.

## E. Alternative — Microsoft Graph PowerShell (YOU run it; the agent never does)

The script below reproduces §A steps 2–8 in one go (default + `en` localization,
layout template, plum fallback color). It needs no secrets — it signs in
interactively with your account. **The agent that prepared this package has not
run it and will not run it; it is here for you to review and run yourself.**

Save the block as `entra-branding/apply-branding.ps1` (so `$PSScriptRoot` resolves),
read every line, then run it interactively.

```powershell
# =============================================================================
#  Panel MR — Entra company branding (graph/powershell)     YOU RUN THIS FILE
#  -----------------------------------------------------------------------------
#  Prepared by the design agent as a TEMPLATE. The agent never executed it and
#  has no access to your tenant. You review and run it as Organizational
#  Branding Administrator (or Global Administrator).
#
#  Prereqs: PowerShell 7+, Microsoft Graph PowerShell SDK v2
#    Install-Module Microsoft.Graph.Beta.Identity.DirectoryManagement -Scope CurrentUser
#  Tenant licence: Entra ID P1/P2 (or M365 Business Standard / SharePoint Plan 1)
# =============================================================================

#Requires -Version 7
$ErrorActionPreference = 'Stop'

# 0. Safety stop — type YES to do anything at all.
if ((Read-Host "This changes company branding for YOUR Entra tenant. Type YES to continue") -ne 'YES') {
    Write-Host 'Aborted. Nothing was changed.'; exit
}

# 1. Connect to Microsoft Graph (interactive sign-in, delegated permissions; no secrets).
Import-Module Microsoft.Graph.Beta.Identity.DirectoryManagement
Connect-MgGraph -Scopes Organization.ReadWrite.All -NoWelcome

# 2. Paths — assumes the script sits inside entra-branding/.
$root  = $PSScriptRoot
$build = Join-Path $root 'build'

# 3. Your tenant (organization) id.
$org = (Get-MgOrganization -Property id).Id

# 4. Values (character counts verified; see texts.md).
#    Default sign-in page text (Polish) — 169 characters, limit 1024:
$plSignInPageText =
    "**Panel MR** — system członkowy Stowarzyszenia Młodzi Razem.`n`n" +
    "Pomoc IT: społeczność Viva Engage — engage.cloud.microsoft. Zgłoś incydent: bezpieczenstwo@mlodzirazem.org."

#    'en' localization sign-in page text (English) — 182 characters:
$enSignInPageText =
    "**Panel MR** — the membership system of Stowarzyszenie Młodzi Razem.`n`n" +
    "IT Help: the Viva Engage community — engage.cloud.microsoft. Report an incident: bezpieczenstwo@mlodzirazem.org."

#    Layout: partial-screen (verticalSplit) template mirrors the Panel login
#    composition (gradient brand panel + gray form side); header strip OFF for
#    fidelity — switch isHeaderShown to $true (and upload the header logo in the
#    admin centre) if you want the top bar; footer on (Microsoft defaults).
$layout = @{
    layoutTemplateType = 'verticalSplit'
    isHeaderShown      = $false
    isFooterShown      = $true
}

# 5. Create-or-update the DEFAULT branding experience with all assets + texts.
$branding = Get-MgBetaOrganizationBranding -OrganizationId $org -ErrorAction SilentlyContinue
$defaultArgs = @{
    OrganizationId             = $org
    BackgroundImageInputFile   = Join-Path $build 'background-plum-gradient-1920x1080.png'  # default: Panel brand gradient; swap to 'background-1920x1080.jpg' for the Kongres photo
    FaviconInputFile           = Join-Path $build 'favicon-32x32.png'
    BannerLogoInputFile        = Join-Path $build 'banner-logo-245x36.png'
    SquareLogoInputFile        = Join-Path $build 'square-logo-light-240x240.png'
    SquareLogoDarkInputFile    = Join-Path $build 'square-logo-dark-240x240.png'
    BackgroundColor            = '#3d0a27'           # plum-900, the gradient's tone ('#720546' with the photo option)
    SignInPageText             = $plSignInPageText
    LoginPageLayoutConfiguration = $layout
}
#    If you switch isHeaderShown to $true, also add to $defaultArgs:
#      HeaderLogoInputFile = Join-Path $build 'header-logo-245x36.png'
if ($branding) { Update-MgBetaOrganizationBranding @defaultArgs }
else           { New-MgBetaOrganizationBranding  @defaultArgs }

# 6. Create-or-update the 'en' browser-language localization (English text only;
#    everything not set falls back to the default experience).
$enLoc = Get-MgBetaOrganizationBrandingLocalization -OrganizationId $org `
        -OrganizationalBrandingLocalizationId 'en' -ErrorAction SilentlyContinue
if ($enLoc) { Update-MgBetaOrganizationBrandingLocalization -OrganizationId $org `
                -OrganizationalBrandingLocalizationId 'en' -SignInPageText $enSignInPageText }
else        { New-MgBetaOrganizationBrandingLocalization  -OrganizationId $org `
                -Id 'en' -SignInPageText $enSignInPageText }

# 7. OPTIONAL — custom CSS. ⚠ First read README §C: after 2026-07-21 tenants that
#    did not already use custom CSS can no longer configure it. Check the Layout
#    section. Uncomment ONLY if the option is still there:
# Update-MgBetaOrganizationBranding -OrganizationId $org -CustomCssInputFile (Join-Path $root 'custom.css')

#    If a username hint was ever set and you want it gone (we recommend none):
# Update-MgBetaOrganizationBranding -OrganizationId $org -UsernameHintText ''
# Update-MgBetaOrganizationBrandingLocalization -OrganizationId $org -OrganizationalBrandingLocalizationId 'en' -UsernameHintText ''

# 8. Show the result (read-only). Propagation may take a few minutes.
Get-MgBetaOrganizationBranding -OrganizationId $org |
    Format-List Id, BackgroundColor, SignInPageText
Get-MgBetaOrganizationBrandingLocalization -OrganizationId $org |
    Select-Object Id, SignInPageText

Disconnect-MgGraph
```

SDK notes: the `*-InputFile` parameters upload a local file as the stream property
(Graph beta `organizationalBranding`); if your SDK version spells a parameter
differently (e.g. `-CustomCSSInputFile` vs `-CustomCssInputFile`), tab-complete the
cmdlet to confirm. If `[hashtable]` binding for `-LoginPageLayoutConfiguration` is
rejected by an older SDK, create the typed object shown by
`(Get-Command Update-MgBetaOrganizationBranding).Parameters['LoginPageLayoutConfiguration']`.

## Reference — verified limits, at a glance

| Item | Value (Microsoft Learn, 2026-11-21) |
|---|---|
| Admin path | Entra admin centre → **Entra ID → Custom Branding** (sections: Basics…Layout…Header…Footer…Sign-in form…Review) |
| Favicon | 32×32 px, PNG/JPG, ≤ 5 KB |
| Background image | 1920×1080 px, PNG/JPG, ≤ 300 KB; scales/crops to window |
| Page background color | hex fallback for slow connections |
| Visual templates | full-screen background (centered lightbox) / partial-screen background (`verticalSplit`) |
| Header logo | 245×36 px, ≤ 10 KB (only shown when the header is enabled) |
| Banner logo | 245×36 px, ≤ 50 KB |
| Square logo light/dark | 240×240 px, ≤ 50 KB each |
| Sign-in page text | ≤ 1024 chars; `**bold**`, `[text](link)`; new paragraph = Enter twice; links render as text in native apps |
| Username hint text | ≤ 64 chars; "If guests use the same sign-in page, we don't recommend using hint text" |
| Custom CSS | one `.css` per branding object (default + each localization), ≤ 25 KB, enumerated selectors only, 58 layout/positioning properties deprecated; feature retiring (§C) |
| Localization | default experience + "Add browser language" overrides; unset fields fall back to default |

Primary sources: <https://learn.microsoft.com/en-us/entra/fundamentals/how-to-customize-branding>,
<https://learn.microsoft.com/en-us/entra/fundamentals/reference-company-branding-css-template>,
<https://learn.microsoft.com/en-us/graph/api/resources/organizationalbranding?view=graph-rest-beta> —
full list in [`research-notes.md`](research-notes.md).
