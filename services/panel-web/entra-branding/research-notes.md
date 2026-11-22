# Microsoft Entra ID company branding — verified facts (STEP 1 research notes)

Research for the Panel MR sign-in experience branding package.
**All limits below were verified against the current Microsoft Learn documentation on 2026-11-21.**
They supersede any numbers quoted from memory.

## 1. Primary sources

| # | Source | URL |
|---|---|---|
| 1 | *Configure your company branding* (Microsoft Entra admin-center how-to — the authoritative page for image/text limits) | https://learn.microsoft.com/en-us/entra/fundamentals/how-to-customize-branding |
| 2 | *CSS template reference guide* (allowed selectors, deprecated properties, retirement timeline) | https://learn.microsoft.com/en-us/entra/fundamentals/reference-company-branding-css-template |
| 3 | `organizationalBranding` resource type (Graph /beta — property list and caps) | https://learn.microsoft.com/en-us/graph/api/resources/organizationalbranding?view=graph-rest-beta |
| 4 | `organizationalBrandingLocalization` resource type (Graph /beta — per-language branding) | https://learn.microsoft.com/en-us/graph/api/resources/organizationalbrandinglocalization?view=graph-rest-beta |
| 5 | `loginPageLayoutConfiguration` resource type (templates, header/footer switches) | https://learn.microsoft.com/en-us/graph/api/resources/loginpagelayoutconfiguration?view=graph-rest-beta |
| 6 | `loginPageTextVisibilitySettings` resource type (footer/link hiding flags) | https://learn.microsoft.com/en-us/graph/api/resources/loginpagetextvisibilitysettings?view=graph-rest-beta |
| 7 | *Update organizationalBrandingLocalization* (Graph /beta — clearing a localized field example) | https://learn.microsoft.com/en-us/graph/api/organizationalbrandinglocalization-update?view=graph-rest-beta |
| 8 | Microsoft Entra blog: *Microsoft Entra ID enhances security of branded sign-ins* (announcement of the CSS retirement; linked from Learn; the page itself is behind a captcha for bots, so facts below are taken from the Learn pages that cite it) | https://techcommunity.microsoft.com/blog/microsoft-entra-blog/microsoft-entra-id-enhances-security-of-branded-sign-ins/4537471 |
| 9 | Graph PowerShell cmdlet docs (parameter names for branding uploads) | https://learn.microsoft.com/en-us/powershell/module/microsoft.graph.identity.directorymanagement/update-mgorganizationbrandinglocalization?view=graph-powershell-1.0 |
| 10 | *Customize the sign-in experience for your application with branding themes* (per-app themes — a separate, newer capability; NOT needed for tenant-wide branding but linked because it describes the same custom-text fields) | https://learn.microsoft.com/en-us/entra/fundamentals/how-to-customize-branding-themes-apps |

## 2. Where it lives; roles; licensing

