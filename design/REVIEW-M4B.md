# REVIEW-M4B — odbiór publicznego flow deklaracji kandydata (M4b) wg REVIEW-CHECKLIST.md

**Obiekt:** worktree `panel-recruitment-eform-web`, branch `feat/recruitment-eform-web` (Junior 4). Review czytelne — **zero zmian w cudzym worktree**.
**Zakres plików:** `app/(public)/rekrutacja/deklaracja/**`, `app/actions/recruitment.ts`, `app/api/recruitment/declaration-form/**`, `panel-api/recruitment/{declarationForm,mockDeclarationFormApi}.ts`, `util/pesel.ts`, `i18n/messages/messages-{pl,en}.ts` (ns `recruitment`), `config/env.ts`.
**Metoda:** [REVIEW-CHECKLIST.md](REVIEW-CHECKLIST.md) A–K binarnie + porównanie 1:1 z `prototypes/screen-candidate-form.html` i `prototypes/screen-candidate-upload.html`.
**Data:** 2026-11-22 · Werdykt całościowy: **warunkowo zaliczone przy 4 blockerach do naprawy** (wszystkie 4 to jednoplikowe, tokenowe patche — trzy z nich to znane luki wspólnej warstwy z SPEC §7.2, które w kodzie M4b się ujawniają).

Scoring wykonany: obowiązkowe kategorie seniora — BLOCKER (a11y prawne), SHOULD (tokeny/kopie/stany/RODO), NICE (kosmetyka).

---

## 1. Wyniki checklisty sekcjami (pass ✓ / fail ✗ / n/d)

### A. Tokeny i style

| Pkt | Werdykt | Dowód (plik:linia) |
|---|---|---|
| A1 brak hex/rgba inline | ✓ | `DeclarationPage.module.scss` — wszystkie kolory przez `var(--…)`; zero własnych hexów; select z data-URI (`%236b6270`) siedzi w `Form.module.scss` — pliku nietkniętym przez M4b |
| A2 `--brand-*` nie `--plum-*` | **n/d** | migracja aliasów ze SPEC §7.1 jeszcze nie wylądowała w drzewie; M4b konsekwentnie na `--plum-*` — po wdrożeniu aliasów zgodność bez zmian w kodzie |
| A3 primary #7A0E3E | **n/d** | kod odwołuje się `var(--color-primary)`; wartość zależy od migracji (dziś #720546, Δ wizualnie znikoma) |
| A4 success-strong dla tekstu | ✗ | tekst sukcesu w kolorze `--color-success` #1E8E4E na tincie #E7F6ED = **3.73:1** → rendering w `DeclarationUploadStep.tsx:185` (`Notice tone="success"`, deklaracja karty klasy w pre-existing `Page.module.scss .notice[data-tone="success"]`) |
| A5 orange nie jako tekst | ✓ | brak użycia orange w M4b |
| A6 badge pastel | n/d | brak badge'y w flow |
| A7 focus ring pełny ≥2px, bez outline:none bez zamiennika | ✗ | `--color-focus` = `color-mix(plum-600 35%, transparent)` → skomponowany nad bielą **#D7AAC5 = 2.01:1** (<3:1, WCAG 1.4.11); występowanie: warstwa wspólna `theme.scss:62,142`, natywnie w plikach M4b: `DeclarationPage.module.scss:276` (`.linkButton:focus-visible`) i `:316` (`.dropzoneButton:focus-within`); `.input` (`Form.module.scss:69–71`): `outline:none` + `box-shadow` w tym samym słabym kolorze (ratuje tylko border `plum-600` 9.08:1) |
| A8 spacing 4/8 z tokenów | ✗ | `DeclarationPage.module.scss:286`: `padding: var(--space-7) var(--space-4)` — **token `--space-7` nie istnieje** (theme definiuje 1–6, 8) → cała deklaracja paddingu dropzone nieważna, padding pionowy = 0 (dropzone się skleja); reszta odstępów OK |
| A9 cienie/z-index z tokenów | ✗ | `DeclarationPage.module.scss:71`: własny cień karty `0 var(--space-1) calc(var(--space-2)*3) var(--color-shadow)` ≠ `--shadow-1/2/3`; z-index niewykorzystany ✓ |
| A10 prefers-reduced-motion | ✗ | brak bloku w theme (pre-existing) i w plikach M4b, które dodają własne transition (`DeclarationPage.module.scss:290`; `Form.module.scss:58`) |
| A11 jeden breakpoint 900px | ✗ | `DeclarationPage.module.scss:43,58,162` — breakpointy `48rem`/`40rem`; nie łamie a11y, niespójna konwencja |

