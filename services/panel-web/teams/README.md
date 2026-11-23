# MR Panel – Microsoft Teams app package

This folder holds the Microsoft Teams app package for MR Panel. It is a personal app with five static tabs (Strona główna, Moje członkostwo, Okręg, Głosowania, Informacje). Each tab loads a `/teams/*` route of panel-web inside Teams, and users sign in with Teams SSO through the Entra app `6b106e9e-8d52-48a3-b09e-52985110f6c6`.

| File | Purpose |
|---|---|
| `manifest.template.json` | Teams manifest (schema 1.19) with `{{HOST}}`, `{{APP_ID}}` and `{{NAME_SUFFIX}}` placeholders |
| `color.png` / `color.svg` | 192×192 colour icon: plum `#720546` badge with white "mr" |
| `outline.png` / `outline.svg` | 32×32 outline icon: white "mr" on a transparent background |
| `package.mjs` | Fills in the host and zips the package (no dependencies) |

## Build

Run the script from `services/panel-web`. It needs Node and the system `zip` command.

Production (`panel.mlodzirazem.org`):

```bash
node teams/package.mjs
```

Dev tunnel or another host. Pass the bare host name, with no scheme and no path:

```bash
HOST=abc123-3000.euw.devtunnels.ms node teams/package.mjs
```

The script writes `teams/build/manifest.json`, copies the icons and prints the path of `teams/build/panel-mr-<host>.zip`. Git ignores `teams/build/`. Upload the zip in the Teams admin centre (Manage apps → Upload new app) or with "Upload a custom app" in Teams. Bump `version` in the template before each re-upload of a changed package.

## Dev package (stage, `dev.panel.mlodzirazem.org`)

```bash
HOST=dev.panel.mlodzirazem.org node teams/package.mjs
```

The dev package has its own Teams app id and the name suffix „(dev)”, so it coexists with the production app and does not replace it in the org catalog. Every host other than `panel.mlodzirazem.org` gets this dev id.

Steps in the tenant (owner):

1. Entra app „Panel MR” → Manifest → add a second entry to `identifierUris`: `api://dev.panel.mlodzirazem.org/6b106e9e-8d52-48a3-b09e-52985110f6c6` (the Expose an API page edits only the first URI). The `access_as_user` scope and the pre-authorised Teams clients are shared across all URIs.
2. Teams Admin Center → Manage apps → Upload new app: upload `teams/build/panel-mr-dev.panel.mlodzirazem.org.zip`.
3. Pin the app for pilot users with a pilot app setup policy.

`TEAMS_APP=prod|dev` overrides which catalog entry the package updates. The production entry („Panel MR”, version 1.1.0 for `panel.mlodzirazem.org`) was uploaded first and now points at stage through an update built with:

```bash
TEAMS_APP=prod HOST=dev.panel.mlodzirazem.org node teams/package.mjs
```

This writes `teams/build/panel-mr-dev.panel.mlodzirazem.org-prod-app.zip`. Upload it from the app's page in Manage apps (Upload file) as an update. Before production starts, build the default package with a higher `version` and update the same entry again.

## Entra app registration

- Set `api.requestedAccessTokenVersion` to `2` in the app registration manifest. Panel's Teams SSO check (`auth/teamsSso.ts`) accepts only v2 tokens.
- The production Application ID URI must be `api://panel.mlodzirazem.org/6b106e9e-8d52-48a3-b09e-52985110f6c6` with the `access_as_user` scope (the dev URI is added as described above). The Teams clients must be pre-authorised for that scope.

`webApplicationInfo.resource` is `api://<host>/<client id>`, built from the same `HOST`. Teams refuses SSO when the resource domain differs from the tab's host ("App resource defined in manifest and iframe origin do not match"), so every host you package for needs its own Application ID URI on the app registration (the client id stays the same).

## Icons

The "mr" letterforms are outlines of the bold "m" and "r" in "razem" from `assets/mr-logo.svg`, so the SVGs do not depend on any installed font. If you change an SVG, re-render its PNG at exactly 192×192 or 32×32. The outline icon may contain only white pixels with varying transparency. Check the size with `sips -g pixelWidth -g pixelHeight teams/*.png`. Do not use macOS Quick Look (`qlmanage`) for this, because it renders these SVGs at the wrong scale.