- Blade path in the **Microsoft Entra admin center** (https://entra.microsoft.com): **Entra ID → Custom Branding** (`Identity → User experiences → Company branding` was the old Azure-portal-era path; the current Learn doc says *Entra ID > Custom Branding*).
- The flow is sectioned: **Basics → Layout → Header → Footer → Sign-in form → Review** (each ends with *Review + create* / *Next*; final button is **Create**, later **Edit**). A default sign-in experience cannot be deleted after creation, only reset.
- **Minimum role:** *Organizational Branding Administrator* (also Global Admin works).
- **License required:** Microsoft Entra ID P1 or P2, or Microsoft 365 Business Standard, or SharePoint (Plan 1).

## 3. Localization model (default vs browser languages)

- There is exactly **one default sign-in experience** (the tenant-wide base) plus **optional per-browser-language localizations** ("Add browser language" in the admin center; in Graph the `organizationalBrandingLocalization` objects whose `id` is the RFC-1766 locale, e.g. `en`, `pl`/`pl-PL`).
- A language localization **overrides** the default for users whose browser requests that language; any field not set in the localization falls back to the default (Graph `update` examples show you must write an explicit empty string to *clear* an inherited value).
- Right-to-left languages (Arabic, Hebrew) get automatic layout mirroring.
- Practical consequence for MR: keep **Polish text in the default** experience and add an **`en`** localization with the English text (a separate `pl` localization is redundant when the default is already Polish).

## 4. Sign-in page assets — verified limits (admin-center doc, source 1)

| Element (admin-center section) | Dimensions | Format | Max size | Notes |
|---|---|---|---|---|
| **Favicon** (Basics) | 32 × 32 px | PNG or JPG | **5 KB** | Replaces the Microsoft favicon on the browser tab. Confirmed: favicon upload **exists**. |
| **Background image** (Basics) | 1920 × 1080 px | PNG or JPG | **300 KB** | Scales and crops to the window; may be partially hidden behind the sign-in box. |
| Page background color (Basics) | — | hex color | — | Fallback shown when the image can't load (low bandwidth). |
| Visual template (Layout) | — | `default` (centered lightbox over full-screen background) or `verticalSplit` (background on the left, full-height lightbox on the right — a.k.a. ADFS/partial-screen template) | — | Portal wording: "choose the partial-screen background if your background image is important" (the full-screen template can obscure it). |
| **Header logo** (Header; only if Layout → *Show header*) | 245 × 36 px | PNG or JPG | **10 KB** | Renders in the optional header strip at the top of the page (upper-left). |
| Footer (Layout → *Show footer*) | — | — | — | Two slots: **Privacy & Cookies** and **Terms of Use**, each with display text + URL; shown by default (Microsoft's own links). Custom URLs **render as plain text and are not clickable**. |
| **Banner logo** (Sign-in form) | 245 × 36 px | PNG or JPG | **50 KB** | Short/wide logo at the top of the sign-in box. Yes — the banner/rectangle logo slot still exists. |
| **Square logo — light theme** (Sign-in form) | 240 × 240 px | PNG or JPG | **50 KB** | Used on the Entra web interface and in Windows. |
| **Square logo — dark theme** (Sign-in form) | 240 × 240 px | PNG or JPG | **50 KB** | "If your logo looks good on light and dark backgrounds, there's no need to add a dark theme logo." |

Graph-side limits (source 3) differ for two of these: the /beta `organizationalBrandingProperties` table says `squareLogo`/`squareLogoDark` "not more than 10 KB" and describes `backgroundImage` as "not smaller than 300 KB" (a leftover of the old "not smaller than 300×190 px" wording). The admin-center numbers above (50 KB / max 300 KB) are what the Entra admin-center upload UI enforces and what the current UX page states, so those are the caps used for this package; the build also tries to keep the square logos ≤ 10 KB to satisfy the stricter Graph wording where practical.

## 5. Text fields — verified limits

| Field (admin-center section) | Limit | Rules found in docs |
|---|---|---|
| **Sign-in page text** (Sign-in form; renders at the bottom of the sign-in box/below the form) | **1024 characters** | Unicode. New paragraph = Enter twice. Formatting syntax: link `[text](link)`, bold `**text**` or `__text__`, italics `*text*`/`_text_`, underline `++text++`. "This page is public, so don't provide sensitive information." Hyperlinks render as plain text in native (desktop/mobile) environments and all custom URLs display unclickable. |
| **Username hint text** (Sign-in form) | **64 characters** | Unicode, "without links or code". Docs: *"If guests use the same sign-in page, we don't recommend using hint text here."* |
| Footer links *Privacy & Cookies* / *Terms of Use* display text (Footer) | **256 characters** each (Graph: `customPrivacyAndCookiesText`, `customTermsOfUseText`) | Custom URLs shown as text, not clickable; URLs themselves ≤ 128 chars (Graph). Keep Microsoft defaults unless MR has real policy URLs. |
| SSPR texts (`customAccountResetCredentialsUrl` 128; `customCannotAccessYourAccountText` 256; `customForgotMyPasswordText` ≤256; `customResetItNowText` ≤256 marked **DO NOT USE** in Graph docs) | 128–256 chars | SSPR section: show/hide SSPR, common URL, username-collection and password-collection display texts. |
| `signInPageText` in Graph | 1024 chars | Matches the admin center. |

**Header/footer mapping (differs from the brief's wording — important):**
the current sign-in model has **no free-text "header" field**. The *Header* is an optional strip containing a **logo image** (245×36) plus the tenant display name; the in-box "title" is the tenant name rendered automatically below the banner logo. Free text lives in exactly two places: **Sign-in page text** (bottom of the form — the closest equivalent of a "footer caption", used here) and the two **footer link** slots (Privacy & Cookies / Terms of Use display text + URL). Any "Panel MR" caption therefore belongs in the Sign-in page text field (or could only be faked via CSS content injection, which the docs do not offer — see §6).
The favicon, page title, and tenant name are automatic; there is nothing else to fill.

## 6. Custom CSS — verified rules (source 2 + 3)

- **Feature/size cap:** one `.css` file per branding object (default and each localization), **"allowed format is .css format only and not larger than 25 KB"** (Graph `customCSS`). Replaces the Microsoft default style of the page; a template can be downloaded from the admin center.
- **Scope:** applies to the sign-in page (Graph: "CSS styling that appears on the sign-in page"). The current Learn docs no longer make any claim about SSPR/combined-registration pages; company *branding* (background/logos/text) itself does appear on tenant-specific SSPR URLs (e.g. `https://passwordreset.microsoftonline.com/?whr=<domain>`), but no current doc promises the CSS follows there — assume sign-in page only and verify visually in the tenant.
- **Allowed selectors (enumerated in the CSS template reference):**
  - Page-wide defaults: `body`; `a, a:link`; `a:hover`; `a:focus`; `a:focus:hover`; `a:active`
  - `.ext-background-image` (background container, lightbox template)
  - `.ext-header`, `.ext-header-logo`
  - `.ext-middle` (full-screen template centering)
  - `.ext-vertical-split-main-section`, `.ext-vertical-split-background-image-container` (vertical-split/ADFS template)
  - `.ext-sign-in-box`, `.ext-title`, `.ext-subtitle`
  - `.ext-link` (internal navigation links — the recommended way to style in-page links)
  - `.ext-button.ext-primary` (+ `:hover`, `:focus`, `:focus:hover`, `:active`)
  - `.ext-button.ext-secondary` (+ same states)
  - `.ext-error`
  - `.ext-input.ext-text-box` (+ `.ext-has-error`, `:hover`, `:focus`, `:focus:hover`)
  - `.ext-boilerplate-text` (the custom "Sign-in page text")
  - `.ext-promoted-fed-cred-box` (sign-in options box)
  - `.ext-footer`, `.ext-footer-links`, `.ext-footer-item`, `.ext-debug-item`
- **Documented restrictions — the deprecated *layout and positioning* properties** (58 of them). These must not be used:
  `position` (incl. `top/right/bottom/left/z-index`), all `margin` shorthands/hands (`margin-*`, `margin-block(-start/-end)`, `margin-inline(-start/-end)`), `transform`, `-webkit-transform`, `-moz-transform`, `-ms-transform`, `-o-transform`, `translate`, `scale`, `rotate`, `perspective`, `opacity`, `overflow` (and `-x/-y/-block/-inline`), `filter`, `pointer-events`, `clip-path`, `clip`, `display`, `visibility`, `inset`, `zoom`, `offset`, `offset-path`, `offset-distance`, `order`, `grid-area`, `grid-column(-start/-end)`, `grid-row(-start/-end)`, `isolation`, `content-visibility`, `mix-blend-mode`, `mask`, `mask-image`, `-webkit-mask`, `-webkit-mask-image`.
  → Safe non-deprecated properties used in this package: `color`, `background-color`, `border`, `border-color`, `border-radius`, `box-shadow`, `font-family`, `font-weight`, `text-decoration`. (`content` / `::before`/`::after` are **not offered** by the docs — no content insertion; the caption goes into the Sign-in page text field instead, as the brief anticipated.)
- **Per locale:** `customCSS` is a property of every branding object — default **and** each browser-language localization get their own CSS (the deprecation-checker workflow in source 2 exports all localizations and checks each locale's CSS).
- **Feature retirement timeline (source 2, dated 2026-11-21 read):**
  1. Tenants **created after 2026-01-05** never got custom CSS.
  2. **After 2026-07-21**, tenants created before 2026-01-06 **that don't already use custom CSS can no longer configure it** (this date has already passed — see §8).
  3. Later: the deprecated layout/positioning properties will be blocked globally even for existing CSS.
  4. Eventually: custom CSS retires entirely ("first step toward retiring the custom CSS feature entirely").
  External ID tenants are not affected. Microsoft links this to the Secure Future Initiative / phishing resistance (branded sign-ins can be abused to make phishing pages look legitimate).

## 7. Which page actually shows this branding (login.microsoftonline.com)

- Tenant branding appears on **tenant-specific sign-in pages**: with a domain hint (`?whr=mlodzirazem.org`), tenant-name URLs (`https://outlook.com/<tenant>`, `https://<tenant>.sharepoint.com`, `https://myapps.microsoft.com/?whr=<tenant>`), or after Home Realm Discovery — i.e. on the **common** `https://login.microsoftonline.com` page the default Microsoft look shows **until the user types their email/phone and selects Next**, then the tenant branding takes over.
- B2B guests see **their own home tenant's** branding when signing in cross-tenant; users signing in with personal Microsoft accounts never see the tenant branding.
- Sign-in page branding does not carry over into apps themselves (the app's own UI is unchanged).

## 8. Consequences for THIS package (today is 2026-11-21)

1. All image/text assets are fully usable — verify each against the caps in §4/§5.
2. **Custom CSS is the fragile part**: if the MR tenant does not already use custom CSS and was created before 2026-01-06, the 2026-07-21 cutoff means the Layout → Custom CSS option will no longer be available to it. Check the Layout section in the admin center before investing in STEP 4. The `custom.css` in this package is still provided because (a) it may be applicable to this tenant, (b) it costs nothing to keep, and (c) it is written strictly within the current allowed selectors/properties so it is valid wherever the feature is still open. The README marks it optional and explains the check.
3. Because CSS support is winding down, everything essential (identity, readability, framing) is delivered through the doc-supported **assets + text fields + template choice**: the Kongres background, the badge logos, the page background color, the sign-in page text. `custom.css` only adds plum polish (links, buttons, focus rings) — no required content depends on it.
4. GDPR: the Kongres 2025 background photo contains identifiable people. It stays on this machine and is only to be uploaded to MR's own Entra tenant by the tenant owner (their data, their controller). No names, no metadata (build assets are exported without EXIF), and the generated binaries are kept out of git via `.gitignore`.

## 9. Graph API / PowerShell confirmation (for the README script)

- Branding is managed through `organization/{orgId}/branding` (beta; the branding how-to states Microsoft Graph manages company branding on the **/beta** endpoint; v1.0 cmdlet signatures already expose most fields as well).
- Default object: `Update/New-MgOrganizationBranding(-Beta)`; localizations: `New/Update-MgBetaOrganizationBrandingLocalization` with `-OrganizationId` + `-OrganizationalBrandingLocalizationId <locale>`; binary fields use `…-InputFile` parameters: `-BackgroundImageInputFile`, `-BannerLogoInputFile`, `-SquareLogoInputFile`, `-SquareLogoDarkInputFile`, `-FaviconInputFile`, `-HeaderLogoInputFile`, `-CustomCssInputFile`; text fields: `-SignInPageText`, `-UsernameHintText`, `-BackgroundColor`, `-HeaderBackgroundColor`, `-CustomPrivacyAndCookiesText/Url`, `-CustomTermsOfUseText/Url`, `-LoginPageLayoutConfiguration @{layoutTemplateType='…'; isHeaderShown=$true; isFooterShown=$true}`, `-LoginPageTextVisibilitySettings @{…hide…}`.
- Sufficient delegated scope: `Organization.ReadWrite.All`.
- Structure/naming verified via source 9; the exact tenant operations are left to the tenant owner (this agent performs no tenant calls).

## 10. Revision 2 (2026-11-21) — "look like the Panel MR login" (user review follow-up)

**Review finding:** assets and texts accepted; the first `custom.css` judged too
generic ("średni"). **New requirement:** the Entra sign-in page must look very
similar to the Panel MR browser login page. This section records what changed,
the design tokens mirrored, and where full fidelity is impossible.

### What changed

1. **New background asset (default): `build/background-plum-gradient-1920x1080.png`**
   — a faithful replica of the Panel login `.brand` panel
   (`app/(public)/login/LoginPage.module.scss`):
   `radial-gradient(circle at 20% 15%, var(--plum-500) 0%, transparent 55%)` over
   `linear-gradient(145deg, var(--plum-700) 0%, var(--plum-900) 70%, var(--plum-950) 100%)`,
   full bleed, no text/logo/noise. Generated by `scripts/generate-assets.mjs` from an
   SVG definition (CSS gradient math transposed to the 1920×1080 canvas):
   - 145° axis (screen coords, y down) = centre (960,540) + unit (sin145°, −cos145°)
     = (0.5736, 0.8192); half-length 960·0.5736 + 540·0.8192 = 993 → line ends
     (391,−273) → (1529,1353); stops 0 / 0.7 / 1 = `#720546` / `#3d0a27` / `#2a0619`.
   - radial: centre (384,162) (=20%/15%); CSS `circle` default size = farthest corner
     ≈ 1789 px; "transparent at 55%" → r ≈ 984 px.
   - **Interpolation fidelity note:** CSS `transparent` = `rgba(0,0,0,0)` but browsers
     interpolate premultiplied, so the hue stays `#a8216f` while alpha falls.
     librsvg/`sharp` (the renderer used here) lerps non-premultiplied, so the SVG
     fades to *plum*-transparent to reproduce the browser appearance exactly
     (verified: highlight centre = exactly (168,33,111); top-left = (145,21,94)
     matching the browser-premultiplied prediction; bottom-right = exactly (42,6,25)).
   - Exported **lossless PNG, 251.3 KB ≤ 300 KB** (no banding). The script falls back
     to high-quality JPEG only if the PNG ever exceeds the cap.
   - The Kongres photo background stays in `build/` as option A (warmth, GDPR §8);
     the gradient is the default (option B — fidelity).
2. **`custom.css` fully reworked** to mirror the Panel **form side** on the Entra
   sign-in box, with every rule citing its Panel token (sources: `LoginPage.module.scss`,
   `theme.scss`, `components/button/Button.module.scss`, `components/form/Form.module.scss`):
   - `body`: system Sans stack (for the non-web-safe `--font-normal`), text `#18131a`
     (`--color-text`), line-height 1.5, background `#f8f7f9` (`--color-background-deep`, `.formSide`).
   - `.ext-vertical-split-main-section`: `#f8f7f9` = the `.formSide` tone on the
     split template (recommended layout: `verticalSplit`).
   - `.ext-sign-in-box` ≙ `.card`: `#ffffff` surface, 1px `#e8e4ea` border,
     `--radius-lg` 16px, `--shadow-2` `0 4px 12px rgba(36,29,39,.08), 0 1px 3px rgba(36,29,39,.06)`.
   - `.ext-title` ≙ `.cardText h1` (1.5rem) + theme `h1` rules: 24px/700/1.25/−0.01em, `#18131a`.
   - `.ext-subtitle` ≙ `.cardText p`: `#6b6270` muted, 15px, 1.5.
   - links: `#720546` (`--plum-700`), underline-none (theme `a`), hover/active `#540838`
     (`--plum-800`), focus = theme `:focus-visible` recipe (3px
     `rgba(140,13,88,.35)` = `--color-focus` plum-600@35%, offset 2px) on the doc's `a:focus`.
   - `.ext-button.ext-primary` ≙ Button `[data-variant="primary"]`: plum-700 bg,
     white, pill (`--radius-pill` 999px), weight 550,
     shadow `0 2px 8px rgba(114,5,70,.30)` (= plum-700 30%), hover `#540838`.
     `:active` pegged to the hover tone (Panel defines no active jump).
   - `.ext-button.ext-secondary` ≙ `[data-variant="secondary"]`: filled `#241d27`
     (`--gray-900`), white, pill, hover `#3a323d` (`--gray-800`).
   - `.ext-input.ext-text-box` ≙ Form `.input`: white, 1px `#d6d0d8`
     (`--color-border-strong`), `--radius-sm` 8px; hover `#b3abb6` (`--gray-400`);
     focus = `#8c0d58` (`--plum-600`) border + 3px focus ring (Panel swaps outline
     for the ring); error state = `#c62a3b` + `#fdecee` (`--color-danger(-bg)`).
   - `.ext-boilerplate-text` ≙ `.note`: 13px, muted, centered.
   - No red outside the danger/error tokens; all secondary actions stay plum/gray.
   - Same compliance as before: only doc-enumerated selectors (29 rule blocks),
     zero of the 58 deprecated properties, no `::before/::after`, system fonts,
     11,856 bytes ≤ 25 KB, per-locale awareness unchanged.
3. **README.md updated**: gradient is the default background (photo = alternative
   with one-slot swap), page background color per option (`#3d0a27` gradient /
   `#720546` photo), recommended **Show header = off** (Panel login has no top
   strip; header logo remains optional), custom-css description rewritten,
   Graph script lines updated (background file, `$layout` `isHeaderShown=$false`,
   fallback color; commented `HeaderLogoInputFile` add-back), CSS availability
   caveat (§C) unchanged.
4. `texts.md` deliberately untouched (accepted).

### Where full fidelity is impossible (and what was done instead)

- **Brand-side composition:** Panel's `.brand` shows the white logo + pitch text on
  the gradient; Entra exposes no text-on-background and content insertion is not an
  offered capability → the gradient asset carries only the paint, the identity mark
  rides the logo slots (banner logo in the card; header logo only if the header strip
  is enabled).
- **Element order/composition/spacing:** layout properties are exactly what Microsoft
  deprecated (anti-phishing), and Entra's own box spacing is already calm and generous →
  no padding/size/order overrides; the design leans on color, radius, shadow, typography.
- **`:focus-visible`:** Panel's focus recipe uses `:focus-visible`; the docs enumerate
  only `:focus` states → same recipe applied on the allowed selectors (outline also
  shows on mouse focus; acceptable compromise, documented in `custom.css`).
- **Exact card padding/width** (`.card` max-width/24rem, 2rem padding) cannot be
  replicated without sizing overrides → left to Entra's fixed layout on purpose.
- **The gradient asset vs the browser's own CSS:** Entra renders a static image, so
  the brand panel does not re-flow with the actual login's box geometry; the recipe
  was transposed at the standard 1920×1080 instead.

## 11. Revision 3 (2026-11-21) — visible banner; "can't we just use CSS?"

**User review:** the 245×36 badge banner was "really invisible"; question raised
whether custom buttons/fonts/icons could be added via the CSS ("it's literally
custom CSS - CSS").

### Banner fix

`build/banner-logo-245x36.png` + `build/header-logo-245x36.png` were rebuilt in
`scripts/generate-assets.mjs`: the "mr" **letterform monogram** is extracted from
the favicon SVG (background path dropped, remaining paths flattened to a single
`#720546` ink), trimmed to ink and scaled to fill the full 36 px slot height
(ink ≈ 71×36, centred, no padding — "We recommend using a transparent image with
no padding around the logo", Graph docs). This matches the repo's own icon
language (`teams/outline.svg` = white "mr" on transparent). Measured ink
coverage/colour verified programmatically. Increment cost: 3.0 KB per file
(banner ≤ 50 KB, header ≤ 10 KB caps hold).
Rejected alternatives: the `assets/mr-logo.svg` wordmark (two-line stacked
lockup, ink aspect ≈ 2:1 → each line ~14 px at the 36 px slot ceiling — even
less legible) and badge+wordmark composites (a new lockup = inventing
composition, out of scope).
Hard platform fact: the banner/header **slot itself is 245×36** — no artwork
can render taller than 36 px there; the image file is the only lever
(CSS cannot resize the banner element; none of the enumerated selectors
addresses the in-card banner image anyway).

### "Why not just custom CSS everything?" — verified cap frame (re §6)

Entra company-branding CSS is a **constrained theming surface, not a CSS
sheet**: (1) selector whitelist — `body`, link states, and the enumerated
`.ext-*` elements only; no other node on the page is targetable, so no new
"custom buttons" and no restyling of arbitrary Microsoft UI; (2) property
blacklist — the 58 deprecated layout/positioning properties are blocked
(position/margin/transform/opacity/display/filter/mask/pointer-events/…),
leaving paint-level properties; (3) **no content** — `::before`/`::after`/`content`
are not part of the offered selector set, so icons/logos/text cannot be
injected; (4) **no external resources** — font-face/`@import` aren't offered,
so General Sans cannot be loaded and the system stack is the only option
(fonts an OS ships by default; the old Azure AD preview used a short local
font list, current docs keep fonts un-documented); (5) the feature is
**retiring** (§6/§8: 2026 cutover already passed; layout/positioning blocks
follow; full retirement announced) — announced rationale is phishing
resistance: a real CSS sheet on a branded login page is exactly what
attackers would use to forge convincing fake login pages. Identity therefore
belongs in the asset/text slots (as this package does); CSS only tints,
rounds, shadows and re-types the elements Microsoft already renders.

No changes to texts, square logos, favicon, backgrounds or custom.css in this
revision (custom.css already styles Microsoft's own primary/secondary buttons,
inputs and links to the Panel tokens).
