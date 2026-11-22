# IA — architektura informacji Panelu MR

**Scope:** trzy powierzchnie produktu: (a) aplikacja członkowska w Teams (5 zakładek), (b) back-office rekrutacji ZO/ZK (przeglądarka desktopowa), (c) publiczny flow deklaracji kandydata (mobile, anonimowy). Dla każdej: struktura, widoki, kolumny/filtry/akcje per rola, hygiene i mikrokopie.

Powiązane: [SPEC.md](SPEC.md) (tokeny/komponenty) · [STATES.md](STATES.md) (statusy/zegary/przejścia) · [REVIEW-CHECKLIST.md](REVIEW-CHECKLIST.md) (odbiór) · [prototypes/](prototypes/) (5 ekranów).

---

## 0. Zasady wspólne

1. **Trzy powierzchnie, jeden słownik.** Te same nazwy statusów, tych samych powodów odrzucenia i zegarów na wszystkich powierzchniach (tłumaczenia w [STATES.md](STATES.md)); na powierzchni publicznej słownik procesowy **nie występuje** (§3c).
2. **Model ról.** ZO = LOCAL (własny okręg), ZK = GLOBAL (wszystkie okręgi). Uprawnienia: `RECRUITMENT_READ` (widok), `RECRUITMENT_MANAGE` (akcje). Gating komponentów per STATES §4.4 — akcji nie ma wcale, nie jest szara.
3. **Drabina ekspozycji danych (RODO):**
   1. member Teams app — własne dane, minimalne podsumowania (składka, okręg, głosowania);
   2. back-office — dane kandydata jawne dla uprawnionych (READ w zakresie), PESEL maskowany, odsłonięcie tylko MANAGE;
   3. publiczny flow kandydata — **zero identyfikatorów**: powitanie bez imienia, błędy bez potwierdzania istnienia sprawy (§3c).
4. **Nawigacja:** w Teams pasek zakładek dostarcza host (Panel nie renderuje własnego tabbara); back-office żyje w pełnym Panelu (sidebar jak dziś: `components/shell/Sidebar.tsx`) z nową pozycją „Rekrutacja”; publiczny flow to strona samotna — bez menu, tylko stepper.
5. **Punkty wejścia:** Teams app = `app/(teams)/teams/*`; back-office = `/recruitment/*` w `(authorized)`; publiczny flow = `/d/[token]` w `(public)` (bez autoryzacji — obsługuje go sam token).

---

## 1. (a) Aplikacja członkowska w Teams — hygiene 5 zakładek

Struktura bez zmian: **Strona główna / Moje członkostwo / Okręg / Głosowania / Informacje** (`teams/{home,membership,district,votes,about}`). Celem tej fazy nie jest reorganizacja, tylko hygiene + odświeżenie hierarchii na „Strona główna” (prototyp 5).

### 1.1 Strona główna — układ kanoniczny (artboard „Teams-Panel”, Screen 1; prototyp: `screen-member-home.html`)

Struktura strony tożsama z makietą ownera; projektujemy **stany docelowe** modułów (głosowania, składki, ogłoszenia — dziś w kodzie `ComingSoon`, backend w fazach 2–3). Kolejność bloków od góry:

1. **Hero** (istniejący `Hero` z `TeamsBlocks`): H1 „Cześć, {imię}” + podtytuł członkowski + logo MR po prawej stronie hero (w produkcji: inline `mr-logo.svg` w `currentColor` = biel; greeting = `preferredNameFirst ?? memberName`). Podtytuł wg kanonu: „Członkini zwyczajna od marca 2024 · Okręg Mazowiecki” — w produkcji forma żeńska/męska generowana z rekordu; wariant neutralny: „Członkostwo zwyczajne od marca 2024 · Okręg Mazowiecki” (decyzja copy: owner).
2. **Baner kampanii** (nowy komponent, SPEC §4.13): pełnej szerokości, maroon #7A0E3E; overline w CAPS z rozstrzeleniem („GŁOSOWANIE OTWARTE · RADA KRAJOWA”), tytuł kampanii (przykład z kanonu: „Regulamin Forum Młodych Razem”), tekst deadline + biała pigułka „Zagłosuj →”. Brak aktywnej kampanii → **banner się nie renderuje** (żadnego „Wkrótce” na tej warstwie; informacje o przyszłym module zostają w kartach modułowych „Wkrótce”).
3. **Rząd trzech kart** (istniejąca struktura `CardGrid`):
   - **Składka** — pastelowa pigułka „Opłacona” (success tint + strong, z kropką) + „do 31 grudnia 2026” (termin kolejnej wpłaty) + link „Historia wpłat”;
   - **Mój Okręg** — nazwa okręgu + najbliższe wydarzenie w formacie kanonu („Zebranie 28 listopada, 12:00, Warszawa i Teams”; ikona kalendarza) + link „Szczegóły i zespół Okręgu”;
   - **Moje dane** — hint wniosku o zmianę (istniejąca kopia) + przycisk „Zgłoś zmianę danych”.
4. **Rząd drugi (2:1, `WideNarrowGrid`)**:
   - **Ogłoszenia** (karta szersza): link „Wszystkie” + do 3 pozycji w formacie karty kanonu — wiersz źródło · data (overline CAPS, np. „Zarząd Krajowy · 21.11.2026”), pod nim linkowany tytuł (np. „Raport skarbnika za październik”);
   - **„Coś poszło nie tak?”** (wąska): tekst incydentu (bez zmian), CZERWONA outline-pigułka „Zgłoś incydent” (ikona tarczy; `mailto:bezpieczenstwo@mlodzirazem.org`), pod nią maroon link „Pomoc IT: zgłoś sprawę”.

Zasady hygiene: nagłówki kart na powierzchni member Teams w CAPS z rozstrzeleniem (SPEC §4.3a, kanon makiety); pigułki statusowe = pastel + ciemny tekst (SPEC §2.3); karty białe na #F5F5F7, wyraźny radius-lg; akcent maroon #7A0E3E wyłącznie dla akcji/kampanii wg kanonu; karty mają max. 1 akcję primary + 1 link; zero pustych kart niejawnego stanu (brak okręgu → komunikat w treści karty, nie ukrywanie karty).

### 1.2 Moje członkostwo

Bez zmian struktury (istniejący `OwnRecord` współdzielony z `/user`). Hygiene: sekcje z jump-linkami („Dane kontaktowe”, „Dane formalne” z anchor `#own-formal` — istnieje), przyciski akcji wiersza per specyfikacja §4.7; „Złóż wniosek o zmianę” — primary; empty-state dla „Wnioski puste” wg SPEC §5.1.

### 1.3 Okręg

Widok członka: nazwa okręgu, kontakt do zarządu, odnośniki do dokumentów okręgowych. Widok członka zarządu okręgu (COORDINATOR/VICE): dodatkowo karta „Wnioski do zatwierdzenia” (istniejąca) — hygiene: licznik w tytule („Wnioski do zatwierdzenia (3)”), akcja otwiera Panel w przeglądarce (istniejący wzorzec: „Wniosek otworzy się w Panelu w przeglądarce”). **Back-office rekrutacji nie komplikujemy z tego widoku do zakładki Teams** (decyzja IA: rekrutacja desktop-only; Teams wskazuje skrót „Rekrutacja prowadzona jest w Panelu — otwórz w przeglądarce” tylko dla członków zarządu z uprawnieniem READ).

### 1.4 Głosowania

Bez zmian: treść „Wkrótce” + informacja o osobnym, szyfrowanym systemie (istniejąca kopia). Po landing modułu głosowań baner z §1.1 pkt 2 zastępuje kartę Wkrótce.

### 1.5 Informacje