### B. Stany komponentów

| Pkt | Werdykt | Dowód |
|---|---|---|
| B1 stany listy | n/d | brak list w flow |
| B2 formularz: etykieta/helper/error/disabled/RO | ✗ | inputy tekstowe: LABEL+for, hint i error powiązane `aria-describedby`, `aria-invalid` — ✓ (`DeclarationFormStep.tsx:341–380`); **checkboxy: błędy NIEpowiązane** — `aria-describedby` wskazuje tylko hint (:234, :284), `<p className=checkboxError>` bez id/referencji (:242, :292), brak `aria-invalid` na checkboksie; brak `aria-required`/`required` na wymaganych inputach (:365 tylko gwiazdka aria-hidden) |
| B3 przyciski: loading/disabled | ✓ | `aria-busy` na formie (:175), `disabled={pending}`, tekst statusu `form.pendingLabel` (:331); upload `disabled={!file \|\| uploading}` (`DeclarationUploadStep.tsx:189`) — brak tylko wizualnego spinnera (NICE) |
| B4 dialog | n/d | brak dialogów (brak stanów destrukcyjnych w flow — zgodne z IA) |
| B5 stany uploadu | ✓ | typ/rozmiar klient+serwer (`pickFile` :66–79; route `declaration/route.ts:35–45`), `SIGNATURE_INVALID` → Notice + plik zachowany, sieć → `errUpload` + plik zachowany, `sentOk` role=status (:185); brak: progress uploadu, rozmiar pliku w chipie, przycisk „Usuń” (NICE) |
| B6 toast | n/d | brak toastów; potwierdzenia przez Notice `role="status"` ✓ |
| B7 EmptyState | n/d | terminalne StatusScreen mają strukturę ikona+H1+body — funkcjonalny odpowiednik |

### C. Accessibility

| Pkt | Werdykt | Dowód |
|---|---|---|
| C1 pary kontrastu ≥4.5/3 | ✗ | fail: success-text 3.73 (A4), focus 2.01 (A7); pass policzone: muted 5.45 na #F8F7F9, danger-tekst 5.53, primary 11.50, logo non-text na plum-500 6.75 |
| C2 klawiatura/fokus widoczny | ✗ | Tab działa przez inputy/przyciski/label-wrapped checkboxy ✓; widoczność ringu krawędziowa na customach (A7) — nie przechodzi |
| C3 dotyk ≥44px | ✗ | `--control-height: 2.5rem` = **40px** (pre-existing `theme.scss:92` + `Button.module.scss:23`) → primary CTA flow mobile 40px; `.linkButton` ≈40px (:262), `.dropzoneButton` ≈39–41px (:302); checkbox ma pełnorzędowy cel dotykowy ✓ (label w gridzie), `stepNumber` 24px dekoracyjny aria-hidden ✓ |
| C4 input ≥16px na dotyku | ✗ | `Form.module.scss:61` `font: inherit` → 15px na dotyku (auto-zoom iOS przy focus) |
| C5 `<html lang="pl">` | ✓ | `app/layout.tsx:43` `<html lang={locale}>`; ns recruitment PL+EN kompletny |
| C6 aria-live/current | ✓ | stepper `aria-current="step"` (`DeclarationWizard.tsx:60`), regiony `aria-live="polite"` na notyfikacjach (:326; `DeclarationUploadStep.tsx:182`), liczników brak (n/d) |
| C7 zoom 200% | ✓~ | kolumna 1-kolumnowa poniżej 40rem, `clamp()` na tytułach; ryczalt: `.step`/`.stepper` `white-space: nowrap` bez wrap — przy 200% na ~320px stepper może uciekać (NICE) |
| C8 info nie tylko kolorem | ✓~ | błędy mają tekst; brak ikony przy błędach (SPEC §4.7: ikona+tekst) — NICE, nie łamie kryterium |
| C9 skip-link | n/d | flow publiczny bez nawigacji |

