# Panel MR — Microsoft Entra sign-in page texts (STEP 3)

Polish (default locale) + English (`en` localization). Texts only — to be pasted/edited
into the **Sign-in form** section of company branding by the tenant owner
(see `README.md` for the click path and character-limit rationale; limits verified
against Microsoft Learn on 2026-11-21 — see `research-notes.md` §5).

## What your "header and footer" maps to in the current Entra model

- **There is no free-text header field.** The optional **Header** strip holds a 245×36 **logo
  image** (and the page header/footer bars themselves); the large title inside the sign-in
  form is the **tenant display name**, rendered automatically — it is not edited in the
  branding flow. Recommendation: make sure the tenant display name reads
  *"Stowarzyszenie Młodzi Razem"* (Entra admin centre → tenant overview) and upload
  `build/header-logo-245x36.png` to the Header slot if you enable the header strip.
- The requested **footer caption** therefore goes into the **Sign-in page text** field
  (bottom of the sign-in box) — the only free-text slot users reliably read.
- The page **Footer** (Layout section) itself is just two link slots
  (Privacy & Cookies, Terms of Use): **keep the Microsoft defaults** — any custom URL would
  render as unclickable text, and inventing MR policy URLs isn't part of this package.
  If MR later has real policy pages, replace display text + URL there
  (text ≤ 256 chars, URL ≤ 128 chars).

All contact points below already exist in the Panel MR app copy
(`app/(teams)/teams/home/page.tsx`, `app/(teams)/teams/about/page.tsx`):
no new channels were invented.

---

## 1. Sign-in page text — field: "Sign-in page text" (limit 1024 chars)

Formatting is real here: `**bold**`, `[text](link)`; a paragraph break is **Enter pressed twice**.
Links in this field render as plain text in native (desktop/mobile) clients, so the value
below keeps both URLs/e-mails readable as plain text anyway.

### Default locale (Polish) — 169 characters total (60 + 107 + paragraph separator)

```
**Panel MR** — system członkowy Stowarzyszenia Młodzi Razem.

Pomoc IT: społeczność Viva Engage — engage.cloud.microsoft. Zgłoś incydent: bezpieczenstwo@mlodzirazem.org.
```

Paste as two paragraphs exactly as above (blank line between them = Enter twice).

### Localization language: English (`en`) — 182 characters total (68 + 112 + paragraph separator)

```
**Panel MR** — the membership system of Stowarzyszenie Młodzi Razem.

IT Help: the Viva Engage community — engage.cloud.microsoft. Report an incident: bezpieczenstwo@mlodzirazem.org.
```

Notes
- "This page is public, so don't provide sensitive information" — both texts contain only
  the product name and already-public help channels.
- The incident mailbox keeps its Polish mailbox name in the English text
  (it is a real address, not translatable).
- Character counts are Unicode code points; Polish diacritics count as one character each.

## 2. Username hint text — field: "Username hint text" (limit 64 chars)

**Recommendation: leave this field EMPTY.** Two reasons, both from the docs and the UPN reality:

1. Microsoft: *"If guests use the same sign-in page, we don't recommend using hint text here."*
   Guests (B2B, partner accounts) would see the hint too.
2. MR UPNs are ambiguous for a single hint: the canonical UPN is the six-digit member number
   (`123456@mlodzirazem.org`), but members also sign in with their e-mail-style UPNs in the
   same domain. One hint cannot teach both without misleading someone.

If you decide to use a hint anyway, these fit both UPN styles (they show the domain, not the
local part) and are within the 64-character limit:

- Default (Polish) — 26 chars: `np. 123456@mlodzirazem.org`
- English (`en`) — 27 chars: `e.g. 123456@mlodzirazem.org`

Remember: a value set in the default branding is inherited by every localization, so paste
the same decision into the `en` localization too (or clear it there explicitly).

## 3. Footer link slots (Layout → Footer) — no changes recommended

| Slot | Current (default) | Change |
|---|---|---|
| Privacy & Cookies | Microsoft link shown | **Keep as is** (display text ≤256 chars, URL ≤128 chars *when* replaced; URLs render unclickable) |
| Terms of Use | Microsoft link shown | **Keep as is** (same limits) |

## 4. SSPR texts — not customized in this package

The Sign-in form section also offers self-service-password-reset fields ("Show SSPR",
Common URL, username/password collection display texts — 128/256-char caps per Graph docs).
Leave them default unless/until MR runs SSPR for member accounts; nothing in the
branding above depends on them.

## 5. Verification checklist for the tenant owner

- [ ] Sign-in page text (default) = the Polish block above; count shown in the admin centre
      stays under 1024 (actual: 169).
- [ ] Added `en` browser-language localization with the English block (actual: 182).
- [ ] Username hint left empty (or the optional 26/27-char hints pasted consciously).
- [ ] Footer links untouched; header strip (optional) carries `header-logo-245x36.png`.
- [ ] Tenant display name reads *Stowarzyszenie Młodzi Razem* (it is the sign-in title).