Bez zmian: opis Panelu, privacy notice („Panel przetwarza dane szczególnej kategorii. Nie kopiuj danych osób członkowskich poza Panel.”), przydatne linki (Pomoc IT / engage, mailto bezpieczeństwo).

---

## 2. (b) Back-office ZO/ZK — desktop (prototypy 1–3)

### 2.1 Wejście i nawigacja

- Sidebar Panelu: nowa pozycja „Rekrutacja” (ikona karty z ludzką postacią), widoczna tylko dla ról z `RECRUITMENT_READ`; licznik spraw „zbliża się/po terminie” jako hint-badge obok etykiety (badge z zegarem — STATES §3.2).
- Route `/recruitment` → lista; `/recruitment/new` → nowa sprawa; `/recruitment/cases/[id]` → szczegóły. Breadcrumbs: „Panel / Rekrutacja / {Numer sprawy}”.
- Nagłówek strony: H1 „Rekrutacja” + podtytuł z zakresem roli („Okręg Mazowiecki” dla ZO; „Wszystkie okręgi” dla ZK) — jawne pokazanie scope to element RODO (użytkownik widzi granicę danych).

### 2.2 Lista spraw (prototyp 1: `screen-bo-cases.html`)

**Kolumny** (kolejność; kolumna priorytetowa = zawsze widoczna przy przewijaniu ≤900px):

| Kolumna | Zawartość | Uwagi |
|---|---|---|
| Numer | mono („S-000128”) | link do szczegółów; sortowalne |
| Kandydat | imię + nazwisko (gated READ) | sortowalne; druga linia: inicjały okręgu dla ZK („Okręg Mazowiecki” muted) |
| Status | Badge grupy semantycznej (STATES §2) | sortowalne; filtry grupowe |
| Zegar | Badge zegarowy najpilniejszy (STATES §3.2) | sortowalne po dacie terminu; „—” dla grupy I |
| Źródło | ikona + skrót („formularz”, „strona”, „ręcznie”) | — |
| Ścieżka oceny | skrót (Standardowa / Afiliowana / Zaangażowana) — po deklaracji | widoczne tylko gdy istnieje |
| Aktualizacja | „dziennie przez {autor}” / „22.11.2026” | sortowalne |

RODO: **na liście nie pokazujemy** e-maila, telefonu, daty urodzenia ani PESEL — dane kontaktowe tylko w szczegółach. (Minimalizacja + gęstość informacji.)

**Filtry** (pasek nad tabelą, sticky): Szukaj (imię/nazwisko/e-mail) · Status: dwupoziomowy select — optgroup grup fazowych (Kontakt/Okres wstępny/Decyzyjne/Terminalne) + pojedyncze statusy · Okręg: **tylko ZK** (ZO scope zaszyty) · Zegar: „Na czas / Zbliża się / Po terminie” · Źródło · Ścieżka. Aktywne filtry = chipy ×. „Wyczyść wszystko”. Wyniki: „Znaleziono 47 spraw” (aria-live polite).

**Sortowanie:** kandydat, termin zegara, aktualizacja (domyślnie: zegar rosnąco — najpilniejsze na górze).

**Paginacja:** 25/stronę; „Poprzednia / Strona 2 z 3 / Następna” z `aria-current`.

**Akcje w wierszu:** jedna — „Otwórz” (numer sprawy jest linkiem do szczegółów). Zero bulk-actions (proces jest kolegialny i indywidualny). Dla roli bez MANAGE wiersz wygląda identycznie (tylko podgląd); brak akcji edycji na liście w ogóle.

**Stany:** ładowania = skeleton wierszy; pusty = SPEC §5.1 (warianty: brak spraw / filtry / brak uprawnień); błąd = Notice + „Spróbuj ponownie”.

### 2.3 Szczegóły sprawy (prototyp 2: `screen-bo-case-detail.html`)

Layout dwukolumnowy (desktop): lewa kolumna szersza — treść operacyjna; prawa — historia i decyzje. ≤900px: jedna kolumna (kolejność jak niżej).