### D, E. Statusy/zegary back-office; kolumny/filtry

**n/d** dla M4b (moduł M2b). Pozytywny wariant domyślny: flow kandydata **nie eksponuje** statusów maszyny ani nazw powodów (zgoda z IA §3c i STATES §5) — `FinalScreen` jest celowo wolny od zegarów (`DeclarationScreens.tsx:47–60`, kopia `final.probationBody` bez „62–76 dni”).

### F. Publiczny flow kandydata (sekcja dedykowana M4b)

| Pkt | Werdykt | Dowód |
|---|---|---|
| F1 identyczny ekran martwego linku | **✗** | cztery odrębne ekrany: `DeclarationScreens.tsx:27–39` + `messages-pl.ts:604–610` — „Link niedostępny” / „Link wygasł” / „Sprawa zakończona” / „Coś poszło nie tak”, każdy z innym body; klasyfikacja błędów: 404→unavailable, `TOKEN_EXPIRED`→expired, 409→closed (`declarationForm.ts:classifyCaseFailure`, route `declaration:88–101`) |
| F2 zero identyfikatorów | ✗~ | powitanie z danymi: `` t("landing.greetingTitle", {name: greeting}) `` (`DeclarationWizard.tsx:68`), kontrakt `greeting: z.string()` (`declarationForm.ts:42`), mock zwraca „Anna K.” → ekran „Cześć, Anna K.”; brak echo e-maila ✓; brak third-party skryptów ✓; token tylko w path ✓ |
| F3 obowiązek informacyjny + zgody | ✓ | pełna klauzula w `<details>` (`DeclarationFormStep.tsx:316–330`) + te same treści w PDF; zgoda szczególnej kategorii wymagana z gwiazdką i walidacją; wizerunkowa z dopiskiem „Zgoda jest dobrowolna… możesz ją wycofać w każdej chwili” (`messages-pl.ts`) — dokładnie wg wymogu |
| F4 upload: walidacja+BK/BM, komunikaty, disabled | ✓ | klient `pickFile` + serwer route; komunikaty z SPEC; przycisk disabled + `aria-disabled`; upload do route handlera (server actions mają limit 1 MB — decyzja techniczna udokumentowana w README) |
| F5 stepper 4-krokowy | ✗ | 3 kroki („Rozpoczęcie / Twoje dane / Podpis i wysyłka”, `DeclarationWizard.tsx:31`); sukces poza stepperem (serwerowy FinalScreen po `router.refresh()`) |
| F6 PESEL checksum | ✓ | `pesel.ts:isValidPesel` (wagi 1,3,7,9…; checksum oficjalny) + format 11 cyfr + bonus: wyliczona data urodzenia + checkbox potwierdzenia daty (anty-literówka — lepsze niż w prototypie); błędy nie zdradzają istnienia konta ✓ |
| F7 ekran sukcesu | ✓~ | FinalScreen bez PII i bez zegarów; kopia „Deklaracja złożona / Twój Zarząd Okręgu…” — brak jedynie zdania o unieważnieniu linku z SPEC §5.3 (NICE; semantycznie SIGNED ≠ martwy link — odświeżenie ponownie pokazuje sukces) |
| F8 routing + identyczny DOM martwych linków | ✗~ | ścieżka `/rekrutacja/deklaracja/[token]` (IA proponowało `/d/[token]` — dopuszczalne odstępstwo nazewnicze, url dłuższy ale czytelniejszy); „identyczny DOM” ✗ jak F1 |
| F9 pola kroku „Twoje dane” 1:1 | ✗~ | zgodne w kolejności: urzędowe → preferowane → PESEL → zaimki → telefon; brak: uczelnia (C.11, moja kolumna „opcjonalna”), e-mail (świadomie: pochodzi z intake na sprawie — dopuszczalne), adres (Q4 otwarte — dopuszczalne), „miejscowość wypełnienia” pominięta zgodnie z Q5 ✓ |
| F10 struktura kroku „Dokumenty” 1:1 | ✓~ | podgląd PDF w iframe + „Otwórz PDF w nowej karcie” (lepsze niż prototyp — ratuje Safari mobile), instrukcja podpisu 3-krokowa (mObywatel/eDO — konkretniejsza niż neutralna kopia; **zależna od decyzji Q2**), dropzone+stany, zgody przeniesione do kroku 2 (semantyka zachowana) |
| F11 restylizacja po scaleniu | n/d | zależne od migracji tokenów SPEC §7.1 |
| F12 kopie/stany/aria 1:1 | ✗~ | różnice kopi per G2; aria powyżej średniej (aria-current, aria-busy, aria-live ✓) |

