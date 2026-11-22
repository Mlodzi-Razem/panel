# Start – Microsoft Teams app package

This folder holds the Microsoft Teams app package for **Start**, the association's second personal app next to `Panel MR` (`../teams/`). It is a single-tab wrapper around the MR SharePoint home site, `https://mloderazemki.sharepoint.com/`, signed into silently by Teams via the `teamslogon.aspx` bootstrap (see "Why teamslogon" below). Its purpose is to replace the generic SharePoint pin in the Teams app bar with a branded "Start" app pinned directly **above** `Panel MR`. The app itself has no code, no bot and no sign-in page: it renders a SharePoint page, and the manifest's `webApplicationInfo` borrows the first-party SharePoint Online principal so Teams can hand the tab a SharePoint session.

The manifest `id` (`ee5d02ca-08ce-4f42-bcc6-1fd9a42a82d9`) is a fresh Teams app id, deliberately different from Panel MR's, so the two apps coexist and can be ordered independently on the bar.

| File | Purpose |
|---|---|
| `manifest.template.json` | Teams manifest (schema 1.19); the URL is fixed, so it has no placeholders |
| `color.png` / `color.svg` | 192×192 colour icon: plum `#720546` badge with a white house |
| `outline.png` / `outline.svg` | 32×32 outline icon: the same house in white on a transparent background |
| `scripts/generate-assets.mjs` | Renders the PNGs from the SVGs with sharp (no new dependencies) |
| `package.mjs` | Validates the manifest and icon sizes and zips the package (no dependencies) |

## Build

Run the script from `services/panel-web`. It needs Node and the system `zip` command:

```bash
node teams-start/package.mjs
```

The script writes `teams-start/build/manifest.json`, copies the icons and prints the path of `teams-start/build/mr-start.zip`. Git ignores `teams-start/build/`. **Bump `version` in the manifest before each re-upload of a changed package** — Teams treats a re-upload of the same `id` as an update and surfaces the new version.

If you change an SVG, re-render its PNG (from `services/panel-web`, so the local `node_modules` is used):

```bash
node teams-start/scripts/generate-assets.mjs
```

## Icons

The house glyph is one geometric path: a pentagon (apex, two eaves, two wall corners) with a rectangular door cut out by a second subpath and `fill-rule="evenodd"`. So `color.svg` — a plum `#720546` rounded square, rx 40, echoing the Panel MR badge — and `outline.svg` — the same path scaled 192 → 32 — share one geometry, with no fonts involved. Teams needs the colour icon at exactly 192×192 and the outline icon at exactly 32×32. The outline icon may contain only white pixels with varying transparency. Check the size with `sips -g pixelWidth -g pixelHeight teams-start/*.png`. Do not use macOS Quick Look (`qlmanage`) for this, because it renders these SVGs at the wrong scale.

## Why teamslogon — SharePoint SSO inside a Teams tab

The naive manifest for this app pointed `contentUrl` straight at `https://mloderazemki.sharepoint.com/`. That cannot work in a custom app: the session-less iframe is redirected to `https://login.microsoftonline.com/<tenant>/oauth2/authorize?...`, and that endpoint always sends `X-Frame-Options: DENY` — Microsoft never allows the identity stack to render in a frame, and no SharePoint or manifest setting changes it. In an ordinary browser window with warm cookies nothing frames at all so the defect hides; in a private window or a cold session the tab is just blank/stuck. A custom app gets none of the built-in SharePoint app's silent sign-in plumbing, so the session must be bootstrapped explicitly.

The bootstrap is the endpoint Teams itself generates for SPFx and "SharePoint page" tabs: `_layouts/15/teamslogon.aspx`. The tab opens:

```
https://mloderazemki.sharepoint.com/_layouts/15/teamslogon.aspx?SPFX=true&dest=%2F
```

`teamslogon.aspx` lives on the SharePoint host itself (so `validDomains` covers it). Backed by a `webApplicationInfo` naming the first-party SharePoint Online principal — official SPFx packaging guidance [1] —

```json
"webApplicationInfo": {
  "id": "00000003-0000-0ff1-ce00-000000000000",
  "resource": "https://mloderazemki.sharepoint.com"
}
```