- **Nagłówek strony**: numer (mono) + imię i nazwisko kandydata (H1) · Badge statusu + Badge zegarowy · akcje: „Zmień status” (primary; tylko MANAGE), a dla statusu Przyjęta dodatkowo „Otwórz rekord członkowski”. Breadcrumb powrotu.
- **Karta „Dane kandydata”** (gated READ): e-mail, telefon, profil społecznościowy, data urodzenia (+ wiek w latach), ścieżka oceny, źródło. PESEL (od 0.0.7): mono, maska `••••••123`, przycisk odsłonięcia tylko MANAGE (wzorzec `RevealIdentifier`).
- **Karta „Deklaracja”**: forma (elektroniczna/skan), referencja/odnośnik do artefaktu (od 0.0.7), data otrzymania. W statusie AWAITING_DECLARATION: przycisk „Wyślij link do deklaracji” (MANAGE; potwierdzenie dialogiem, toast po wysłaniu); w stanie braku deklaracji: EmptyState „Deklaracja jeszcze nie wpłynęła”.
- **Karta „Zegary”** (STATES §3.1–3.2): wiersze Z-1..Z-3 z datami i badge'ami; Z-4 jako pasek informacyjny (AWAITING_DECLARATION: „Link wysłany 22.11.2026 — bez statutowego terminu”). Sekcja zawiera przyciski kontekstowe: „Przedłuż okres” (PROBATION; jednorazowe — STATES §4.3).
- **Karta „Notatki i przypomnienia”**: lista notatek (autor, data) + pole nowej notatki (MANAGE) + „Wyślij przypomnienie” (MANAGE; dialog potwierdzenia: „Wysłać przypomnienie do kandydata/kandydatki na adres e-mail? Dziś wyślemy {n}. e-mail.” — count-aware copy).
- **Panel/Zagłębienie „Decyzje kolegialne”** (prawa kolumna): tabela decyzji (ETYKIETA PL z STATES §3.4, sygnatura mono, data, organ, notka) + checklista kolegialności dla ZK_VOTING. Empty state: SPEC §5.1.
- **Timeline** (prawa kolumna, pod decyzjami): STATES §3.3; ostatni wpis u góry; „Wczytaj starszą” jeśli paginacja zdarzeń (25 events/page).

### 2.4 Nowa sprawa (prototyp 3: `screen-bo-new-case.html`)

Formularz (SPEC §4.7) na jednej stronie (bez wizarda — short form):

| Pole | Typ | Wymagane | Uwagi |
|---|---|---|---|
| Okręg | select | tak | ZO: zablokowany na własny (RO, z ikoną kłódki + „twój okręg”); ZK: wybór dowolnego |
| Źródło | select (GOOGLE_FORM/WEBSITE/MANUAL) | tak | domyślnie MANUAL |
| Imię | input | tak | autocomplete off (dane kandydata) |
| Nazwisko | input | tak | — |
| E-mail | input | tak | walidacja formatu; hint: „Na ten adres wyślemy link do deklaracji” |
| Telefon | input | nie | 9+ cyfr, opcjonalna prefiks „+” |
| Profil w social media | input | nie | URL |
| Data urodzenia | date | nie | walidacja wieku 16–26 (ORDINARY) z komunikatem SPEC §5.2 |
| Ścieżka oceny | select (Standardowa/Afiliowana/Zaangażowana) | nie | domyślnie Standardowa; helper: „Ścieżka wpływa na najwcześniejszy termin oceny (62/14/31 dni)” |
| Notatka początkowa | textarea | nie | zapisze się jako pierwsza notatka (event NOTE_ADDED po utworzeniu) |

