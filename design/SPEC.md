# SPEC — Panel MR · Design System

**Status:** specyfikacja obowiązująca przy scalaniu modułów M2b (back-office rekrutacji) i M4b (publiczny flow deklaracji kandydata).
**Autor:** junior UX (worktree `panel-ux`, branch `feat/panel-ux`, base `0ebbc11`).
**Data:** 2026-11-22 · **Wersja:** 1.0

Powiązane dokumenty: [IA.md](IA.md) · [STATES.md](STATES.md) · [REVIEW-CHECKLIST.md](REVIEW-CHECKLIST.md) · prototypy w [prototypes/](prototypes/).

Sposób użycia: kod M2b/M4b review'ujemy punkt po punkcie względem [REVIEW-CHECKLIST.md](REVIEW-CHECKLIST.md); wartości tokenów sprawdzamy z tabelami §2–§3 jeden-do-jednego. Prototypy w [prototypes/](prototypes/) są referencją wizualną — `prototypes/tokens.css` jest jedynym źródłem wartości CSS dla prototypów.

---

## 0. Zasady nadrzędne

1. **Jeden system, trzy powierzchnie.** Teamowa aplikacja członkowska (Teams web i mobile), back-office ZO/ZK (przeglądarka desktopowa) i publiczny flow kandydata (mobile, anonimowy) dzielą tę samą paletę, typografię i słownik statusów. Różni je zagęszczenie, nie wygląd.
2. **Tokeny, nie wartości literalne.** Panel-web nie używa Tailwinda — ma własne CSS custom properties w `app/theme/theme.scss`; nowy kod również odwołuje się wyłącznie do tokenów, nigdy nie wpisuje hexa „z ręki”. Nazwy tokenów są stabilne (`--color-*`, `--space-*`, `--radius-*`…) i zgodne z konwencją z `theme.scss`, żeby review był mechaniczny.
3. **WCAG 2.1 AA to minimum, nie cel.** Wszystkie pary tekst/tło użyte w projekcie mają policzone kontrasty (§3); poniżej 4.5:1 dla tekstu i 3:1 dla elementów niebędących tekstem (fokus, ikony informacyjne) nie stosujemy.
4. **Mobile-first.** Najpierw ekran ≤900px dotykowy, potem desktop. Cel dotykowy ≥44px, typografia w rem, siatka 4/8.
5. **RODO determinuje widoczność.** Dane osobowe kandydata widać tylko w back-office z uprawnieniem `RECRUITMENT_READ` w zakresie (ZO: własny okręg; ZK: globalnie). Publiczny flow: minimalna ekspozycja — powitanie bez danych, komunikaty błędów bez potwierdzania istnienia sprawy.
6. **Dane w makietach i przykładach:** wyłącznie persony testowe z pasma 950001–950012 (`dev/seed/test-personas.sql`), np. „Anna Testowa”. Zero realnych danych osobowych; PESEL zawsze zamaskowany (wzór: `••••••123`).
7. **Kanon wizualny makiety „Teams-Panel”.** Ekran aplikacji członkowskiej (artboard Screen 1) jest wiążący dla powierzchni member Teams: hero z greetingiem i logiem po prawej, maroon baner kampanii z białą pigułką, nagłówki kart w CAPS z rozstrzeleniem, pigułki statusowe = pastel + ciemny tekst, karty białe na #F5F5F7, akcent #7A0E3E wyłącznie dla akcji i kampanii. Tam, gdzie makieta pokazuje treść z modułów wchodzących dopiero w fazach 2–3 (głosowania, składki, ogłoszenia), projektujemy stan docelowy, a w kodzie do czasu backendu zostają placeholdery `ComingSoon`.

---

## 1. Przegląd systemu (dla osób, które czytają tylko to)