### G. Mikrokopie

| Pkt | Werdykt | Dowód |
|---|---|---|
| G1 teksty z i18n | ✓ | 100% przez `t("recruitment…")`; brak hardcoded PL w TSX |
| G2 teksty 1:1 ze słownikami SPEC | ✗~ | patrz findings #15–16; jakościowo równoważne, ale ≠ literowi mojemu słownikowi — wymagana decyzja: przyjąć słownik M4b (rekomendacja: tak) albo dosłownie dosprawdzić |
| G3 mapowanie kodów bez wycieku | ✓ | kody mapowane (`fieldMessage` w `DeclarationFormStep.tsx:160–166`), surowe kody poza UI |
| G4 bez żargonu, format dat | ✓ | brak „endpointu/rekordu” w UI; daty przez `Intl.DateTimeFormat` (medium/long) ✓ |

### H. RODO exposure

| Pkt | Werdykt | Dowód |
|---|---|---|
| H4 public flow minimum | ✗~ | imię w greeting (#6) + rozróżnialne stany linku (#5); POZYTYWY: `robots noindex,follow=false` (`page.tsx:39–41`), `Cache-Control: no-store` na PDF i uploadzie, token wyciszony z OTEL (`declarationForm.ts:publicFetch` + `suppressTracing`), mock bez PII (`mockDeclarationFormApi.ts`) |

### I. Zgodność wizualna z prototypami — patrz §2 poniżej (diff 1:1).

### J. Poza zakresem — n/d (dark mode: kod nie pisze nic, co blokowałoby podmianę — A1 ✓).

### K. Higiena danych

| Pkt | Werdykt | Dowód |
|---|---|---|
| K1 zero realnych PII | ✓ | fikstury syntetyczne („Anna K.” bez pasma — mock dev-only, wyłączony w produkcji bez env); PESEL-e testowe przechodzą checksumę |
| K2 brak danych prod w treściach | ✓ | notatki/README bez realnych danych |

---

## 2. Porównanie 1:1 z prototypami

### `screen-candidate-form.html` (4a) vs `DeclarationFormStep`

| Obszar | Prototyp 4a | Implementacja M4b | Ocena |
|---|---|---|---|
| Stepper | 4 kroki, kropki+liczby, statusy done/current | 3 kroki, liczby w kółkach, `aria-current="step"` | różnica struktury (F5); aria lepsza w impl. |
| Kolumna treści | 28rem (rama telefonu 390px) | max-width 43rem, mobile 1 kol, ≥40rem 2 kol w `formGrid` | odstępstwo szerokości kolumny (43rem > 28rem IA); na mobile zgodne |
| Sekcje A | urzędowe → preferowane → PESEL (mono) → zaimki → telefon → e-mail → uczelnia + adres (collapse) | urzędowe → preferowane → PESEL → zaimki → telefon + przysięga + zgody + klauzula | kolejność zgodna; brak: e-mail (świadomie), uczelnia (**finding #15**), adres (Q4); plus: przysięga+klauzula w UI |
| PESEL | mono font, checksum, error z ikoną | zwykły font (finding **NICE #14**), checksum ✓, wyliczona data urodzenia + potwierdzenie (**PLUS**), error tekstem powiązanym aria ✓ |
| Błędy pól | ikona+tekst, aria-invalid, aria-describedby | tekst powiązany (inputy ✓) / **niepowiązany (checkboxy — BLOCKER #2)** |
| Stany pending | `aria-disabled` + helper | `aria-busy` na form, disabled, tekst „Zapisujemy deklarację…” ✓ |
| Zgody | kroku upload | kroku form | różnica przypisania kroków; semantyka wymagane/dobrowolna zgodna ✓ |
| HTTPS-note | footer „szyfrowanym kanałem” | brak odpowiednika w form step (jest `deviceHint` bez wzmianki o szyfrowaniu; klauzula RODO jest) | NICE — dopisać jedną linijkę |

### `screen-candidate-upload.html` (4b) vs `DeclarationUploadStep`

| Obszar | Prototyp 4b | Implementacja M4b | Ocena |
|---|---|---|---|
| Podgląd PDF | 2 przyciski „Pobierz PDF” | iframe podglądu + „Otwórz w nowej karcie” | **PLUS impl.** (podgląd inline; fallback dla Safari) |
| Instrukcja podpisu | 3 kroki, neutralnie „u swojego dostawcy” | 3 kroki, mObywatel/web/eDO | spójna struktura; konkret providerów zależny od Q2 (#16) |
| Dropzone | tekst + „Wybierz plik” | ikona + `dropLabel` + label-button; dragging state | zgodne; **finding #7** (padding=0 przez `--space-7`) |
| Stan pliku | nazwa + rozmiar + „Usuń” | chip: nazwa tylko | NICE #18 |
| Błędy | format/rozmiar z ikoną w Notice | format/rozmiar/podpis/sieć w Notice `role=alert` + `aria-live` | zgodne funkcjonalnie; zasięg lepszy w impl. |
| Submit | disabled aż plik+zgody + helper | disabled aż plik; zgody załatwione w kroku 2 | zgodne (semantyka przesunięta); brak helpera „dlaczego disabled” (NICE) |
| Sukces | ekran kroku 4 | Notice `role=status` + `router.refresh()` → serwerowy FinalScreen | zgodne; wznowienie od kroku 3 przy statusie FILLED **PLUS** |

---

## 3. Findings (pełna tabela, malejąco wg wagi)

Format: `plik:linia | obecnie | powinno być | kryterium | klasa`.

| # | plik:linia | obecnie | powinno być | kryterium | klasa |
|---|---|---|---|---|---|
| 1 | `theme.scss:62,142` (pre) + `DeclarationPage.module.scss:276,316` + `Form.module.scss:69–71` | focus ring w `--color-focus` = mix 35% → **#D7AAC5 na bieli = 2.01:1**; `.input` `outline:none` + box-shadow w tym samym kolorze | pełnobarwny ring `--brand-600 #93275D` (7.17:1 na #F5F5F7), 2–3px, offset 2px (SPEC §6.2); zakaz `outline:none` bez pełnoprawnego zamiennika | A7, C2 | **BLOCKER** |
| 2 | `DeclarationFormStep.tsx:234+242, 284+292` | błędy checkboxów (zgoda wymagana, potwierdzenie daty) jako `<p>` bez id; input `aria-describedby`→tylko hint; brak `aria-invalid` | error `<p id="…-error">` + `aria-describedby="hint error"` + `aria-invalid="true"` na checkboksie; opcjonalnie kontener `aria-live` | B2 (+definicja BLOCKER seniora: brak stanu error powiązanego z polem) | **BLOCKER** |
| 3 | `theme.scss:92` + `Button.module.scss:23` + `DeclarationPage.module.scss:262,302` | `--control-height: 2.5rem` (40px) → wszystkie CTA i inputy; `.linkButton`≈40px, `.dropzoneButton`≈39–41px | ≥44px na dotyku: `--control-height: 2.75rem` przy ≤900px (SPEC §7.2-5); buttony tego flow są głównym celem dotykowym | C3 | **BLOCKER** |
| 4 | `DeclarationUploadStep.tsx:185` (miejsce renderu) + warstwa wspólna `Page.module.scss .notice[data-tone="success"]` | tekst „Deklaracja przyjęta…” w `--color-success` #1E8E4E na #E7F6ED = **3.73:1** | tekst w `--success-strong #14663A` (6.29 na tincie) — SPEC §2.3/§7.2-3 | A4, C1 | **BLOCKER** |
| 5 | `DeclarationScreens.tsx:27–39` + `messages-pl.ts:604–610` + `declarationForm.ts` (classify) | 4 odrębne ekrany i kopie dla martwych linków (unavailable/expired/closed/error) → martwy token zdradza status sprawy (enumeracja: „wygasł” ⇒ sprawa istniała) | jeden ujednolicony ekran i kopia: „Ten link nie działa — … poproś ZO o nowy” (SPEC §5.2 / IA §3.1); rozróżnienie logów zostaje po stronie serwera | F1, F8, H4 | **SHOULD** |
| 6 | `DeclarationWizard.tsx:68` + `declarationForm.ts:42` + mock „Anna K.” | powitanie „Cześć, {imię i inicjał}” — echo danych osobowych za bearer tokenem | powitanie bez danych („Witaj!”), ewent. świadoma decyzja ownera (personalizacja ↑ zaufanie) — do potwierdzenia | F2, H4 | **SHOULD** |
| 7 | `DeclarationPage.module.scss:286` | `padding: var(--space-7) var(--space-4)` — token `--space-7` **nie istnieje** → deklaracja nieważna, padding pionowy dropzone = 0 (UKŁAD SIE KLEI) | `padding: var(--space-6) var(--space-4)` | A8 | **SHOULD** |
| 8 | całość stylów + `DeclarationPage.module.scss:290` | brak `prefers-reduced-motion` przy dodanych transition (dropzone, input) | blok globalny w theme: `@media (prefers-reduced-motion: reduce) { *{transition-duration:.01ms!important;…} }` — SPEC §2.7 | A10 | **SHOULD** |
| 9 | `Form.module.scss:61` (+205) | `font: inherit` → inputy 15px → iOS Safari auto-zoom przy focus | `font-size: 1rem` (≥16px) dla inputów/selectów/textarea na dotyku — SPEC §2.5b | C4 | **SHOULD** |
| 10 | `DeclarationFormStep.tsx:365` (+Field) | gwiazdka `aria-hidden`, input bez `required`/`aria-required` | `aria-required="true"` (i normatywny `required`) na wymaganych polach | B2 | **SHOULD** |
| 11 | `DeclarationPage.module.scss:366–368` | `.statusIconDanger { color: var(--color-primary) }` — ikony ekranów błędu w maroon | semantyka SPEC §2.3: danger → `--danger-strong` (albo świadoma decyzja stylistyczna „brand-softened”; kontrast 11.5 ✓ więc a11y n/d) | §2.3 semantyka | **SHOULD** |
| 12 | brak w `DeclarationFormStep` | pole „uczelnia” (C.11) nieobecne (w prototypie sekcja opcjonalna) | dodać pole opcjonalne albo odnotować świadome pominięcie (ma też sens — minimum danych) | F9 | **SHOULD** |
| 13 | `messages-pl.ts` (upload.signIntro/SignStep*) | instrukcja podpisu konkretnie mObywatel/eDO | dopuszczalne **po decyzji Q2 ownera** (IA zakładało neutralną kopię) | F10 | **SHOULD** (zależne od decyzji Q2) |
| 14 | `DeclarationFormStep.tsx:167–275` + PESEL input | PESEL bez fontu mono | `--font-mono` dla PESEL (SPEC §2.5: identyfikatory w mono) | §2.5 | NICE |
| 15 | `messages-pl.ts` (sekcja `errors`) | komunikaty równoważne, ale niezgodne co do litery ze słownikiem SPEC §5.2 („Numer PESEL to jedenaście cyfr.” vs „…ma 11 cyfr”; „zawiera błąd…” vs „suma kontrolna… nie zgadza się”) | decyzja: przyjąć słownik M4b do SPEC (rekomendacja: brzmienie naturalniejsze) albo wyrównać 1:1 | G2 | NICE |
| 16 | `DeclarationUploadStep.tsx:177` | chip pliku: nazwa bez rozmiaru i bez „Usuń” | nazwa + rozmiar + ghost „Usuń” (prototyp 4b) | F10/badanie 1:1 | NICE |
| 17 | `DeclarationWizard.tsx:31,68`+`messages` | stepper 3-krokowy; brak zdania o unieważnieniu tokenu na sukcesie (w SPEC §3.1 §5.3 było) | krok 4 „Gotowe” (IA §3.2) albo akceptacja 3-krokowej konwencji + 1 zdanie na FinalScreen | F5, F7 | NICE |
| 18 | `DeclarationPage.module.scss:43,58,162` | breakpointy 48rem/40rem (sąsiedztwo 900px konwencji) | scalać na 900px przy okazji migracji | A11 | NICE |
| 19 | `DeclarationPage.module.scss:71` | własny cień karty zamiast `--shadow-1` | `box-shadow: var(--shadow-1)` | A9 | NICE |
| 20 | `DeclarationPage.module.scss:115,262…` | `.step` 12.5px (0.78rem), `.cardHint` 13.6px (0.85rem) — poniżej skali (caption 13px) | skalować na `--text-caption/.8125rem`, `--text-small/.875rem` | §2.5 | NICE |
| 21 | `DeclarationWizard.tsx:58` + `.stepper/.step` | `white-space: nowrap` bez wrap — przy 200% zoom stepper może uciekać poziomo | flex-wrap: wrap | C7 | NICE |
| 22 | `DeclarationFormStep.tsx:326` + `DeclarationUploadStep.tsx:182` | `aria-live="polite"` wokół Notice, które same mają `role="alert"`/`"status"` | redundancja nieszkodliwa; uproszczyć przy okazji | C6 | NICE |
| 23 | `DeclarationPage.module.scss:23` | tło bandy: gradient plum-500/700/900/950 | po migracji — rampa brand; dzisiaj zgodne ze starą stylistyką loginu (spójne) | A2 (n/d) | NICE |
| 24 | brak | footer form stepa nie przypomina o HTTPS (w prototypie „szyfrowanym kanałem (HTTPS)”) | 1 linia trust-copy | F12 | NICE |

Klasyfikacja liczbowo: **BLOCKER ×4 · SHOULD ×9 · NICE ×11**.

Uwaga do blockerów #1/#3/#4: wszystkie trzy pochodzą **z warstwy wspólnej** (theme.scss → Button/Form/Page module) i były przewidziane jako naprawy w SPEC §7.2 (odp. poz. 2, 5, 3). M4b nie spowodował ich — ale ekrany tego modułu są pierwszą powierzchnią publikowaną na dotyk, więc przejawiają się tu w pełnej ostrości. Naprawa to trzy linijki na poziomie tokenów i zdejmuje wszystkie trzy.

## 4. TOP-3 do naprawy przy merge (co sprawdzić najostroźniej w integracji)

1. **Trójka a11y z tokenów (BLOCKER #1+#3+#4)** — jeden commit w warstwie wspólnej: `--color-focus` → pełny `#93275D`; `--control-height` → 44px przy ≤900px; `.notice[data-tone="success"]` → `--success-strong`. Po nim: ręczny test Tab przez landing→form→upload (ring widoczny na każdym elemencie) + test dotykowy na telefonie + pomiar kontrastu notice'u sukcesu.
2. **Bindowanie błędów checkboxów (BLOCKER #2)** — dwa pola (zgoda szczegółowa, potwierdzenie daty): id + `aria-describedby` + `aria-invalid`; test: VoiceOver odczytuje błąd po submit bez zaznaczenia zgody; test fokus nie ucieka z formularza.
3. **RODO anti-enumeration + minimalizacja (SHOULD #5+#6)** — decyzja produktowa z ownerem (jedna kopia dead-link vs cztery; greeting z imieniem vs bez), potem prosty diff w `DeclarationScreens`+i18n. Do momentu decyzji: kopia przejściowa. Bez tej decyzji nie publikować linków produkcyjnych.

## 5. Co jest zgodne z designem — plusy (lokalizacja jakości)

- **A1 token-first CSS**: zero własnych hexów w całym module — po migracji brand zamienia się tokenem, bez dotykania plików M4b.
- **RODO engineering**: `robots noindex/follow=false` na stronie osobistej; `Cache-Control: no-store` na PDF i uploadzie; **token wyciszony z OTEL spans** (`suppressTracing` + komentarz-brama); brak logowania tokenu; mock dev-only, zero PII, deterministyczny.
- **Aria powyżej średniej**: `aria-current="step"` na stepperze, `aria-busy` na formularzu, `aria-live` regiony, `role=alert/status` na Notice, etykiety+helpery+errors powiązane na wszystkich inputach tekstowych, `lang` z layoutu.
- **PESEL wzorcowo**: poprawna checksuma (wagi 1,3,7,9,1,3,7,9,1,3), format, plus anty-literówkowy wzorzec „wyliczona data urodzenia + potwierdzenie” — wnoszę go jako **uzupełnienie prototypu 4a** (przyjmuję lepsze rozwiązanie implementacji do design systemu).
- **Kompletne stany uploadu** (typ/rozmiar/podpis/sieć) z zachowaniem pliku po błędzie — dokładnie wg SPEC §5.2; upload przez route handler świadomie (limit 1 MB server actions) i udokumentowane.
- **Wznawialność przepływu**: `initialStep` z statusu serwera (CREATED→landing, FILLED→upload, SIGNED→finale) — spełnia IA §3.4 („ponowne wejście po częściowym wypełnieniu”).
- **PDF inline iframe + otwarcie w nowej karcie** — lepsze od prototypu (Safari mobile nie renderuje PDF w iframe — fallback).
- **Klauzula informacyjna pełna** w `<details>` przed podpisem + te same treści w PDF — dokładne odwzorowanie obowiązku z dokumentu „Zgoda na przetwarzanie danych” (retencje C.1–C.4, odbiorcy, prawa, zakaz zautomatyzowanego podejmowania decyzji).
- **README z kontraktem M4a** (endpointy, mok, decyzje) — perfekcyjny materiał do review integracji.
- **Tolerancja kopii**: przestrzeń nazw PL+EN kompletna (G1 pass).

## 6. Rekomendacje dot. moich dokumentów (samokrytyka po pierwszym użyciu checklisty)

1. Dodaję punkt „F13: aria-required na polach wymaganych” do checklisty (dziś ukryte w B2).
2. Propozycja uzupełnienia prototypu 4a o potwierdzenie daty urodzenia (odwrotny transfer wiedzy, jak wyżej).
3. Słownik SPEC §5.2: proponuję **przyjąć słownik M4b** jako kanoniczny (naturalniejszy PL) i zarchiwizować różnice — zamiast wymuszać dosłowność (założenie „słownik co do litery” w G2 okazało się zbyt sztywne).

---

*Review wykonane na COMMIT 0ebbc11 + uncommitted M4b w worktree panel-recruitment-eform-web. Zero zmian w tamtym worktree; ten raport jest jedynym artefaktem po stronie panel-ux (plik `design/REVIEW-M4B.md`).*