Walidacja inline po blur + całej formy po submit; błędy wg SPEC §5.2; po zapisie: toast „Sprawę utworzono” + przekierowanie do szczegółów (status Nowa). Przy duplikacie (ten sam e-mail w otwartej sprawie w okręgu — `UNIQUE` w 0.0.6): błąd 409 → komunikat „Ten adres e-mail ma już otwartą sprawę w tym okręgu” z linkiem do niej (gating jak zwykle).

### 2.5 Mapowanie ról na akcje w back-office

| Widok/akcja | ZO READ | ZO MANAGE | ZK READ | ZK MANAGE |
|---|---|---|---|---|
| Lista + szczegóły | własny okręg | własny okręg | globalnie | globalnie |
| Filtr okręgu | ukryty | ukryty | widoczny | widoczny |
| Zmień status / decyzje / notatki / przypomnienia / wyślij link | nie | tak | nie | tak |
| Nowa sprawa | nie | tak (własny okręg) | nie | tak (dowolny okręg) |
| Odsłonięcie PESEL | nie | tak | nie | tak |

Zasada spójna z STATES §4.4: brak akcji = brak elementu (nie disabled).

---

## 3. (c) Publiczny flow kandydata — anonimowy, mobile (prototyp 4a/4b)

**Kontekst:** kandydat otrzymuje magic-link (e-mail od ZO). Sesja bez logowania — autoryzatorem jest token. Urządzenie: telefon, przeglądarka. Zero nawigacji globalnej; jedyna nawigacja to stepper wizarda.

### 3.1 Stany linku (RODO: komunikaty nie zdradzają niczego)

Wszystkie niedostępne stany linku (token nieprawidłowy / wygasły / sprawa zamknięta / sprawa anulowana) renderują **identyczny ekran** jedną kopią (SPEC §5.2 — anti-enumeration):

> **H1:** „Ten link nie działa”
> **Body:** „Link może być nieaktualny albo sprawa została już zakończona. Skontaktuj się z Zarządem Okręgu, który Ci go wysłał — poprosi o nowy.”
> Elementy: logo MR (neutralne), brak pól, brak mailto (adresat zna nadawcę — podpowiadanie adresu byłoby leak), brak licznika prób.

Ekran ma **jeden** przycisk: „Spróbuj ponownie” (odświeża stronę — może to być chwilowa awaria). To wszystko.

### 3.2 Wizard — kroki (stepper 4-krokowy, mobile)

```
(1) Start → (2) Twoje dane → (3) Dokumenty i podpis → (4) Gotowe
```

Stepper: kropki + nazwa bieżącego kroku; dostępny powrót „Wstecz” (bez utraty danych — draft trzymany po stronie serwera pod tokenem — decyzja dla M4a; w UI zachowujemy wartości pól po powrocie). Zakaz skipowania kroków.

**Krok 1 — Start („Zanim zaczniemy”):**
- powitanie bez danych kandydata: H1 „Deklaracja członkowska” + „Witaj! Wypełnisz tu swoją deklarację członkowską. Zajmie to ok. 10 minut.”
- Obowiązek informacyjny (art. 13/14 RODO): akordeon/sekcja z treścią obowiązku (administrator, IOD, cele i podstawy, retencja, prawa) — **tekst obowiązkowy do przedstawienia przed podpisaniem** (treść prawna z PDF Zgody — wprost, bez parafrazowania); wygodna rozwijana sekcja (details/summary).
- Informacja techniczna: „Twoje dane są przesyłane szyfrowanym kanałem (HTTPS) i nie są widoczne publicznie.”
- Przycisk primary: „Zaczynam”.

**Krok 2 — Twoje dane (prototyp 4a):** pola z deklaracji (A. Sekcja: imię i imiona urzędowe, nazwisko urzędowe, preferowane imię/nazwisko, PESEL (mono, 11 cyfr, checksum), zaimki, telefon, e-mail; opcjonalnie: uczelnia, adres korespondencji — sekcja rozwijana „Chcę podać adres do korespondencji” wg decyzji Q4 (mapping-report); dla kandydatów <16 — gałąź rodzica (Q3 otwarte — nie implementować w M4b domyślnie; UI tylko przy decyzji)). Autocomplete wg przeznaczenia; błędy inline (SPEC §5.2); zapis draftu po każdej sekcji.