| Obszar | Decyzja | Uzasadnienie |
|---|---|---|
| Akcent | maroon **#7A0E3E** (`--brand-700`); kontrast na bieli **10.71:1**, na tle #F5F5F7 **9.84:1** | makieta ownera; przechodzi AA i AAA dla tekstu i elementów niebędących tekstem |
| Statusy sprawy | 4 grupy semantyczne (kontakt → okres wstępny → decyzje → terminalne), **NIE** 11 odcieni | 11 statusów maszyny to za dużo kolorów — kolor koduje fazę życia sprawy, tekst nazywa status (§STATES) |
| Badge statusu | pastel (tint) + ciemny tekst; zakaz „solid + biały tekst” dla <18.66px semibold | pastel jest w makiecie ownera (pigułka „Opłacona”); biały na `#1E8E4E` = 4.17:1 (poniżej AA) — patrz §3.4 |
| Tekst success | nowy token `--success-strong: #14663A` (7.02:1) zamiast `#1E8E4E` (4.17:1) dla tekstu | konieczna zmiana względem `theme.scss`; finding dostępnościowy |
| Focus ring | pełnobarwny `--brand-600 #93275D`, 2–3px, offset 2px (7.17:1 na #F5F5F7) | obecny ring w `theme.scss` to kolor rozmyty 35% (≈1.6:1) — łamie WCAG 1.4.11; finding dostępnościowy |
| Typografia | GeneralSans Variable (istnieje w repo, pełne polskie znaki) + sterta systemowa; body 15px/1.5 desktop, 16px mobile | zero nowych zależności; sterta fontów systemowych jako zapas |
| Motion | jeden czas trwania 180ms ease-in-out, używany oszczędnie; pełne poszanowanie `prefers-reduced-motion` | Teams ma własne przejścia — Panel nie dodaje teatru |
| Dark mode | tokeny dark-forward w `[data-teams-theme="dark"]` (Teams sam ustawia atrybut) — §2.8 | znany luk z AGENTS.md („Panel has no dark styles yet”); projektujemy tak, by dorobienie ciemnego motywu było podmianą tokenów |

---

## 2. Tokeny

Semantyka nazewnictwa trzywarstwowa: `--brand-*/--gray-*` (prymitywy — paleta surowa) → `--color-*` (role dla komponentów) → `--status-*/--clock-*` (pochodne specyficzne dla rekrutacji, §2.4). Komponenty używają **wyłącznie** ról i statusów, nigdy prymitywów wprost.

### 2.1 Paleta brand (maroon) — nowa skala

Zastępuje skalę `--plum-*` z `theme.scss`. Mapowanie migracyjne (review: nowy kod używa `--brand-*`/`--color-primary`; stare `--plum-*` zostają jako aliasy do czasu generalnego porządkowania):

| Token | Wartość | Użycie | Na #FFFFFF | Na #F5F5F7 |
|---|---|---|---|---|
| `--brand-900` | `#4A0725` | tekst na tintach brandowych | 15.58 | 14.30 |
| `--brand-800` | `#680C35` | hover/aktywny akcentu; tekst badge'u brandowego | 12.41 | 11.39 |
| `--brand-700` | `#7A0E3E` | **`--color-primary`** — hero, przycisk primary, linki akcentowe | **10.71** | **9.84** |
| `--brand-600` | `#93275D` | focus ring, primary w dark mode | 7.81 | 7.17 |
| `--brand-300` | `#D886AC` | dekoracje w dark mode; **zakaz jako tekst na jasnym** | 2.62 ✗ | — |
| `--brand-100` | `#F6E3ED` | tint badge/hero (`data-tone="brand"`), hover powierzchni | (tło; 8.74 z #7A0E3E) | — |
| `--brand-50` | `#FAF0F6` | `--color-primary-soft` — tło hover linków/kafli | (tło; 9.62 z #7A0E3E) | — |

Stary → nowy: `--plum-700 #720546` → `--brand-700 #7A0E3E`; `--plum-800 #540838` → `--brand-800 #680C35`; `--plum-600 #8C0D58` → `--brand-600 #93275D`; `--plum-100 #F7E3EE` → `--brand-100 #F6E3ED`; `--plum-50 #FCF3F8` → `--brand-50 #FAF0F6`. Różnice są wizualnie małe; logo `assets/mr-logo.svg` dziedziczy `currentColor` — bez zmian.

### 2.2 Skala szarości (bez zmian względem `theme.scss`)

- `--gray-950 #18131A` — tekst główny (18.31:1 na bieli)
- `--gray-900 #241D27` — nagłówki tabel, przycisk secondary (biały na nim: 16.41:1)
- `--gray-800 #3A323D`, `--gray-700 #4F4653`
- `--gray-600 #6B6270` — **`--color-text-muted`** (5.82:1)
- `--gray-500 #8B8290` — 3.69:1: **wyłącznie disabled i dekoracje**, nigdy żywy tekst (§3.2)
- `--gray-400 #B3ABB6`, `--gray-300 #D6D0D8` (obramowanie inputu), `--gray-200 #E8E4EA` (`--color-border`), `--gray-100 #F2EFF3`, `--gray-50 #F8F7F9` (`--color-background-deep`; makieta ownera używa #F5F5F7 — obie wartości przeszły wszystkie pary i są zamienne)

### 2.3 Semantyka (success/warning/danger/info) — trzy odcienie każdej roli

Wzorzec: **strong** (tekst i ikony — zawsze ≥4.5:1 na bieli i na tincie) / **base** (duże powierzchnie, ikony dekoracyjne, paski; jako tekst tylko ≥18.66px semibold) / **tint** (pastelowe tła badge'ów i notice'ów). Obramowanie notice'u: `color-mix(in srgb, [strong] 25%, transparent)`.

| Rola | strong (tekst) | base (powierzchnie) | tint (tło) | strong na bieli | strong na tincie |
|---|---|---|---|---|---|
| `--success-*` | `#14663A` **(nowy)** | `#1E8E4E` | `#E7F6ED` | 7.02 | 6.29 |
| `--warning-*` | `#B45309` | `#B45309` | `#FFF4E5` | 5.02 | 4.62 |
| `--danger-*` | `#C62A3B` | `#C62A3B` | `#FDECEE` | 5.53 | 4.85 |
| `--info-*` | `#1F2F6B` | `#1F2F6B` | `#E8ECF8` | 12.52 | 10.60 |
| `--orange-*` (zdegradowany do dekoracji) | ✗ (3.58) | `#E8590C` | `#FFF0E6` | **zakaz użycia jako tekst** | ✗ |

Reguły użycia semantyki:

- **Notice** (komunikat blokowy): tło = tint, tekst = strong, obramowanie = color-mix(strong 25%). Nie stosować tekstu base na tincie (dla success byłoby to 3.73:1).
- **Badge pastelowy** (wzorzec domyślny): tło = tint, tekst = strong + kropka w kolorze base. Pastel jest spójny z makietą ownera („Opłacona”).
- **Badge solidny** (jednolity kolor): dozwolony tylko gdy kontrast tekstu ≥4.5:1 — spełniają go: info (12.52), warning (5.02), danger (5.53), brand-700/800 (10.71/12.41). **Success solidny: wyłącznie biały tekst na `#2E5E44` (7.50:1)**; rekomendacja: nie robić solid-success w ogóle.
- `--color-orange #E8590C`: zostaje w tokenach wyłącznie jako kolor bazowy ikony „po terminie”. Zakaz tekstu pomarańczowego na bieli/tincie (3.58/3.22 — poniżej AA). Istniejący `Badge tone="warning"` w `Page.module.scss` (tło orange + biały tekst = 3.58:1) — finding do naprawy (§7.2).

### 2.4 Statusy rekrutacji i zegary (tokeny pochodne)

Definicje biznesowe w [STATES.md](STATES.md); warstwa tokenów wystawia pary `--status-{grupa}-fg/bg` i `--clock-{stan}-fg/bg`, żeby komponent nie znał wartości surowych:

```css
--status-contact-fg:   var(--info-strong);    --status-contact-bg:   var(--info-tint);
--status-probation-fg: var(--warning-strong); --status-probation-bg: var(--warning-tint);
--status-decision-fg:  var(--brand-800);     --status-decision-bg:  var(--brand-100);
--status-accepted-fg:  var(--success-strong); --status-accepted-bg:  var(--success-tint);
--status-rejected-fg:  var(--danger-strong);  --status-rejected-bg:  var(--danger-tint);

--clock-ok-fg:   var(--success-strong); --clock-ok-bg:   var(--success-tint);
--clock-soon-fg: var(--warning-strong); --clock-soon-bg: var(--warning-tint);
--clock-over-fg: var(--danger-strong);  --clock-over-bg:  var(--danger-tint);
```

### 2.5 Typografia

Rodzina (zero nowych zależności — fonty już są w repo, `app/theme/font/`):

```css
--font-sans: 'GeneralSans-Variable', system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
--font-mono: 'SourceCodePro-VariableFont', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; /* numery spraw, sygnatury uchwał, PESEL */
```

GeneralSans (wagi 200–700, variable) ma pełny zestaw znaków Latin Extended — polskie znaki (ą ć ę ł ń ó ś ź ż) są w komplecie. Sterta systemowa (SF/Segoe/Roboto) również je posiada. „Typografia PL” w tekstach: polskie cudzysłowy „ ”, pauza/em-dash —, zero glyph-substytucji (dlatego fallback następuje dopiero po rodzinie systemowej, nigdy przed nią).

Skala (mobile-first, wszystkie rozmiary w rem; `clamp` skaluje nagłówki między 380–1440px):

| Token | Wartość | Użycie |
|---|---|---|
| `--text-display` | `clamp(1.625rem, 1.3rem + 1.25vw, 2.25rem)` | hero „Cześć, {imię}” (waga 600) |
| `--text-h1` | `clamp(1.375rem, 1.2rem + 0.75vw, 1.875rem)` | tytuł strony back-office (waga 650) |
| `--text-h2` | `1.0625rem` (waga 600) | tytuł karty |
| `--text-body` | `0.9375rem` (15px desktop; przy ≤900px — `1rem`) | treść; line-height 1.5 |
| `--text-small` | `0.875rem` | metadane, pomocniki; line-height 1.45 |
| `--text-caption` | `0.8125rem` (waga 500–550 dla etykiet) | etykiety pól, Badge, filtry; line-height 1.4 |
| `--text-micro` | `0.75rem` | wyłącznie uppercase overline („SPRAWA NR 128”); rzadko |

Wymogi: (a) wszystko w rem — powiększenie tekstu w przeglądarce do 200% musi działać bez utraty treści (WCAG 1.4.4); (b) inputy/selecty na ekranach dotykowych **≥16px** (blokuje automatyczny zoom iOS); (c) line-height ≥1.4, liternictwo `-0.01em` tylko w nagłówkach; (d) kolor nie jest jedynym nośnikiem informacji — badge ma ikonę/kropkę przy kolorze (§STATES).

### 2.6 Spacing, radius, wysokości, siatka

- Spacing 4/8: `--space-1: .25rem`, `--space-2: .5rem`, `--space-3: .75rem`, `--space-4: 1rem`, `--space-5: 1.25rem`, `--space-6: 1.5rem`, `--space-8: 2rem`, plus nowe `--space-10: 2.5rem` i `--space-12: 3rem`. Wyłącznie wielokrotności 4px.
- Padding karty: `--space-6` (mobile: `--space-4`). Przerwa między sekcjami: `--space-6`. Odstępy wewnątrz pól: `--space-2`–`--space-3`.
- Radius: `--radius-sm: .5rem` (pola), `--radius-md: .75rem` (notice, tabela), `--radius-lg: 1rem` (karty, hero), `--radius-pill: 999px` (badge, przyciski). Maksymalnie 2 promienie na ekran w obrębie jednego komponentu.
- Wysokości: `--control-height: 2.75rem` (**44px** — domyślna, wszędzie na ≤900px i wszędzie tam, gdzie dotyk), `--control-height-sm: 2.5rem` (40px — tylko >900px przy sterowaniu myszą), `--tap-size: 2.75rem` (kwadratowe przyciski ikonowe). Wiersz tabeli: min-height 44px.
- Breakpointy: **jeden** — `900px` (zgodnie z istniejącą konwencją `Page.module.scss`), zapytania pisane mobile-first (`@media (max-width: 900px)` dla korekt).
- Szerokości treści: back-office `--size-content-max-width: 88rem` (istniejące), zakładka Teams 72rem, publiczny flow kandydata: kolumna `28rem` wycentrowana (mobile).

### 2.7 Cienie, z-index, motion

Cienie (z `theme.scss`, bez zmian; zawsze w parze z obramowaniem 1px `--color-border`):

```css
--shadow-1: 0 1px 2px rgba(36,29,39,.06), 0 1px 3px rgba(36,29,39,.06);   /* karty */
--shadow-2: 0 4px 12px rgba(36,29,39,.08), 0 1px 3px rgba(36,29,39,.06);  /* sticky toolbar, wzniesione */
--shadow-3: 0 12px 32px rgba(36,29,39,.16);                               /* overlay dialogu */
```

Z-index — tylko cztery warstwy:
`--z-base: 0` · `--z-sticky: 10` (nagłówek tabeli, pasek filtrów) · `--z-popover: 30` (dropdown filtrów, tooltip) · `--z-overlay: 50` (natywny `<dialog>` i tak trafia do top-layer, patrz `components/dialog/Dialog.tsx`).

Motion — oszczędne, jedna para zmiennych:
`--duration: 180ms`, `--duration-instant: 90ms` (mikrointerakcje), `--ease: ease-in-out`, `--transition: all var(--duration) var(--ease)`.
Dozwolone: zmiany koloru/tła/obramowania, fade-in overlay (180ms), spinner (rotacja). Zakaz: paralaksa, przesuwanie treści, automatyczne karuzele. Obowiązkowo: `@media (prefers-reduced-motion: reduce)` — animacje stają w miejscu (spinner zostawia tekst „Wczytywanie…”), `transition-duration: .01ms !important`.

### 2.8 Dark mode (forward)

Teams ustawia `data-teams-theme="dark"` na `<html>`. Ponieważ komponenty nie znają wartości surowych (§0.2), dorobienie ciemnego motywu sprowadza się do podmiany ~14 prymitywów. Docelowy blok (też w `prototypes/tokens.css`):

```css
:root[data-teams-theme="dark"] {
  --color-text: #F2EAEE;           /* na tle #1C1016 — 15.66:1 */
  --color-text-muted: #C5B7BD;      /* na powierzchni #271921 — 8.72:1 */
  --color-surface: #271921;
  --color-background-deep: #1C1016;
  --color-border: #4A3B44;
  --color-border-strong: #6B5762;
  --color-primary: var(--brand-600);   /* #93275D — biały na nim 7.81:1 */
  --color-primary-soft: #3A1E2C;
  --accent-on-dark: #E4A0BE;           /* linki/ikony — 8.07:1 na powierzchni; zakaz na jasnym tle */
  /* tinty semantyczne: color-mix(in srgb, [strong] 24%, var(--color-surface));
     tekst: strong rozjaśniony o jeden stopień — wartości do policzenia przy implementacji */
}
```

Zasady dark: tekst na powierzchniach ≥7:1 (AAA small); tinty powstają przez `color-mix` z powierzchnią, nie przez jaśniejsze hexy. **Zakres M2b/M4b: stany dark nie wchodzą do review v1** (checklist odnotowuje to jako „świadomie poza zakresem”), ale żaden nowy styl nie może uniemożliwiać podmiany (zakaz inline `rgba(...)` bez tokenu).

---

## 3. Kontrast WCAG — kompletne obliczenia

Metoda: WCAG 2.1 relative luminance + contrast ratio, zaokrąglone do 2 miejsc. AA dla tekstu: 4.5:1 (normalny), 3:1 (≥24px lub ≥18.66px semibold). Elementy niebędące tekstem (focus ring, ikony informacyjne, granice pól): 3:1 (kryterium 1.4.11).

### 3.1 Brand na powierzchniach (pary obowiązkowe)

| Para (tekst/tło) | Ratio | Werdykt |
|---|---|---|
| **#7A0E3E na #FFFFFF** | **10.71** | ✓ AAA (tekst i non-text) |
| **#7A0E3E na #F5F5F7** (tło aplikacji z makiety) | **9.84** | ✓ AAA |
| #7A0E3E na #FAF0F6 (--brand-50) | 9.62 | ✓ AAA |
| #7A0E3E na #F6E3ED (--brand-100) | 8.74 | ✓ AAA |
| #FFFFFF na #7A0E3E (hero, przycisk primary) | 10.71 | ✓ AAA |
| #FFFFFF na #680C35 (hover przycisku) | 12.41 | ✓ AAA |
| #F6E3ED (podtytuł hero) na #7A0E3E | 8.74 | ✓ AAA |
| #4A0725 (--brand-900) na #F6E3ED | 12.70 | ✓ AAA |
| #93275D (focus ring) na #FFFFFF / na #F5F5F7 | 7.81 / 7.17 | ✓ non-text 3:1 z dużym zapasem |
| #680C35 (--brand-800) na #FFFFFF | 12.41 | ✓ AAA |

### 3.2 Tekst systemowy

| Para | Ratio | Werdykt |
|---|---|---|
| #18131A (tekst) na #FFFFFF / na #F5F5F7 | 18.31 / 16.82 | ✓ AAA |
| #6B6270 (muted) na #FFFFFF / na #F8F7F9 / na #F2EFF3 | 5.82 / 5.35 / 5.11 | ✓ AA (świadomie nie AAA) |
| #3A323D (--gray-800) na #FFFFFF | 12.33 | ✓ AAA |
| #FFFFFF na #241D27 (nagłówek tabeli) | 16.41 | ✓ AAA |
| #8B8290 (--gray-500) na #FFFFFF | 3.69 | ✗ — wyłącznie disabled (WCAG zwalnia disabled z kontrastu) i dekoracje |

### 3.3 Formularze (placeholder nie może być mniej czytelny niż etykieta)

| Para | Ratio | Werdykt |
|---|---|---|
| Placeholder #6B6270 na #FFFFFF | 5.82 | ✓ AA — placeholder nigdy w #8B8290 |
| Obramowanie inputu #D6D0D8 na #FFFFFF | 1.63 | ✗ jako non-text — dlatego: pole wymaga etykiety widocznej; obramowanie ma podbijać kontrast w stanach focus (#93275D — 7.17) i błędu (#C62A3B — 5.53); w stanie spoczynku towarzyszy mu pełny kontekst etykiety (kryterium 1.4.11 dotyczy granic rozpoznawczych komponentu — zapewnia je parę obramowanie+etykieta, a stan niedostępny nigdy nie pozostaje jedynym sygnałem) |

### 3.4 Semantyka — pełne pary

| Para (tekst/tło) | Ratio | Werdykt |
|---|---|---|
| #14663A (success-strong) na #FFFFFF / na #F5F5F7 / na #E7F6ED | 7.02 / 6.45 / 6.29 | ✓ AA/AAA |
| **#1E8E4E (stary `--color-success`) na #FFFFFF** | **4.17** | **✗ poniżej AA jako tekst** — finding; jako ikona/base (non-text 3:1) dopuszczalny |
| #B45309 (warning) na #FFFFFF / na #F5F5F7 / na #FFF4E5 | 5.02 / 4.61 / 4.62 | ✓ AA |
| #C62A3B (danger) na #FFFFFF / na #F5F5F7 / na #FDECEE | 5.53 / 5.08 / 4.85 | ✓ AA |
| #1F2F6B (info) na #FFFFFF / na #F5F5F7 / na #E8ECF8 | 12.52 / 11.50 / 10.60 | ✓ AAA |
| #E8590C (orange) na #FFFFFF / na #FFF0E6 | 3.58 / 3.22 | **✗** — wyłącznie dekoracja/non-text; istniejący `Badge tone="warning"` (tło orange, biały tekst) = 3.58 → łamie AA, do naprawy (§7.2) |
| #6B6270 (muted) na tintach success/warning/danger/info | 5.21 / 5.36 / 5.10 / 4.93 | ✓ AA (metryki w badge, np. daty) |
| Biały na solidnych info / warning / danger / brand-800 | 12.52 / 5.02 / 5.53 / 12.41 | ✓ AA (solid-badge dopuszczalny) |
| **Biały na solidnym success #1E8E4E** | **4.17** | **✗** — dlatego pastel jest wzorcem badge'ów |
| #14532D na #DCF0E4 (pigułka „Opłacona” — pastel z makiety) | 7.65 | ✓ AAA; dopuszczalna alternatywa dla --success-tint |
| #14663A na #DCF0E4 | 5.89 | ✓ AA |

### 3.5 Dark (forward — do walidacji przy implementacji)

| Para | Ratio | Werdykt |
|---|---|---|
| #F2EAEE na #1C1016 / na #271921 | 15.66 / 14.25 | ✓ AAA |
| #C5B7BD (muted) na #271921 | 8.72 | ✓ AAA |
| #E4A0BE (akcent) na #271921 | 8.07 | ✓ AAA |
| #FFFFFF na #93275D (primary dark) | 7.81 | ✓ AAA |

---

## 4. Inventory komponentów ze stanami

Legenda stanów: **D** default, **H** hover, **F** focus-visible, **I** active/pressed, **Ds** disabled, **L** loading, **E** empty, **R** error, **RO** tylko odczyt (rola z `RECRUITMENT_READ`, bez `RECRUITMENT_MANAGE`).

Wspólne dla wszystkiego interaktywnego: focus ring §6.2; disabled = `opacity .45` + `cursor: not-allowed` + `aria-disabled`; stan loading nie usuwa etykiety (czytniki ekranu).

### 4.1 Button (istniejący `components/button/Button.tsx`; 5 wariantów)

| Wariant | D | H | F | I | Ds | L |
|---|---|---|---|---|---|---|
| primary | tło `--brand-700`, tekst biały | tło `--brand-800` | ring §6.2 | tło `--brand-900` | wspólny Ds | spinner + tekst bez zmian, `aria-busy="true"` |
| secondary | tło `--gray-900`, tekst biały | tło `--gray-800` | ring | ciemniejszy | Ds | L jw. |
| outline | tło surface, obram. `--gray-300`, tekst `--gray-900` | obram. `--gray-400`, tło `--gray-50` | ring | obram. `--gray-500` | Ds | L |
| danger | tło `--danger`, tekst biały | tło `#A81F2D` (mix z czernią 12%) | ring | — | Ds | L |
| ghost | przezroczyste, tekst `--gray-800` | tło `--gray-100` | ring | — | Ds | L |

Wysokość ≥44px na ≤900px; ikona `aria-hidden`; `aria-label` gdy widoczna jest sama ikona.

### 4.2 Badge statusu + Badge zegarowy (nowy komponent)

- Pastel: tło `--status-{grupa}-bg`, tekst `--status-{grupa}-fg` (waga 550), kropka 6px w kolorze base, radius pill, padding .15/.65rem, rozmiar caption (13px).
- Kolor nie jest jedynym nośnikiem: kropka + pełny tekst statusu, zawsze.
- Badge zegarowy: ikona zegara 16px + tekst („Na czas” / „Zbliża się termin” / „Po terminie (nieblokujący)”); nie ma stanów L/Ds; gdy zegar nie dotyczy statusu (np. ACCEPTED) — ukryty, nie „—”.

### 4.3 Card (istniejący `components/page/Page.tsx`)

D: powierzchnia, obramowanie, radius-lg, shadow-1, padding space-6/4. H: brak — karta nie jest hoverowana w całości, hoverują elementy w środku. E: wariant z EmptyState (§4.10). R: Notice wewnątrz karty. Nagłówek: h2 + opcjonalne akcje z prawej.

**§4.3a. Nagłówek karty na powierzchni member Teams** (kanon makiety „Teams-Panel”): tytuł w CAPS z rozstrzeleniem — `--text-micro`, waga 600, `text-transform: uppercase`, `letter-spacing: .12em`, kolor `--gray-700`; struktura nagłówka bez zmian (`cardHeader` + `h2` + akcje). Back-office (prototypy 1–3) zachowuje obecny `cardTitle` bez CAPS — dwie stylistyki rozróżniają powierzchnie: członkowska (makieta ownera) vs operacyjna (zagęszczona tabela/dane).

### 4.4 Notice (istniejący) — ton: neutral/success/warning/danger/info/brand

D: tło tint, tekst strong, obramowanie `color-mix(strong 25%)`, radius-md, padding space-3/4, ikona 18px `aria-hidden`. **R (błąd inline formularza):** `role="alert"`. **L (status async):** `role="status"` + spinner. Bez H/Ds.

### 4.5 Table (istniejący TableWrap, back-office)

- D: thead tło `--gray-900`, tekst biały, sticky; pasy: wiersze parzyste tło `--gray-50`; hover wiersza tło `--brand-50`. Pierwsza komórka = link do szczegółów (ring wewnątrz komórki). Wiersz min-height 44px; przy ≤900px tabela przewija się w poziomie w TableWrap (overflow-x), kolumny priorytetowe (numer, kandydat, status, zegar) pozostają widoczne.
- **L:** skeleton — 5 wierszy tła `--gray-100` pulsujących; `aria-busy="true"`; tekst dostępny tylko dla czytników „Wczytywanie spraw…”.
- **E:** wiersz pustego stanu wg §5.1 („Brak spraw dla wybranych filtrów” + „Wyczyść filtry”); wariant E2 braku uprawnień: „Nie masz uprawnień do rekrutacji w tym okręgu”.
- **R:** alert z akcją „Spróbuj ponownie”.
- Sortowanie: `<th aria-sort>`; sortujemy kandydata, datę aktualizacji i termin zegara; ikony 16px, kontrast non-text ≥3:1.

### 4.6 FilterBar + Pagination (nowy; lista spraw)

- FilterBar: pasek sticky (`--z-sticky`): Szukaj (input type=search, etykieta sr-only, placeholder §5.1), Status (select — grupy fazowe jako optgroup, pojedyncze statusy wewnątrz), Okręg (select widoczny tylko dla ZK — ZO ma scope zaszyty), Zegar (select), Źródło/Ścieżka oceny. Aktywne filtry: chipy pigułkowe tła `--brand-100`, tekst `--brand-800`, przycisk × (tap 44px). „Wyczyść wszystko” ghost. Liczba wyników: `aria-live="polite"`.
- Pagination: „Poprzednia/Następna” 44px + „Strona x z y”; `aria-current="page"`; Ds na krańcach; L skeleton. 25 wierszy na stronę.

### 4.7 Field / Input / Select / Textarea (wspólny wzorzec)

- Etykieta zawsze widoczna nad polem (caption, waga 500–550); pole wymagane: `aria-required` + gwiazdka; tekst pomocniczy small muted pod polem; błąd: small `--danger-strong` z ikoną, powiązany `aria-describedby`.
- D: tło #FFFFFF, obramowanie 1px `--gray-300`, radius-sm; H: obram. `--gray-400`; **F**: obram. `--brand-600` + ring; **R**: obram. `--danger-strong`, `aria-invalid="true"` + komunikat; Ds: tło `--gray-100`, tekst `--gray-500`; **RO** (uprawnienie tylko do odczytu): tło `--gray-50`, tekst muted, brak edycji.
- Input na ekranach dotykowych: font-size 16px (§2.5); textarea min. 3 wiersze.
- Pole PESEL: font mono, maska `••••••123`, przycisk wglądu — widoczny tylko roli z `RECRUITMENT_MANAGE` (wzorzec: istniejący `components/members/RevealIdentifier.tsx`).

### 4.8 Dialog potwierdzenia przejścia (rozszerzenie istniejącego `Dialog.tsx`)

- Natywny `<dialog>`: focus wewnętrzny, ESC zamyka, fokus wraca do elementu wywołującego, `aria-labelledby`; overlay shadow-3; szerokość 32rem, padding space-6.
- Struktura: H2 „Zmień status sprawy” → Notice (skutek dla zegarów, np. „Okno oceny 62–76 dni wystartuje od daty deklaracji”) → select celu (wyłącznie dozwolone przejścia z maszyny; przejścia niedozwolone **nie są renderowane** — nie pokazujemy szarych, niedostępnych celów) → Reason select (obowiązkowy dla REJECTED; opcje zależą od statusu źródłowego, §STATES) → Notatka (textarea, opcjonalna) → akcje: [Anuluj — outline] [Zapisz — primary; dla REJECTED: Odrzuć sprawę — danger].
- Wariant destrukcyjny: kopia „Tej czynności nie można cofnąć. Zmiana zapisze się w historii sprawy.” Rola bez MANAGE widzi tylko podgląd: przyciski zastąpione tekstem „Status może zmienić tylko Zarząd Okręgu kandydata (uprawnienie zarządzania).”

### 4.9 Timeline zdarzeń (nowy)

- Pionowa lista zdarzeń (typy z migracji 0.0.6): ikona w kółku (tło `--brand-100`, ikona `--brand-700`; zdarzenia terminalne w tintach success/danger), tytuł, data (`<time datetime>`), autor, treść (np. notatka).
- Timeline pokazuje **przeszłość** („co było”); nie pokazuje prognoz — przyszłość prezentują zegary i dostępne przejścia.
- **L:** skeleton 3 wpisów. **E:** EmptyState §5.1.

### 4.10 EmptyState (nowy wspólny wzorzec)

Struktura: ikona 48px (tło brand-100, ikona brand-600, `aria-hidden`) → H3 tytuł → body small muted → jedna akcja (primary albo ghost). Osobne warianty: brak spraw w ogóle / filtry bez wyników / brak uprawnień (akcji brak).

### 4.11 Toast (nowy; tylko potwierdzenia asynchroniczne)

Po zapisie: dole ekranu (mobile) / prawy dolny róg (desktop); tło `--gray-950`, tekst biały, radius-md, 4s; „Zapisano.”; `role="status"`. Błędy nie są toastami — zawsze Notice inline.

### 4.12 Pozostałe (porządek na bazie istniejących)

- LinkButton (Page.tsx): warianty outline/primary spójne z Button; `a` bez `href` nie wygląda jak przycisk.
- Spinner: 16px przy tekście, 24px w pustych stanach; zawsze towarzyszy mu tekst dla czytników.
- Skeleton: tło `--gray-100`, radius-sm, puls opacity (przy reduced-motion — statyczny).
- Tabs w Teams: TabBar dostarcza host Teams (Team app ma 5 zakładek bez własnego paska); w trybie przeglądarki poza Teams istniejący fallback — `role="tablist"`.
- Avatar: brak zdjęć w v1 (minimalizacja RODO) — inicjały: tło `--brand-100`, tekst `--brand-800`.

### 4.13 CampaignBanner (nowy; powierzchnia member Teams, kanon makiety „Teams-Panel”)

Pełnej szerokości baner kampanii (głosowanie, zwołanie zebrania, ogólnokrajowa akcja):

- D: tło `--brand-700` (#7A0E3E), tekst biały (10.71:1), radius-md, padding space-5/6; struktura: overline CAPS z rozstrzeleniem .14em w `--brand-200 #F0CBDD` (np. „GŁOSOWANIE OTWARTE · RADA KRAJOWA”) → H2 biała (tytuł kampanii) → deadline w `--brand-100` (small); z prawej: biała pigułka „Zagłosuj →” (tło #FFFFFF, tekst `--brand-800` — 12.41:1, `radius-pill`, min-height `--control-height`).
- H (pigułki): tło `--brand-50`, tekst bez zmian. F: ring biały (na maroon); I: `--brand-100`.
- Stany: E (brak kampanii) = **komponent się nie renderuje**; L (dane kampanii ładowane) = skeleton banera; R = baner nie pokazuje się wcale (błąd pobierania kampanii nie blokuje strony — `role="region"` + `aria-label` z nazwą kampanii).
- Kontrast non-text: biała pigułka na maroon = 10.71:1 ✓; overline #F0CBDD na #7A0E3E ≈ 8.5:1 ✓ (do powtórzenia wraz z ostatecznym odcieniem przy implementacji — dopuszczalny zakres #F0CBDD–#F6E3ED).

Każdy komponent przechodzi checklistę stanów przy review (REVIEW-CHECKLIST: „stany pokryte”).

---

## 5. Mikrokopie PL

Ton: mówimy per „Ty” do członka/członkini i kandydata/kandydatki; bez żargonu systemowego („status sprawy”, nie „rekord maszyny”); bez winienia użytkownika; wzorcem tonu jest istniejąca kopia incydentu — „Za zgłoszenie nikt nie wyciąga konsekwencji”. Formy neutralne: „osoba członkowska”, „kandydat/kandydatka” — tam, gdzie forma gramatyczna wymusza, używamy „członek/członkini”.

### 5.1 Empty states (słownik kompletny)

| Kontekst | Tytuł | Body | Akcja |
|---|---|---|---|
| Lista spraw, brak danych | Nie ma jeszcze żadnych spraw | Gdy zgłosi się kandydat z Twojego okręgu, sprawa pojawi się tutaj. | „Zalóż sprawę ręcznie” (primary) |
| Lista spraw, filtry bez wyników | Brak spraw dla wybranych filtrów | Zmień filtry albo wyczyść je wszystkie. | „Wyczyść filtry” (ghost) |
| Lista spraw, brak uprawnień | Nie masz dostępu do rekrutacji | Rekrutacja widoczna jest dla zarządu właściwego okręgu i Zarządu Krajowego. | — |
| Timeline pusty | Historia zdarzeń jest pusta | Kontakt, deklaracja i decyzje zapiszą się tutaj w miarę prowadzenia sprawy. | — |
| Panel decyzji pusty | Nie zapisano jeszcze decyzji kolegialnych | Rekomendacja ZO i głosowanie ZK pojawią się tutaj wraz z sygnaturami uchwał. | — |
| Notatki puste | Brak notatek | Dodaj pierwszą notatkę do sprawy. | „Dodaj notatkę” |

### 5.2 Mapowanie kodów błędów API → teksty dla człowieka

Zasada: nie pokazujemy surowego kodu ani stosu; fundament: konkretny tytuł + jedno zdanie + akcja. Kod wolno zostawić w `data-error-code` dla diagnostyki.

*Adnotacja (2026-11-22, po review M4b):* słownik poniżej traktujemy jako **kanon znaczeń**, nie liter — dopuszcza się równoważne sformułowania z zachowaniem tytułu, intencji i akcji (propozycja: przyjąć brzmienia modułu M4b jako kanoniczne). Patrz REVIEW-M4B.md §6.3.

| Kod (backend) | Gdzie | Komunikat UI |
|---|---|---|
| `NOT_AWAITING_DECLARATION` (409) | e-deklaracja (M4a/M4b) | „Nie można zastosować deklaracji” — „Ta sprawa nie oczekuje już na deklarację. Jeśli to błąd, skontaktuj się ze swoim Zarządem Okręgu.” |
| Brak `RECRUITMENT_READ` (403) | lista/szczegóły | „Nie masz uprawnień do tej sprawy” — „Rekrutację widzi zarząd okręgu kandydata i Zarząd Krajowy.” |
| `RECRUITMENT_MANAGE_REQUIRED` (403) | akcje modyfikujące | „Nie możesz zmienić tej sprawy” — „Zmiany wykonuje zarząd okręgu kandydata. Skontaktuj się z koordynatorem.” |
| Konflikt stanu (409, np. zmiana równoległa) | dialog przejścia | „Status sprawy się zmienił” — „Ktoś inny właśnie zaktualizował tę sprawę. Odśwież szczegóły i spróbuj ponownie.” |
| Walidacja pola (e-mail, telefon, PESEL) | formularze inline | „Podaj adres e-mail w formacie nazwa@domena.pl” / „Numer telefonu ma 9 cyfr” / „Suma kontrolna numeru PESEL nie zgadza się — sprawdź cyfry.” |
| Upload: plik za duży (>10 MB) | upload deklaracji | „Plik jest za duży” — „Maksymalnie 10 MB. Skompresuj PDF i spróbuj ponownie.” |
| Upload: zły format | upload | „To nie jest PDF” — „Wgraj dokument w formacie PDF.” |
| Upload: sieć/timeout | upload | „Nie udało się wysłać pliku” — „Sprawdź połączenie i spróbuj ponownie — wgrany plik nie zniknie.” |
| Link kandydata nieosiągalny (nieprawidłowy/wygasły/zamknięty) | publiczny flow | **jeden ujednolicony komunikat** (patrz IA: RODO): „Ten link nie działa” — „Link może być nieaktualny albo sprawa została już zakończona. Skontaktuj się z Zarządem Okręgu, który Ci go wysłał — poprosi o nowy.” |
| 500/503 (globalnie) | wszystkie | „Coś poszło nie tak” — „To błąd po naszej stronie. Twoje dane są bezpieczne. Spróbuj ponownie za chwilę.” + przycisk „Spróbuj ponownie”. |

### 5.3 Mikrokopie newralgiczne

- Przyjęcie (→ ACCEPTED): dialog „Zapisać przyjęcie i utworzyć rekord członkowski?” — „Po zapisaniu system przydzieli numer członkowski i utworzy rekord. Czynności nie można cofnąć.” (primary „Zapisz przyjęcie”)
- Przedłużenie okresu (jednorazowe): „Przedłuż okres wstępny” → „Przedłużenie jest możliwe tylko raz. Do kiedy zaplanować drugą ocenę?” (pole daty, maks. +31 dni)
- Uchwała ZO / opinia ZK: „Sygnatura uchwały (np. ZO/MZ/01/2026)” + „Data uchwały” — font mono; domyślna notka dla milczącej zgody ZK: „termin minął bez opinii — art. 9 ust. 4 pkt 6” (z konstanty `TACIT_ZK_APPROVAL_NOTE`, nie do edycji).
- Zgłoszenie incydentu (bez zmian wobec obecnego): „Zgłoś incydent” + „Za zgłoszenie nikt nie wyciąga konsekwencji.”
- Powierzchnia member Teams — mikrokopie kanonu makiety: hero „Cześć, {imię}”; podtytuł „Członkini zwyczajna od marca 2024 · Okręg Mazowiecki” (forma zależna od rekordu; wariant neutralny: „Członkostwo zwyczajne od…”, decyzja: owner); baner kampanii: overline „GŁOSOWANIE OTWARTE · RADA KRAJOWA”, tytuł kampanii, deadline, pigułka „Zagłosuj →”; karty: „Opłacona”, „do 31 grudnia 2026”, „Historia wpłat”, „Zebranie 28 listopada, 12:00, Warszawa i Teams”, „Szczegóły i zespół Okręgu”, „Zgłoś zmianę danych”, „Wszystkie”, „Pomoc IT: zgłoś sprawę”; ogłoszenia w formacie „źródło · data” (overline) pod spodem linkowany tytuł.
- Publiczny flow — kroki: „Twoje dane” / „Zgody” / „Dokumenty” / „Gotowe”.
- Sukces: „Dziękujemy! Deklaracja została wysłana.” — „Zarząd Okręgu potwierdzi przyjęcie i skontaktuje się z Tobą.”
- Zakaz w kopiach: kody błędów, nazwy pól bazy, „rekord”, „endpoint”, surowe daty ISO (używamy formatu dd.mm.rrrr).

---

## 6. Accessibility (WCAG 2.1 AA — wymagania brzegowe)

1. **Kontrast** — wszystkie pary z §3; żadne własne kombinacje bez przeliczenia (§0.3).
2. **Focus-visible** — pełnobarwny ring `--brand-600`: `outline: 2px solid; outline-offset: 2px` (na ciemnych powierzchniach ring biały). Zakaz `outline: none` bez zamiennika; zakaz stylizacji wyłącznie `:focus` bez `:focus-visible`.
3. **Dotyk** — cel ≥44×44px (przycisk, checkbox, „×” chipu); odstępy między celami ≥8px.
4. **Klawiatura** — wszystkie akcje osiągalne z klawiatury; dialog natywny (focus uwięziony, powrót fokusu po zamknięciu, ESC); link „Przejdź do zawartości” na początku tabIndex każdej strony back-office.
5. **Semantyka ARIA** — statusy jako tekst + ikona (nie sama etykieta aria); błędy inline `role="alert"`; licznik wyników `aria-live="polite"`; `aria-sort` na nagłówkach sortowalnych; `aria-disabled` dla dekoracyjnych stanów wyłączonych; nagłówek formularza nowej sprawy informuje: „Pola oznaczone * są wymagane”.
6. **Zrozumiałość** — widoczna etykieta każdego pola + `autocomplete` wg przeznaczenia (dane kandydata i deklaracja publiczna); błąd nie tylko kolorem — ikona + tekst.
7. **Skalowanie** — layout przetestowany na 200% powiększenia tekstu; długie nazwiska zawijają się (`overflow-wrap: anywhere`); tabela przewija się bez ucinania treści.
8. **Język** — `<html lang="pl">` na wszystkich stronach, także w publicznym flow; opcje selectów to pełne nazwy statusów, nie skróty.

---

## 7. Zgodność z istniejącym kodem (delty)

### 7.1 Tokeny — co przyjmujemy, co zmieniamy w `theme.scss`

- Przyjmujemy bez zmian: skalę szarości, spacing, radius, cienie, wysokości desktopowe, easing.
- Zmieniamy (bez łamania istniejących ekranów): `--color-primary: var(--brand-700)`; `--color-focus` — z przezroczystego mixa na pełny `--brand-600` (finding 1.4.11); dodajemy `--success-strong` i przepinamy teksty success; `--color-orange` degradowany do non-text.
- Aliasing: `--plum-*` zostają jako aliasy `--brand-*` do czasu generalnego porządkowania; prototypy i nowy kod używają **wyłącznie** `--brand-*`.

### 7.2 Rekomendacje napraw findingów (do backlogu; nie blokują M2b/M4b — patrz REVIEW-CHECKLIST)

1. `Page.module.scss` `.badge[data-tone="warning"]`: tło `--color-orange` + biały tekst = 3.58:1 — łamie AA. Naprawa: tło `--warning-tint`, tekst `--warning-strong` (4.62:1) — spójne z pastelowym wzorcem.
2. `theme.scss` `--color-focus` (mix 35% transparent): ≈1.6:1 na bieli — łamie 1.4.11. Naprawa: pełny kolor (§6.2).
3. `.notice[data-tone="success"]` — tekst `--color-success` na tincie = 3.73:1 (fail). Naprawa: `--success-strong` (6.29:1).
4. Badge'e solid `tone="success|danger|info"`: biały na success = 4.17 ✗ (info 12.52 ✓, danger 5.53 ✓). Naprawa: pastel (§4.2).
5. `--control-height: 2.5rem` (40px) na dotyku <44px — dotyczy Teams mobile. Naprawa: przy ≤900px — 2.75rem.

### 7.3 Świadomie poza zakresem v1

- Pełny dark mode (tokeny forward-only, §2.8) — decyzja backlogowa.
- Nowe biblioteki ikon: prototypy używają inline SVG (stroke 1.5px, styl zgodny z Phosphor, który już jest w zależnościach — `home/page.tsx`).
- Animacje przejść między zakładkami (nawigację prowadzi Teams).
- **Ekran postu aplikacji na kanale Teams** (zwołanie zebrania okręgu — artboard Screen 2 makiety): zakres nieprzekuty z ownerem; w IA.md notowany wyłącznie jako powierzchnia przyszłości (IA §6). Komponenty z tego SPEC (CampaignBanner, Card, Button) nadają się do niego bez zmian.
- Favicon/manifest — poza zakresem UX.

---

*Koniec SPEC. Kolejność czytania przy review: SPEC §2–§3 (tokeny) → REVIEW-CHECKLIST (binarnie) → STATES (statusy/zegary) → IA (struktura) → prototypy (spójność wizualna).*
