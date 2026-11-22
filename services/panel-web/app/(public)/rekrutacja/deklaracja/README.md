# Rekrutacja: publiczna e-deklaracja kandydata (M4b, front)

Anonimowa, publiczna trasa `​/rekrutacja/deklaracja/[token]` — kandydat wchodzi z magic-linka
wysłanego przez ZO (back-office M2b generuje linki; ekranu ZO tu nie ma). Nie loguje się nigdzie;
token w ścieżce jest jedynym „zaimkiem" dostępu (ladder R0: podpisu kwalifikowanego składa
poza panelem — mObywatel / podpis.mobywatel.gov.pl / eDO — i wgrywa podpisany PDF).

Ścieżka leży w grupie `(public)`, obok `/login` — **w panel-web nie istnieje żadne
middleware**, a cała autoryzacja dzieje się w layoucie `(authorized)`, więc ten widok NIE
wymagał żadnych zmian w auth poza dodaniem plików w tej lokalizacji.

## Przebieg (wizard)

1. **Landing** — uniwersalne powitanie „Witaj!" (BEZ imienia — dane echo za bearer tokenem
   to leak; decyzja RO z odbioru designu, finding F2/H4), ważność linku (`expiresAt`), CTA.
   **Jeden ekran terminalny dla każdego martwego linku** (decyzja RO, F1/F8/H4): 404,
   `TOKEN_EXPIRED` i 409 są rozróżniane WYŁĄCZNIE w warstwie BFF/diagnostyce (kody wracają
   w odpowiedziach mojego proxy zgodnie z kontraktem back-office), a prezentacja spłaszcza
   wszystkie stany do identycznego ekranu i jednej kopii — po to, by sam UI nie potwierdzał
   istnienia sprawy (anti-enumeration).
2. **Formularz** — sekcje jak na papierze: A.1/A.2 dane urzędowe (wymagane), A.3/A.4
   preferowane (opcjonalne), A.5 PESEL (wymagany; FE: checksum + wyliczona data urodzenia
   + pole potwierdzenia daty), A.6 zaimki, B.7 telefon (opcjonalny), przysięga (renderowana),
   zgody (wizerunek — opcjonalna; kategoria szczególna — wymagana, z osobnym wyjaśnieniem
   art. 9 ust. 2 lit. d RODO), pełny obowiązek informacyjny w `<details>`.
3. **Podsumowanie + PDF + upload** — podgląd PDF (BFF‑proxy w iframe; „otwórz w nowej karcie"
   dla telefonów z Safari, które nie renderują PDF w iframe), instrukcja podpisu, drag&drop
   (.pdf, limit 10 MB) → walidacja podpisu po stronie serwera; `SIGNATURE_INVALID` → komunikat
   i powrót do uploadu; sukces → ekran „Deklaracja złożona" (bez zegarów wewnętrznych, bez PII).

## Kontrakt API (M4a — piszemy pod niego; backend startuje równolegle)

Klient: `panel-api/recruitment/declarationForm.ts` (BEZ auth — publiczne endpointy, cechowo
odrębne od `FetchPanelApiClient`, który zawsze mintuje service token):

| Endpoint panel-api | Użycie tutaj |
|---|---|
| `GET  /public/declaration-form/{token}` | RSC strony: status `CREATED → wizard od landingu`, `FILLED → krok uploadu`, `SIGNED → ekran końcowy`; błędy 404/`TOKEN_EXPIRED`/409 → ekrany terminalne |
| `POST /public/declaration-form/{token}` | server action `submitDeclarationFormAction` (`app/actions/recruitment.ts`); 422 `{code, field}` mapowane na komunikaty + pola |
| `GET  /public/declaration-form/{token}/pdf` | proxy `app/api/recruitment/declaration-form/[token]/pdf/route.ts` (stream, `application/pdf`, `no-store`) |
| `POST /public/declaration-form/{token}/declaration` (multipart `file`) | proxy `app/api/recruitment/declaration-form/[token]/declaration/route.ts` (dlaczego route handler, nie akcja: server actions mają domyślny limit ciała 1 MB, a pliki podpisane do 10 MB) |

Kody błędów traktowane jako wykaz otwarty — konsumenci mapują na uzasadniony default.

## Mock i prawdziwy backend

Backend jeszcze nie stoi, więc wywołania idą przez przełącznik:

```bash
# demo/dev bez backendu (mock w pamięci procesu; patrz mockDeclarationFormApi.ts):
PANEL_RECRUITMENT_DECLARATION_MOCK=1 npm run dev
# prawdziwy backend M4a:
PANEL_API_URL=https://panel-api.…   # (bez mock-switcha)
```

Mock jest deterministyczny względem **całego tokenu** (stan trzyma w pamięci procesu;
to wyłącznie blank-formy, zero PII):
`…invalid…` → 404, `…expired…` → `TOKEN_EXPIRED`, `…closed…` → 409, dowolny inny → CREATED
z greetingiem „Anna K." i ważnością +48 h; po poprawnym submicie → FILLED; upload z nazwą
pliku zawierającą „invalid" → `SIGNATURE_INVALID`, inny → SIGNED. PDF z mocka to
procedurally wygenerowany, poprawny plik PDF (podgląd działa w przeglądarce).

## RODO / tokeny

- Tokenów nie logujemy (żadnych `console.log` wartości), nie trafiają do logów, telemetrii
  ani analityki.
- Wszystkie fetche kontraktowe biegają pod `context.suppressTracing()` — token z pathu
  nie trafia do spanów OpenTelemetry (instrumentacja fetch jest włączona globalnie
  w `instrumentation.ts`).
- Strona ma `robots: noindex, nofollow` i `Cache-Control: no-store` na proxy PDF.
- Ekrany terminalne i końcowy nie wyświetlają żadnych danych osobowych.

## Dostępność i mobile-first

Cały flow projektowany pod telefon: jedna kolumna kart, `100dvh`, wskaźnik postępu
(`ol` + `aria-current="step"`), breakpointy scalone do konwencji 900px.

- **Dotyk (odbiór designu, C3/C4):** własne przyciski modułu ≥44px (`.linkButton`, `.dropzoneButton`,
  `min-height: 2.75rem`); inputy ≥16px przy ≤900px (anty-auto-zoom iOS) — nadklasa `.formCard`
  w module (plik `Form.module.scss` jest wspólny, patch J6); `prefers-reduced-motion` wyłącza
  transition dropzone.
- **Bindowanie błędów (B2):** checkboxy wymagane mają `id` błędu w `aria-describedby`
  (hint+error), `aria-invalid`, `required` + `aria-required`; inputy tekstowe wymagane —
  `required` + `aria-required` (form z `noValidate`, walidacja własna).
- **Kontrast/focus (A4/A7):** tekst sukcesu w `var(--success-strong, #14663A)`; ring focusowego
  moich przycisków w `var(--color-focus-strong, #93275D)`; wzorzec `var(--token, fallback)`
  — tokeny wspólne wylądują w theme przy merge gałęzi designu (patch J6), fallback trzyma
  koherencję do tego czasu.
Known limitation: iOS Safari nie renderuje PDFów w iframe — dlatego równolegle
do podglądu działa przycisk „Otwórz PDF w nowej karcie".
