# PATCH-NOTES — token patch warstwy wspólnej styli (brand/a11y/urządzenia)

**Branch:** `feat/panel-ux` (worktree panel-ux, base `0ebbc11`) · **Data:** 2026-11-22 · **Zero commitów.**
**Cel:** zamknięcie findingów klas a11y z [REVIEW-M4B.md](REVIEW-M4B.md) (BLOCKER #1,#3,#4 + SHOULD #7,#8,#9 + bonus) na poziomie współdzielonych plików styli, żeby scalenie M2b/M3b/M4b wymagało zero zmian w ich plikach.
**Mandat:** orkiestrator, rozszerzenie z 2026-11-22 (dot. współdzielonych plików styli w TYM worktree).
**Weryfikacja:** `npm ci` → exit 0; `npm run build` → exit 0 (wszystkie route'y skompilowane, SCSS przeszedł pipeline Next 16.1.6). Zweryfikowano lokalnie, jednak przy merge: powtórzyć `npm run build` + rzut oka na 3 ekrany wizualnie (patrz „Testy po merge").

Pliki patchowane (5): `app/theme/theme.scss`, `components/form/Form.module.scss`, `components/page/Page.module.scss`, `components/ui/ui.module.scss`, `components/members/record/record.module.scss`. Diff: **+115/−24 linii** (w tym ~40 linii komentarzy z uzasadnieniami i kontrastami).

---

## A. Rejestr zmian (per plik)

### A1. `app/theme/theme.scss` (główna część patcha — tokeny + urządzenia + dark)

| # | Co | Dlaczego (kryterium + kontrast przed/po) | SPEC |
|---|---|---|---|
| 1 | Nowa skala **`--brand-950…--brand-50`** (10 tokenów; uwaga: projektowy `prototypes/tokens.css` definiuje podzbiór — theme jest nadzbiorem, bo przejmuje legacy 950/500/200, których SPEC nie rusza): `#2a0619, #4a0725, #680c35, #7a0e3e, #93275d, #a8216f, #d886ac, #efc9de, #f6e3ed, #faf0f6` | kanon makiety ownera + §SPEC §2.1; akcent #7A0E3E: 10.71:1 na bieli, 9.84:1 na #F5F5F7 (przed: plum-700 #720546 — 11.50:1, obie wersje AAA, Δ koloru zgodna z kanonem) | §2.1 |
| 2 | **Aliasy legacy `--plum-*: var(--brand-*)`** (9 aliasów) | zero konieczności zmian w 47 miejscach użycia `--plum-*` (plum-800 ×7, plum-600 ×7, plum-50 ×7, plum-900 ×6, plum-100 ×6, plum-700 ×4, plum-200 ×4, plum-950 ×3, plum-500 ×2 — komponenty, hero Teams, gradienty login i strony deklaracji, stany hover). **Wartości 950/500/200 identyczne ze starymi = zero regresji gradientów;** 900/800/700/600/100/50 świadoma delta do wartości kanonicznych (ΔE niewielkie, cel patcha) | §7.1 |
| 3 | **`--color-focus: var(--brand-600)`** (pełny kolor; był `color-mix(plum-600 35%, transparent)`) |_review-M4B BLOCKER #1 (A7/C2): skomponowany ring nad bielą = **#D7AAC5 = 2.01:1 → łamał WCAG 1.4.11**; po: **#93275D = 7.81:1** (7.17 na #F5F5F7). Globalny `:focus-visible` (3px/offset 2px) bez zmian reguły — naprawa wędruje przez token do: pól Form, Page, ui, shell, menu; `.linkButton`/`.dropzoneButton` (custom outline'y) też dziedziczą pełny kolor | §6.2, §7.2-2 |
| 4 | **`--success-strong: #14663A`** + aliasy `--warning-strong/--danger-strong/--info-strong` | tekst success w `--color-success` #1E8E4E = **4.17:1 na bieli / 3.73:1 na tincie (FAIL AA)**; strong: **7.02 / 6.29**. Alias pozostałych trzech semantyk (identyczne wartości) — kompletny zestaw „strong" na potrzeby przyszłych modułów (M2b/M3b wg §2.3) | §2.3, §7.2-3 |
| 5 | **`--space-7: 1.75rem`** | REVIEW-M4B SHOULD #7 (A8): token nie istniał → `padding: var(--space-7) ...` w M4b dropzone dawał nieważną deklarację (padding pionowy = 0). Teraz zdefiniowany zgodnie ze skalą 4/8 (7 → 28px; sąsiedzi w skali: 6 = 24px, 8 = 32px) | §2.6 |
| 6 | **`@media (max-width: 900px) { :root { --control-height: 2.75rem } }`** | REVIEW-M4B BLOCKER #3 (C3): dotyk 40px → **44px** na ≤900px, globalnie (Button, LinkButton, inputy: `min-height: var(--control-height)`); desktop bez zmian (2.5rem — zero regresji w back-office) | §2.6, §7.2-5 |
| 7 | **Blok `@media (prefers-reduced-motion: reduce)`** (transition/animation `.01ms`, scroll-behavior auto) | REVIEW-M4B SHOULD #8 (A10): brak honorowania systemowej preferencji; teraz wszystkie transitiony (theme, Form, ui, wnioski) gaszone globalnie, w tym `scroll-behavior: smooth` z `html` | §2.7 |
| 8 | **`thead th :focus-visible { outline-color: #ffffff }`** | nowy, potrzebny po (3): akcent #93275D na ciemnym pasie nagłówka (gray-900) daje ≈2.07:1 (<3:1); biel = 16.41:1. Linki w `th` (sortowanie tabel Page) jedynym dotkniętym selektorem | §6.2 |
| 9 | **Komentarz-konwencja breakpointu 900px** | punkt 8 mandatu; media queries nie czytają `var()`, więc token = deklaracja: wszystkie NOWE zapytania `@media (max-width: 900px)`; inwentarz legacy zanotowany w komentarzu (login 800, ui 600, Dashboard 1000, Requests 1100, Shell `$mobile`) — nie ruszane (minimal diff, tam gdzie dziś przechodzi — zostaje) | §2.6, review A11 |
| 10 | **Blok dark `:root[data-teams-theme="dark"]`** (14 tokenów, przeniesiony z `prototypes/tokens.css`) | punkt 9 mandatu (forward; Teams ustawia atrybut); kontrasty w komentarzu: tekst 15.66/14.25, muted 8.72, primary+biel 7.81, akcent-on-dark 8.07, ring-biel 14.25. **W dark `--color-focus: #ffffff`** — konsekwencja (3)+(8). Nie ruszam tintów semantycznych w dark (§7.3 — dark poza review v1) | §2.8 |

### A2. `components/form/Form.module.scss`

| # | Co | Dlaczego | SPEC |
|---|---|---|---|
| 11 | `.status[data-tone="success"]`: `color: var(--color-success)` → `var(--success-strong)` | REVIEW-M4B BLOCKER #4 — drugi punkt renderu tekstu sukcesu; **3.73:1 → 6.29:1** | §2.3 |
| 12 | Nowe `@media (max-width: 900px) { .input { font-size: 1rem } }` | REVIEW-M4B SHOULD #9 (C4): `font: inherit` daje 15px → iOS Safari automatycznie zoomuje kartę przy focus pola. 16px blokuje zoom. Wysokość pól idzie przez `--control-height` (dziedziczy punkt 6) | §2.5b |

### A3. `components/page/Page.module.scss`

| # | Co | Dlaczego | SPEC |
|---|---|---|---|
| 13 | `Notice tone="success"`: color → `var(--success-strong)` | REVIEW-M4B BLOCKER #4 — główny punkt renderu („Deklaracja przyjęta…" M4b); **3.73 → 6.29**. Obramowanie (`color-mix` z base) i tło (tint) zostają — non-text ≥3:1 ✓ | §2.3, §7.2-3 |
| 14 | **Badże → pastel**: success `bg success-bg + success-strong` (było: success solid + biel); warning `bg warning-bg + --color-warning` (było: orange solid + biel); muted `bg gray-100 + gray-700` (było: gray-500 + biel). Danger/info bez zmian (biały na solid = 5.53/12.52 ✓) | REVIEW-M4B/spec §7.2-4: solid+biały padał AA — **success 4.17 → 6.29; warning 3.58 → 4.62; muted 3.69 → ≈7.5**. Pastel = wzorzec SPEC §4.2 i kanon makiety (pigułka „Opłacona"). Uwaga: `ComingSoon` („Wkrótce") zmienia odcień z szarego na jaśniejszy pastel — świadoma, wymagana przez AA | §4.2, §7.2-1/4 |

### A4. `components/ui/ui.module.scss`

| # | Co | Dlaczego | SPEC |
|---|---|---|---|
| 15 | StatChip-like: `[data-tone="success"]` color → `var(--success-strong)`; `[data-tone="warning"]` `orange-bg/orange` → `warning-bg/--color-warning` | to samo co A2/A3 (tekst statystyk „zaliczone"): success **3.73 → 6.29**; warning — orange jako tekst = **3.22 → 4.62** (orange zdegradowany do non-text, §7.2-1) | §2.3 |

### A5. `components/members/record/record.module.scss`

| # | Co | Dlaczego | SPEC |
|---|---|---|---|
| 16 | `.yes`: `--color-success` → `--success-strong` (**4.17 → 7.02 na bieli**); `.no`: `--gray-400` → `--gray-600` (**≈2.15 → 5.82**) | bonus poza mandatem, ta sama klasa problemu (C1): znaczący tekst praw wyborczych („Tak/Nie") poniżej AA. `.yes` = bez tła (czysty tekst), więc poprawka jest tu silniej odczuwalna niż w tintach | §2.3 |

## B. Czego patch świadomie NIE robi (i dlaczego)

1. **Nie rusza plików M2b/M3b/M4b** — deklaracja mandate'u; ich autorzy łatają własne (M4b: dropzoneButton wysokość, aria-required, binding błędów checkboxów — REVIEW-M4B #2/#10).
2. **Nie usuwa aliasów `--plum-*`** i nie zmienia 47 miejsc użycia — aliasy są warstwą przejściową (SPEC §7.1); usunięcie = przyszły cleanup po scaleniu wszystkich modułów.
3. **Nie scala detekowych breakpointów legacy** (login 800px, ui 600px, Dashboard 1000px, Requests 1100px, Shell `$mobile`) — minimal diff + zero regresji; zanotowane w komentarzu tokenowym jako do sprowadzenia przy generalnym sprzątaniu.
4. **Nie publikuje `design/prototypes/tokens.css` do aplikacji** — to źródło projektowe; scalanie tokenów produkcyjnych odbyło się teraz bezpośrednio w `theme.scss` (1:1 wartości). `tokens.css` służy prototypom i pozostaje zasobem specyfikacji.
5. **Nie modyfikuje dark tintów semantycznych / cieni w dark** — SPEC §7.3: dark forward-only, poza review v1; blok jest minimalnym kompletem powierzchni/tekstu/ogranicznika (§2.8).
6. **`Requests.module.scss`, `Dashboard.module.scss`, `Shell.module.scss`, `menu.scss`** — audyt wykonany (użycia `--color-*` jako tekstu: warning-bg+warning=4.62 ✓, danger=5.53 ✓, gray-600=5.35 ✓) — wszystko co dziś przechodzi, zostaje nietknięte (zasada mandate'u).

## C. Dlaczego nic się nie zepsuło (analiza ryzyka)

- **Aliasing zamiast rename'ów**: każdy plik używający `--plum-*`/`--color-primary`/`--color-focus`/`--control-height`/`--space-*` czuje zmianę wyłącznie przez wartość tokena. Ryzyka wizualne ograniczone do: (a) ~6 wartości brand celowo podniesionych do kanonu makiety (Δ nieduża; hero, primary button, linki); (b) badge `success/warning/muted` stają się jaśniejsze (pastel — zamierzone); (c) pola/przyciski na ≤900px rosną o 4px (zamierzone, AA); (d) ring fokusu staje się wyraźnie widoczny (zamierzone, AA).
- **Wartości brzegowe**: sprawdzany scenariusz — ring na przycisku primary (tło #7A0E3E): akcent na maroon dawałby tylko ok. 2.5:1, ale ring ma `outline-offset: 2px`, więc leży na tle aplikacji wokół przycisku (białe/#F5F5F7), a tam kontrast wynosi 7.17:1 ✓. W hero Teams (pas maroon) nie ma elementów focusable — link `reportButton` siedzi poniżej, poza pasem; w banerze M4b przypadek ten liczy po swojej stronie J4.
- **Build potwierdza kompilację SCSS**: `next build` kompiluje wszystkie `.module.scss` — dowolna literówka tokenów/f nawias spowodowałaby błąd builda. Exit 0 = składnia i resolves czyste.
- **Brak zmian zachowania JS/TSX**: patch czysto-stylowy (0 plików TSX), więc wektor ryzyka = wyłącznie CSS → pokryty punktami wyżej.

## D. Testy po merge (dla scalającego — 10 minut)

1. `/login`, `/teams/home`, `/user` — rzut oka: hero/linki (maroon Δ), badge „Wkrótce", focus ring widoczny na Tab (kolor pełny).
2. Tel. ≤900px (devtools): przyciski/pola mają 44px, iOS bez auto-zoom na polu.
3. Ustaw systemowe „Reduce motion" → przejścia gasną.
4. VoiceOver/NVDA: fokus ogłaszany na th (sortowanie) — biała obwódka widoczna na ciemnym pasie.
5. Jeżeli Teams dark trial: `documentElement[data-teams-theme='dark']` → strona ciemnieje bez dalszych zmian (forward — jakość dark do oceny przy implementacji czasu).

## E. Git

`git status`: `?? design/` (dokumenty + PATCH-NOTES) + zmodyfikowane 5 plików SCSS (lista wyżej) — **zero commitów**; head `0ebbc11` bez zmian.