**Krok 3 — Dokumenty i podpis (prototyp 4b):**
1. „Rzuć okiem na dokument” — podgląd/„Pobierz PDF” (deklaracja + zgoda), template version wskazana subtelnie (mono, small: „ver. 2024-09”).
2. „Podpisz dokument” — instrukcja 3 kroków (pobierz → podpisz kwalifikowanym podpisem elektronicznym u swojego dostawcy → wgraj podpisany PDF); link do FAQ dostawców wg decyzji Q2 (bez preferencji dostawcy w UI).
3. „Wgraj podpisany dokument” — upload (PDF, ≤10 MB) — dropzone + pola; stany: wybrany plik (nazwa, rozmiar, ikona pdf, „Usuń” ghost), błąd formatu/rozmiaru (SPEC §5.2), postęp (progress bar bez procentów animowanych >1fps — reduced motion).
4. Oświadczenie + zgody: checkboxy obowiązkowe (zgoda na przetwarzanie danych szczególnej kategorii — art. 9 ust. 2 lit. d) i dobrowolna (wizerunek, wycofywalna) — z jawnym zaznaczeniem „(dobrowolna)”.
5. Przycisk primary: „Wyślij deklarację” (disabled dopóki plik i zgody obowiązkowe niekompletne — wtedy disabled z `aria-disabled` i helperem „Wgraj podpisany PDF i zaznacz wymagane zgody”).

**Krok 4 — Gotowe:**
- H1 „Dziękujemy! Deklaracja została wysłana.” + body: „Zarząd Okręgu potwierdzi przyjęcie i skontaktuje się z Tobą.” + Pigułka success „Przyjęte 22.11.2026”.
- Bez dalszych akcji nie wynikających z procesu: strona statyczna, bez linków w głąb Panelu; dodatkowa wskazówka: „Ten link przestanie działać — zachowaj potwierdzenie (np. zrzut ekranu)”. Fallback papierowy: „Nie dało się podpisać elektronicznie? Wyślij skan pocztą na adres podany przez Twój zarząd okręgu.” (bez adresu w UI — właściwy adres dodaje ZO w dedykowanym mailu; RODO: żadnej listy adresów na stronie publicznej).

### 3.3 Zasady RODO w publicznym flow

- Brak imienia w powitaniu („Witaj!”), brak echa adresu e-mail, brak jakichkolwiek identyfikatorów w URL-ach (token w path, nic więcej; parametry UTM nie współdzielone z zewnętrznymi skryptami — **brak third-party tracking**).
- Błędy walidacji nie zdradzają istnienia konta w systemie (gwint „ten e-mail już jest członkiem” — nie ma publicznie).
- Po sukcesie token jest spalany — odświeżenie strony pokazuje standardowy ekran „link nie działa” (§3.1).

### 3.4 Stany wizarda poza happy-path

| Stan | UX |
|---|---|
| Wygaśnięcie sesji/draftu w trakcie | ekran §3.1 z dodatkowym zdaniem: „Wypełnione dane nie przepadły — poproś zarząd okręgu o nowy link.” (zachęta bez winy) |
| Upload przerwany | retention pliku lokalnie (input zachowuje plik) + „Spróbuj ponownie” |
| Błąd serwera | SPEC §5.2 (500/503) + „Spróbuj ponownie”; dane formularza nie giną |
| Ponowne wejście po częściowym wypełnieniu | pola wstępnie wypełnione z draftu (token powiązany) — kandydat widzi swoje dane (swoje = nie PII innej osoby) |

---

## 4. Mapowanie IA → moduły M2b/M4b (co review'ujemy w kodzie)

