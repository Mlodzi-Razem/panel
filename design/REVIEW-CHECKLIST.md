# REVIEW-CHECKLIST — odbiór kodu M2b / M4b po merge

**Cel:** mechaniczna, binarna weryfikacja scalonego kodu (M2b: back-office rekrutacji ZO; M4b: publiczny flow deklaracji) względem [SPEC.md](SPEC.md), [IA.md](IA.md), [STATES.md](STATES.md) i prototypów. Każdy punkt to **✅ (tak) / ❌ (nie)** — bez „w zasadzie”. Jeśli punkt nie dotyczy review'owanego modułu, wpisz „n/d”.

**Jak sprawdzać (narzędzia):**
- **grep** — wartości literalne w stylach: `grep -rn "#[0-9a-fA-F]\{3,8\}" services/panel-web/components/recruitment services/panel-web/app/(authorized)/recruitment services/panel-web/app/(public)/d` → jedyne trafienia dozwolone: brak (odwołania przez `var(--…)`).
- **DevTools** — Computed → sprawdź parę fg/bg; kontrast porównaj z tabelą SPEC §3 (albo wtyczka typu kontrast-checker).
- **Klawiatura** — Tab/Shift+Tab/Esc przez cały flow; fokus widoczny na każdym elemencie.
- **Zoom 200%** (ustawienie przeglądarki) — brak utraty treści i funkcji.
- **Czytnik ekranu / aria** — VoiceOver (klawisz cmd+5); każda lista formuje do nawigacji po tabelach/listach.

---

## A. Tokeny i style (wszystkie nowe pliki M2b/M4b)