— Teams silently obtains a token for the SharePoint resource, teamslogon exchanges it and writes the SharePoint session cookie (`SPOIDCRL`) inside the iframe, then redirects to `dest`; `SPFX=true` also serves the modern page without the normal navigation chrome [1][2]. `dest` is the URL-encoded server-relative page path — `%2F` requests the home-site root; if the root redirect ever misbehaves, swap it in this one manifest place for the explicit home page (e.g. `dest=%2FSitePages%2FHome.aspx`).

Two spellings circulate: `SPFX=true&dest=` (used consistently, in both [1] and the community recipes [3][4][6]) and an `aes=0&url=` variant for which no attestable source could be found — do not use it. The `teamslogon.aspx`/`TeamsLogon.aspx` casing varies across sources (IIS ignores it). `permissions: ["identity"]` is not called out as required for this flow by any source, but Teams' own SSO-tab manifest sample carries it [5] and Panel MR ships it — kept as belt-and-braces.

Sources:
1. Microsoft Learn — *Deployment options for SharePoint Framework solutions for Microsoft Teams* — https://learn.microsoft.com/en-us/sharepoint/dev/spfx/deployment-spfx-teams-solutions — official teamslogon contentUrl pattern + the required `webApplicationInfo` id/resource ("required to ensure any API call in the component will succeed from Microsoft Teams rich clients"; the resource cannot point at another tenant's domain).
2. devjhorst — *Understanding the TeamsLogon page* — https://www.devjhorst.com/2020/04/understanding-the-teamslogon-page.html — `spfx` and `dest` are the two required parameters; the POST sets the `SPOIDCRL` cookie; then the redirect to `dest`; `spfx` strips navigation/header/footer for modern pages.
3. Yannick Reekmans — *Show any SharePoint page as a tab in a Microsoft Teams team* — https://blog.yannickreekmans.be/show-sharepoint-page-in-microsoft-teams/ — the SPO-principal `webApplicationInfo` is "the holy grail"; the built-in Website app with the same URL spins forever on desktop, the custom app with `webApplicationInfo` works.
4. erik365 / ESPC — *Attach a SharePoint Online site as a Microsoft Teams app to the Teams navigation bar* — https://erik365.blog/2021/01/07/attach-a-sharepoint-online-site-as-a-microsoft-teams-app-to-the-microsoft-teams-navigation-bar/ — this exact personal-app-in-app-bar scenario.
5. Microsoft Learn — *Update app manifest for SSO and preview app* — https://learn.microsoft.com/en-us/microsoftteams/platform/tabs/how-to/authentication/tab-sso-manifest.
6. Microsoft Q&A — *Custom Teams App 401 Unauthorized issue* — https://learn.microsoft.com/en-us/answers/questions/5916165/custom-teams-app-401-unauthorized-issue — support confirms teamslogon "establishes the required Teams/SharePoint authentication context before redirecting the user to the destination page".
7. Bob German (Microsoft) — *Building Microsoft Teams apps with SharePoint Pages* — https://bob1german.com/2020/01/06/teams-apps-with-sharepoint1/ and its Teams-engineering update https://bob1german.com/about/updates/update-to-building-microsoft-teams-apps-with-sharepoint-pages/.

## Deployment

### Dev — sideload to preview