| Element IA | Oczekiwany plik (propozycja) | Moduł | Kryterium odbioru |
|---|---|---|---|
| Strona główna: układ kanoniczny (hero → baner kampanii → Składka/Okręg/Dane → Ogłoszenia/Pomoc) | `app/(teams)/teams/home/page.tsx` + TeamsBlocks (+ nowy `CampaignBanner`) | (hygiene) | REVIEW §„Member home” + §I5 |
| Lista spraw: kolumny/filtry/paginacja/stany | `app/(authorized)/recruitment/page.tsx` (+ komponenty w `components/recruitment/`) | M2b | REVIEW §„Lista spraw” |
| Szczegóły: karty + timeline + decyzje + zegary | `app/(authorized)/recruitment/cases/[id]/page.tsx` | M2b (timeline) + M3/M4 (decyzje, zegary po DECLARATION_RECEIVED — patrz granice modułów w `RecruitmentWorkflowService`) | REVIEW §„Szczegóły” |
| Nowa sprawa | `app/(authorized)/recruitment/new/page.tsx` | M2b | REVIEW §„Nowa sprawa” |
| Publiczny wizard + stany linku | `app/(public)/d/[token]/page.tsx` | M4b | REVIEW §„Flow kandydata” |
| Upload deklaracji | jak wyżej + server action | M4b / M4a | REVIEW §„Upload” |
| Tokeny/kolory/badges/dialogi | `components/recruitment/*`, SCSS modułowe | wszystkie | REVIEW §„Tokeny i komponenty” |

Uwaga dla scalania: przejście AWAITING_DECLARATION → DECLARATION_RECEIVED jest własnością M4a/M4b (publiczny link); M2b ma tylko UI akcji „Wyślij link”. Review granicy: M2b NIE implementuje walidacji deklaracji, tylko widok miejsca akcji. Zegary zaczynają istnieć po `DECLARATION_RECORDED` — w grupie I karta „Zegary” pokazuje stan informacyjny („Zegary ruszą po zapisaniu deklaracji”), nie pustkę.

## 5. Kwestie otwarte, na które IA zakłada odpowiedź ownera (nie blokują odbioru)

| Pytanie | Wpływ na IA | Założenie tymczasowe |
|---|---|---|
| Q4 (adres w deklaracji) | sekcja opcjonalna w kroku 2 | collapse „Chcę podać adres…” |
| Q6 (konto vs magic-link) | cały flow §3 | magic-link (zgodnie z decyzją ownera) |
| Q2 (dostawca QES) | treść kroku 3 | neutralnie: „u swojego dostawcy podpisu kwalifikowanego” |
| Q3 (kandydaci <16) | gałąź rodzica | nie renderować do decyzji |
| Q1 (retencja REJECTED) | tekst w szczegółach sprawy zamkniętej | „po 30 dniach dane sprawy zostaną zanonimizowane” |

## 6. Powierzchnie przyszłości (świadomie poza zakresem projektowania v1)

- **Post aplikacji na kanale Teams** (kontekst: artboard Screen 2 makiety — zwołanie zebrania okręgu na kanale „Okręg Mazowiecki / Ogólny”: badge „Aplikacja”, maroon banner OKRĘG MAZOWIECKI · ZWOŁANIE ZEBRANIA, karta zebrania z terminem/miejscem/porządkiem, przyciski pełny maroon „Otwórz w Panelu MR” + outline „Dodaj do kalendarza”, reakcje/odpowiedzi). Zakres nieprzekuty jeszcze z ownerem — **nie projektuję**; notuję, że kolory, pigułki i typografia z tego SPEC nadają się bez zmian (baner = wariant CampaignBanner §4.13, karta = Card, przyciski = Button primary/outline). Gdy scope dojdzie, IA tego ekranu to karta zebrania czytana z kalendarza Panelu.

---

*Koniec IA. Elementy dziedzinowe (statusy, zegary, reason-selecty, dialogi) — patrz STATES.md; poziom komponentów — SPEC.md §4.*