- [ ] **A1.** Żaden nowy plik SCSS/TSX nie deklaruje koloru hex/rgba inline — wszystko przez `var(--…)` (grep jak wyżej; wyjątek: cienie w tokenach).
- [ ] **A2.** Nowy kod używa `--brand-*`; `--plum-*` nie pojawia się w nowych plikach (grep `-rn "plum-"` w nowych ścieżkach = 0 trafień).
- [ ] **A3.** `--color-primary` to #7A0E3E; hover przycisku primary to `--brand-800` #680C35 (DevTools: hover).
- [ ] **A4.** Teksty/komunikaty success używają `--success-strong` (#14663A); `#1E8E4E`/`--color-success` nie występuje jako kolor tekstu (grep + DevTools).
- [ ] **A5.** `--color-orange #E8590C` nie jest nigdzie kolorem tekstu ani tłem badge'a z białym tekstem.
- [ ] **A6.** Badge statusu to pastel: tło tint + tekst strong + kropka; **nie ma** solid-badge z białym tekstem size <18.66px, poza dozwolonymi tonami info/danger/brand (SPEC §2.3).
- [ ] **A7.** Focus ring: pełny kolor `--color-focus` (#93275D, minimum 2px, offset 2px); brak `outline: none`/`outline: 0` bez pełnoprawnego zamiennika (grep `outline: none|outline:0`).
- [ ] **A8.** Spacing tylko wielokrotności 4px (tokeny `--space-*`); promienie tylko z tokenów; brak magicznych wartości (przegląd pliku).
- [ ] **A9.** Cienie tylko `--shadow-1/2/3`; z-index tylko `--z-sticky/popover/overlay` (grep `z-index`).
- [ ] **A10.** Motion: przejścia max 180ms ease-in-out; istnieje blok `prefers-reduced-motion: reduce` wyłączający animacje (grep `prefers-reduced-motion`).
- [ ] **A11.** Media query breakpoints: wyłącznie `900px` (grep `@media` — brak wymyślnych punktów).

## B. Stany komponentów (M2b: lista, szczegół, nowa; M4b: wizard)

- [ ] **B1.** Lista spraw ma wizarda stanów: **ładowanie** (skeleton, `aria-busy="true"`, tekst sr-only), **pusty** (3 warianty: brak spraw / filtry bez wyników / brak uprawnień — SPEC §5.1), **błąd** (Notice danger + „Spróbuj ponownie”, `role="alert"`).
- [ ] **B2.** Każdy formularz: etykieta widoczna dla każdego pola, helper pod polem (`aria-describedby`), błąd inline (`aria-invalid`, ikona + tekst, `role="alert"` lub `aria-live`), stan disabled (`aria-disabled`, `.45` opacity), stan tylko-do-odczytu (rola READ — pola RO, bez przycisków zapisu; **przyciski akcji w ogóle nie są renderowane**).
- [ ] **B3.** Przyciski: stan ładowania (spinner + `aria-busy`, etykieta nie znika), hover na tokeny SPEC §4.1, kursor `not-allowed` na disabled.
- [ ] **B4.** Dialog (natywny `<dialog>`): `aria-labelledby` tytułu, ESC zamyka, fokus wraca do elementu wywołującego, overlay z `--shadow-3`; treści wg STATES §4.3 (kopia „Tej czynności nie można cofnąć…”).
- [ ] **B5.** Upload (M4b): stany — wybrany plik (nazwa+rozmiar+Usuń), błąd formatu („To nie jest PDF”), błąd rozmiaru (>10 MB), postęp, retry; plik NIE ginie po błędzie sieci.
- [ ] **B6.** Toast wyłącznie dla potwierdzeń („Zapisano.” / „Sprawę utworzono” · `role="status"`); błędy nigdy toastem.
- [ ] **B7.** EmptyState'y mają dokładnie strukturę SPEC §4.10 (ikona dekoracyjna `aria-hidden` + H3 + body + 1 akcja) i teksty słownika SPEC §5.1.

## C. Accessibility (per ekran)

- [ ] **C1.** Wszystkie pary tekst/tło w nowych widokach występują w tabeli SPEC §3 z ratio ≥4.5 (lub ≥3 dla non-text) — sprawdzić DevTools przy każdym nowym elemencie.
- [ ] **C2.** Cały flow przechodzi klawiaturą; focus widoczny na każdym interaktywnym elemencie (w tym wiersze/linki w tabeli).
- [ ] **C3.** Cele dotykowe ≥44×44px na ≤900px (DevTools: inspect → box), odstęp między celami ≥8px.
- [ ] **C4.** Inputy/selecty ≥16px na ekranach dotykowych (iOS bez auto-zoomu).
- [ ] **C5.** `<html lang="pl">` na każdej stronie, także publicznej.
- [ ] **C6.** Licznik wyników filtrów `aria-live="polite"`; sortowanie `aria-sort`; paginacja `aria-current="page"`.
- [ ] **C7.** Zoom 200%: treść czytelna, tabele przewijalne, nic nie zasłonięte.
- [ ] **C8.** Informacja nie niesie tylko koloru: badge ma kropkę/ikonę + tekst; błąd pola ma ikonę + tekst.
- [ ] **C9.** Link „Przejdź do zawartości” (skip link) na stronach back-office.

## D. Statusy, zegary, przejścia (M2b)

- [ ] **D1.** Etykiety 11 statusów dokładnie ze słownika STATES §2 („Nowa / W toku / Brak kontaktu / Oczekuje na deklarację / Deklaracja odebrana / Okres wstępny / Okres przedłużony / Rekomendacja ZO / Głosowanie ZK / Przyjęta / Odrzucona”); enum nie wycieka do UI (grep `PROBATION|ZK_VOTING` w warstwie widoku — tylko mapowania).
- [ ] **D2.** Kolory statusów: wyłącznie 4 grupy + 2 terminalne wg STATES §1 (pary `--status-*`), zero 11 odcieni.
- [ ] **D3.** Badge zegarowy: 4 stany (czeka/na czas/zbliża się/po terminie) i kopia „(nieblokujący)” dla PO TERMINIE; ikona zegara + tekst.
- [ ] **D4.** Zegary SOFT: po przekroczeniu terminu akcje przejścia **pozostają dostępne** (test: przesunąć datę w fiksturze — akcja dalej działa, badge się zmienia).
- [ ] **D5.** Lista: kolumna zegarowa pokazuje najpilniejszy zegar, tooltip/aria opisuje pozostałe; dla grupy I — pusta komórka z aria-label, nie mylący „—”.
- [ ] **D6.** Timeline: wszystkie 10 typów zdarzeń mapowane na etykiety STATES §3.3; payload bez pełnych danych wrażliwych (brak pełnego e-maila/PESEL w payloadach).
- [ ] **D7.** Decyzje kolegialne: tabela decyzji z sygnaturą (font mono) i datą; w ZK_VOTING checklista kolegialności; notka przychylności milczącej ZK to dokładnie konstanta `TACIT_ZK_APPROVAL_NOTE`.
- [ ] **D8.** Dialog przejścia: select celu **renderuje wyłącznie** cele dozwolone z `RecruitmentRules.TRANSITIONS` dla bieżącego statusu (test na statusach granicznych: PROBATION_EXTENDED → {ZO_RECOMMENDED, REJECTED}; DECLARATION_RECEIVED → {PROBATION}).
- [ ] **D9.** Dla REJECTED select powodu **obowiązkowy**; opcje tylko z `REJECTION_REASONS` dla statusu źródłowego (STATES §4.2); walidacja inline „Wybierz powód odrzucenia.”
- [ ] **D10.** Przedłużenie: dostępne tylko przy `extension_count = 0`; dialog pokazuje limit +31 dni i ikonę „tylko raz”.
- [ ] **D11.** Akcje gated: bez uprawnienia element nie istnieje (nie disabled) — test na rolach 950001 (ZO MANAGE), 950002 (ZO wice), 950004 (ZK), 950003 (ZO innego okręgu — brak dostępu cross-district).

## E. Kolumny/filtry/scope listy (M2b)

- [ ] **E1.** Kolumny listy dokładnie wg IA §2.2 (Numer / Kandydat / Status / Zegar / Źródło / Ścieżka / Aktualizacja); **kontaktów i PESEL na liście brak**.
- [ ] **E2.** Filtr Okręgu widoczny tylko dla roli GLOBAL (ZK); dla ZO scope zaszyty i podany w podtytule nagłówka.
- [ ] **E3.** Filtr Statusu: optgroupy grup fazowych + pojedyncze statusy (STATES §1); chipy aktywne z „×” ≥44px; „Wyczyść wszystko”.
- [ ] **E4.** Paginacja: 25/stronę, `aria-current`, przyciski krańcowe disabled.
- [ ] **E5.** Sortowanie domyślne: najpilniejszy zegar rosnąco; sortowalne kolumny mają `aria-sort`.

## F. Publiczny flow kandydata (M4b — kod wylądował w osobnym worktree ze starą stylistyką; po merge porównać 1:1 z prototypami `screen-candidate-form.html` i `screen-candidate-upload.html`)

**Tryb porównania:** funkcje przenosimy bez zmian, wygląd/stany/aria dosprawdzamy względem prototypów i tokenów SPEC. Każdy punkt F ma swój odpowiednik wizualny w prototypach 4a/4b — odhaczamy z dowodem (zrzut albo plik+linia).


- [ ] **F1.** Stany nieosiągalnego linku (nieprawidłowy/wygasły/zamknięty/spalony po sukcesie) → **identyczny** ekran z dokładną kopią SPEC §5.2/IA §3.1 — bez mailto, bez imienia, bez rozbicionych komunikatów per przypadek.
- [ ] **F2.** Zero identyfikatorów: powitanie „Witaj!” (bez imienia), brak echa e-maila/PESEL w headingach, brak numeru sprawy w URL-ach poza tokenem, brak third-party skryptów (network tab: brak zewnętrznych requestów).
- [ ] **F3.** Obowiązek informacyjny dostępny przed krokiem podpisu (treść z PDF Zgoda, wprost); zgody: wymagana (dane szczególnej kategorii) z walidacją i oznaczona gwiazdką, wizerunkowa z dopiskiem „(dobrowolna — możesz ją wycofać w każdej chwili)”.
- [ ] **F4.** Upload: walidacja PDF + 10 MB po stronie klienta i serwera; komunikaty dokładnie z SPEC §5.2; przycisk „Wyślij deklarację” disabled (`aria-disabled` + helper wyjaśnia warunek).
- [ ] **F5.** Stepper 4-krokowy „Start / Twoje dane / Dokumenty i podpis / Gotowe” — bez skipowania; powrót „Wstecz” zachowuje dane (draft lokalny lub draft serwerowy).
- [ ] **F6.** Walidacja PESEL (checksum, 11 cyfr) z komunikatem SPEC §5.2; błędy nie potwierdzają istnienia sprawy ani konta.
- [ ] **F7.** Ekran sukcesu: kopia SPEC §5.3 + informacja o unieważnieniu tokena; brak linków w głąb Panelu.
- [ ] **F8.** Routing i stany linku odpowiadają IA §3.1 (route `/d/[token]`; wszystkie stany niedostępne → jeden ekran, jedna kopia); navigation test: wejście po tokenie zużytym i błędnym daje identyczny DOM poza ewentualnym „Spróbuj ponownie”.
- [ ] **F9.** Krok „Twoje dane”: pola i kolejność 1:1 z `screen-candidate-form.html` (imiona/nazwisko urzędowe → preferowane → PESEL mono z checksumą → zaimki → telefon → e-mail → uczelnia; adres jako sekcja rozwijana „Chcę podać adres do korespondencji”); brak pól spoza mapowania (bez „miejscowość wypełnienia” — Q5).
- [ ] **F10.** Krok „Dokumenty i podpis”: struktura 1:1 z `screen-candidate-upload.html` (pobierz 2 PDF-y → instrukcja podpisu 3 punkty → upload + stany pliku → zgody); przyciski elastyczne wg §4.1; zgoda wizerunkowa z dopiskiem „(dobrowolna…)”.
- [ ] **F11.** Restylizacja M4b po scaleniu: style wylądowanego M4b przeniesione na tokeny SPEC (sekcja A dotyczy M4b w pełni po re-style; diff starej stylistyki → nowej w PR).
- [ ] **F12.** Zero JS-owych assumptions z prototypów: prototypy są statyczne — interakcja (draft, walidacje, sortowanie) może być rozbudowana, ale kopie/stany/aria muszą odpowiadać 1:1 prototypom.
- [ ] **F13.** Pola wymagane mają atrybut `required`/`aria-required="true"` (sama gwiazdka `aria-hidden` nie spełnia wymogu). *(Dodane po pierwszym użyciu checklisty — review M4b, patrz REVIEW-M4B.md §6.1.)*

## G. Mikrokopie i słowniki

- [ ] **G1.** Wszystkie teksty PL pochodzą z i18n (`messages-pl.ts` namespace modułu); brak stringów w komponentach (grep `"pl"` / hardcoded Polish w TSX).
- [ ] **G2.** Teksty empty/błąd/toast 1:1 ze słownikami SPEC §5.1–§5.3 (diff tekstów po kluczach).
- [ ] **G3.** Mapowanie kodów API → teksty wg SPEC §5.2; surowe kody/stack nie wyciekają do UI (dozwolone `data-error-code`).
- [ ] **G4.** Ton: brak żargonu („rekord”, „endpoint”, ISO-date w UI), format daty dd.mm.rrrr.

## H. RODO exposure

- [ ] **H1.** PII kandydata (e-mail, telefon, profil społecznościowy, data urodzenia, PESEL) widoczne tylko w back-office z `RECRUITMENT_READ` w zakresie; testy ról 950001/950003/950004 (scope LOCAL/GLOBAL).
- [ ] **H2.** PESEL maskowany (`••••••••123`-pattern); przycisk odsłonięcia renderowany tylko przy `RECRUITMENT_MANAGE` (wzorzec `RevealIdentifier`); odsłonięcie audytowane (jeśli implementowane).
- [ ] **H3.** Lista spraw bez danych kontaktowych (E1 pokrywa — odhaczyć po oględzinie widoku z rolą READ).
- [ ] **H4.** Public flow: minimum danych (F1–F3); brak echo w błędach; brak third-party tracking (network).
- [ ] **H5.** Sprawa zamknięta + zanonimizowana renderuje szkielet bez danych kandydata (STATES §5).

## I. Zgodność z prototypami (wizualna)

- [ ] **I1.** Lista spraw: układ i hierarchia zgodna z `prototypes/screen-bo-cases.html` (toolbar → tabela → paginacja; referencja stanów pod tabelą).
- [ ] **I2.** Szczegół: sekcje i kolejność zgodne z `prototypes/screen-bo-case-detail.html` (nagłówek z badge'ami → dane → deklaracja → zegary → notatki **|** decyzje → timeline).
- [ ] **I3.** Nowa sprawa: pola i stany błędów zgodne z `prototypes/screen-bo-new-case.html`.
- [ ] **I4.** Flow kandydata: kroki/stany zgodne z `prototypes/screen-candidate-form.html` i `screen-candidate-upload.html`.
- [ ] **I5.** Member home: układ kanoniczny makiety „Teams-Panel” (hero z podtytułem i logiem po prawej → maroon baner kampanii z białą pigułką „Zagłosuj →” → rząd Składka/Mój Okręg/Moje dane → rząd Ogłoszenia(Pomoc) 2:1), zgodny z `prototypes/screen-member-home.html`; nagłówki kart w CAPS z rozstrzeleniem (SPEC §4.3a) *(dotyczy hygiene, jeśli merge dotyka zakładki)*.

## J. Świadomie POZA zakresem tego review (nie oznaczać jako błędy)

- Pełny dark mode (tokeny są forward-ready: SPEC §2.8; weryfikacja tylko czy nowy kod nie blokuje zamiany: inline `rgba` zakaz — pokrywa A1).
- Nowa biblioteka ikon (dozwolony Phosphor już w zależnościach).
- Implementacja głosowań/składek (moduły przyszłe).
- Packaging Teams/manifest.

## K. Higiena danych

- [ ] **K1.** Zero realnych PII w testach, fiksturach i zrzutach: wyłącznie persony 950001–950012 / fikcyjne kandydackie z pasma testowego; PESEL-e testowe spełniają checksum albo są jawnie błędne.
- [ ] **K2.** Brak danych produkcyjnych w treściach zgłoszeń/dokumentacji tego modułu.

---

*Sposób użycia: skopiować checklistę do RPC/PR review; każdy punkt ✅/❌/n/d + link do dowodu (zrzut/linia). Blokery merge: jakikolwiek ❌ w sekcjach A (tokeny), C1–C3 (kontrast/fokus/dotyk), D1–D4 (statusy/zegary), F1–F2 (RODO public), H1–H2 (RODO PII).*