In the Teams client: **Apps → Manage your apps → Upload a custom app →** pick `build/mr-start.zip`. The uploading account needs `AllowSideLoading` in its app setup policy (the org's dev-sideload recipe is in `/tmp/teams-admin-debug/report.md` §3.2(a), a local scratch report — `/tmp` is wiped on reboot). Pin it manually (right-click → Pin) directly above Panel MR to preview the bar order.

A re-uploaded sideload is picked up the next time the app is opened — restarting the Teams client guarantees the refresh.

### Prod — publish to the org catalog

The org's established recipe is the Graph upload with the zip as the whole body (delegated `AppCatalog.ReadWrite.All`, signed in with the **Teams Administrator** role; application permissions are not supported for publish):

```powershell
Connect-MgGraph -Scopes "AppCatalog.ReadWrite.All"
Invoke-MgGraphRequest -Method POST `
  -Uri "https://graph.microsoft.com/v1.0/appCatalogs/teamsApps" `
  -InputFilePath "teams-start/build/mr-start.zip" `
  -ContentType "application/zip"
# verify it arrived (distributionMethod eq 'organization' = tenant catalog):
Invoke-MgGraphRequest -Method GET `
  -Uri "https://graph.microsoft.com/v1.0/appCatalogs/teamsApps?`$filter=distributionMethod eq 'organization'"
```

Escape hatch: the **Microsoft 365 admin center → Settings → Integrated apps** flow also uploads custom Teams apps and works around a stuck Teams admin centre. For later changes to the published app, see "Updating the catalog app" below.

### Updating the catalog app

Manifest changes need a new `version` in the template first — the update `PUT` is rejected if the version matches the one already published. Then replace the zip **in place**, keeping the same manifest `id`:

```powershell
Connect-MgGraph -Scopes "AppCatalog.ReadWrite.All"   # Teams Administrator, same connection as the publish
# (1) find our entry — match externalId against the manifest id:
Invoke-MgGraphRequest -Method GET `
  -Uri "https://graph.microsoft.com/v1.0/appCatalogs/teamsApps?`$filter=distributionMethod eq 'organization'"
#     -> note the id of the app whose externalId is ee5d02ca-08ce-4f42-bcc6-1fd9a42a82d9
# (2) update it with the rebuilt zip:
Invoke-MgGraphRequest -Method PUT `
  -Uri "https://graph.microsoft.com/v1.0/appCatalogs/teamsApps/<teamsAppId>" `
  -InputFilePath "teams-start/build/mr-start.zip" `
  -ContentType "application/zip"
```

Propagation: the catalog update is documented to take up to 24 hours until existing installs refresh; restarting the Teams client pulls it sooner. For fast iteration, additionally sideload the same package (Apps → Upload a custom app): the uploader's sideloaded copy of the same manifest `id` shows the new version on the next app open, so a change can be checked without waiting for the catalog wave.

### Prod — pinning (app setup policy)

Pinning is not possible via Graph v1.0 CRUD; use the MicrosoftTeams module (`*-CsTeamsAppSetupPolicy` — the org's recipes are in `/tmp/teams-admin-debug/report.md` §3.2; on this Mac connect with MicrosoftTeams **7.8.0**, the last macOS-connectable version, see the report §1.5). For the org-wide app bar, edit the `Global` policy: **replace the generic SharePoint entry with "Start" and order it directly above "Panel MR"** — the order in `PinnedAppBarApps` is the order on the bar:

```powershell
Connect-MicrosoftTeams
# read the exact entry format the tenant uses and mirror it:
Get-CsTeamsAppSetupPolicy -Identity "Global" | select -Expand PinnedAppBarApps
Set-CsTeamsAppSetupPolicy -Identity "Global" `
  -PinnedAppBarApps @("<top-app-id>", "<startTeamsAppId>", "<panelMrCatalogId>") `
  -AllowUserPinning $true
```

The ids are the catalog/external ids returned by the publish step above. The `Global` edit affects everyone — schedule it at a low-use time. Per-user pilots use the same pattern with their own policy:

```powershell
New-CsTeamsAppSetupPolicy -Identity "Start-Pilot" -PinnedAppBarApps @("<startTeamsAppId>", "<panelMrCatalogId>") -Force
Grant-CsTeamsAppSetupPolicy -PolicyName "Start-Pilot" -Identity "<user@mlodzirazem.org>"
# group grants use the Entra group object id; Rank matters when several apply:
Grant-CsTeamsAppSetupPolicy -PolicyName "Start-Pilot" -Group "<entra-group-object-id>" -Rank 1
```

### Update propagation

- **Sideload re-upload:** the next app open for that user; a restart of the Teams client guarantees it.
- **Org-catalog update** (`PUT /appCatalogs/teamsApps/{id}`, version bumped — same-version `PUT` is rejected): documented up to **24 hours** until existing installs refresh to the new version.
- **App setup policy change (pin order):** up to **24 hours**, usually faster after a client restart.
- Caveat: while the M365 "unified app and agent management" migration (MC796790) lands, availability/installation moves to the unified surfaces (TAC Manage apps / M365 Integrated apps) and **pinning stays in setup policies** — if the tenant has been migrated, publish via those surfaces and keep the setup-policy step above.
