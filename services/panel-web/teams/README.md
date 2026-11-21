# MR Panel – Microsoft Teams app package

This folder holds the Microsoft Teams app package for MR Panel. It is a personal app with five static tabs (Strona główna, Moje członkostwo, Okręg, Głosowania, Informacje). Each tab loads a `/teams/*` route of panel-web inside Teams, and users sign in with Teams SSO through the Entra app `6b106e9e-8d52-48a3-b09e-52985110f6c6`.

| File | Purpose |
|---|---|
| `manifest.template.json` | Teams manifest (schema 1.19) with a `{{HOST}}` placeholder |
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

## Entra app registration

- Set `api.requestedAccessTokenVersion` to `2` in the app registration manifest. Panel's Teams SSO check (`auth/teamsSso.ts`) accepts only v2 tokens.
- The Application ID URI must be `api://panel.mlodzirazem.org/6b106e9e-8d52-48a3-b09e-52985110f6c6` with the `access_as_user` scope. The Teams clients must be pre-authorised for that scope.

`webApplicationInfo.resource` is the same for every host. Teams normally refuses SSO when the resource domain differs from the tab's host ("App resource defined in manifest and iframe origin do not match"). On a dev tunnel host, `getAuthToken` may therefore fail. In that case the tunnel needs its own `api://<tunnel host>/<client id>` URI on the app registration, and the template's resource must be changed to match.

## Icons

The "mr" letterforms are outlines of the bold "m" and "r" in "razem" from `assets/mr-logo.svg`, so the SVGs do not depend on any installed font. If you change an SVG, re-render its PNG at exactly 192×192 or 32×32. The outline icon may contain only white pixels with varying transparency. Check the size with `sips -g pixelWidth -g pixelHeight teams/*.png`. Do not use macOS Quick Look (`qlmanage`) for this, because it renders these SVGs at the wrong scale.
