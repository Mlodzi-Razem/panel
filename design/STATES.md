# STATES — statusy sprawy, zegary statutowe i wzorce przejść

**Scope:** moduł rekrutacji (M2b back-office + M4b publiczny flow). Źródła prawdziwych nazw: `RecruitmentCaseStatus`, `RecruitmentRejectionReason`, `RecruitmentRules` (transition map), migracja `0.0.6`. Wszystkie etykiety PL poniżej są obowiązującym słownikiem dla kodu M2b/M4b.

Powiązane: [SPEC.md](SPEC.md) (tokeny kolorów) · [IA.md](IA.md) (gdzie statusy występują) · [REVIEW-CHECKLIST.md](REVIEW-CHECKLIST.md) (jak odebrać implementację).

---

## 1. Zasada nadrzędna: kolor = faza, tekst = status

Maszyna ma **11 statusów** — projektujemy **4 grupy semantyczne + 2 stany terminalne w grupie**, a nie 11 odcieni. Kolor odpowiada na pytanie „czego ta sprawa teraz wymaga od organu”, a dokładny status nazywa tekst badge'a (plus ikona; kolor nigdy nie jest jedynym nośnikiem — WCAG 1.4.1).

| Grupa | Statusy | Kolor (fg/bg) | Znaczenie operacyjne |
|---|---|---|---|
| **I. Kontakt** | NEW, IN_PROGRESS, NO_CONTACT, AWAITING_DECLARATION | info — `--status-contact-*` | sprawą zajmuje się ZO; czekamy na kontakt kandydata lub deklarację |
| **II. Okres wstępny** | DECLARATION_RECEIVED, PROBATION, PROBATION_EXTENDED | warning — `--status-probation-*` | biegną zegary statutowe; odpowiedzialność: ZO (ocena) + informowanie ZK |
| **III. Decyzyjne** | ZO_RECOMMENDED, ZK_VOTING | brand — `--status-decision-*` | wymagane decyzje kolegialne: rekomendacja/uchwała ZO, głosowanie ZK |
| **IV. Terminalne** | ACCEPTED (success) · REJECTED (danger) | `--status-accepted-*` / `--status-rejected-*` | sprawa zamknięta; ACCEPTED ma rekord członkowski; REJECTED ma powód |

## 2. Słownik 11 statusów (jedyny dozwolony zestaw etykiet)

| Enum (backend) | Etykieta PL (badge) | Grupa | Ikona | Opis dla członka zarządu (helper w szczegółach) | Co dalej (możliwe) |
|---|---|---|---|---|---|
| `NEW` | Nowa | I. Kontakt | ✉ koperta | Sprawa zgłoszona, nikt się nią jeszcze nie zajął. | w toku · brak kontaktu · odrzucona |
| `IN_PROGRESS` | W toku | I. Kontakt | ➜ strzałka | ZO prowadzi rozmowę z kandydatem/kandydatką. | oczekuje na deklarację · brak kontaktu · odrzucona |
| `NO_CONTACT` | Brak kontaktu | I. Kontakt | ⏸ pauza | Kandydat nie odpowiada; sprawa czeka, ale nie wygasła. | w toku · odrzucona |
| `AWAITING_DECLARATION` | Oczekuje na deklarację | I. Kontakt | 📄 dokument | Wysłaliśmy kandydatowi link do e-deklaracji. Czekamy na wypełnienie i podpis. | deklaracja odebrana · odrzucona |
| `DECLARATION_RECEIVED` | Deklaracja odebrana | II. Okres wstępny | ✓(kontur) dokument | Deklaracja zapisana; rusza bieg okresu wstępnego (zegary poniżej). Startuje faza wstępna. | okres wstępny |
| `PROBATION` | Okres wstępny | II. Okres wstępny | ⏳ klepsydra | Trwa okres wstępny; ocena ZO możliwa w oknie 62–76 dni od deklaracji (lub krócej dla tracków AFFILIATED/ENGAGED). | rekomendacja ZO · przedłużenie · odrzucona |
| `PROBATION_EXTENDED` | Okres przedłużony | II. Okres wstępny | ⏳(+1) klepsydra | Okres wstępny przedłużony (jednorazowo, max +31 dni). Druga ocena w wyznaczonym terminie. | rekomendacja ZO · odrzucona |
| `ZO_RECOMMENDED` | Rekomendacja ZO | III. Decyzyjne | 🏛 kolumna | ZO rekomenduje przyjęcie; uchwała o przyjęciu w ciągu 7 dni od oceny pozytywnej; sprawę przejmuje ZK. | głosowanie ZK |
| `ZK_VOTING` | Głosowanie ZK | III. Decyzyjne | 🗳 urna | ZK głosuje nad przyjęciem. | przyjęta · odrzucona |
| `ACCEPTED` | Przyjęta | IV. Terminalne (success) | ✓(pełny) tarcza | Kandydat/przyjęty ma numer członkowski i rekord w rejestrze. Sprawa zamknięta. | — |
| `REJECTED` | Odrzucona | IV. Terminalne (danger) | ✕ krzyż | Sprawa zakończona odmownie; obowiązkowy powód odrzucenia. | — (po 30 dniach dane sprawy ulegną zanonimizowaniu) |

Zasady użycia etykiet: badge pokazuje **etykietę krótką**; w helperze dozwolone zdanie pełne. Zakaz: mieszanie enumu z PL w UI („Status: PROBATION” — nie; „Status: Okres wstępny” — tak). W audytach/logach zostaje enum — to nie jest UI.

## 3. Zegary statutowe

### 3.1 Inwentarz zegarów (specyfika domenowa, nie wymyślamy własnych)

| Zegar | Dotyczy statusów | Pole bazowe | Wzór | Charakter |
|---|---|---|---|---|
| Z-1 *Okno oceny ZO* | PROBATION | `assessment_not_before_on` → `assessment_not_later_on` | 62–76 dni od deklaracji (track: STANDARD 62 / AFFILIATED ≥14 / ENGAGED ≥31; górna granica wspólna: +76) | SOFT |
| Z-2 *Okno oceny po przedłużeniu* | PROBATION_EXTENDED | `second_assessment_not_later_on` | max +31 dni od decyzji o przedłużeniu (przedłużenie jednorazowe) | SOFT |
| Z-3 *Uchwała o przyjęciu* | ZO_RECOMMENDED | `admission_resolution_not_later_on` | ≤7 dni od pozytywnej oceny (rekomendacji) | SOFT |
| Z-4 *Deklaracja (oczekiwanie)* | AWAITING_DECLARATION | brak pola daty w 0.0.6 — liczone od wysłania linku (data wysłania linku z M4a) | orientacyjne, informacyjne | SOFT, bez badge'a „po terminie” — brak statutowego terminu maksymalnego; przypomnienie ręczne ZO |

**SOFT = widoczny, nieblokujący:** po przekroczeniu terminu maksymalnego system **nie ukrywa i nie blokuje** akcji przejścia — pokazuje badge „Po terminie” i pozwala działać dalej (decyzja należy do organu kolegialnego, nie do software'u). Kopia: „Po terminie (nieblokujący)”. Moment założenia zegarów: Z-1 przy zapisaniu deklaracji (event `DECLARATION_RECORDED`), Z-2 przy decyzji o przedłużeniu (`PERIOD_EXTENSION`), Z-3 przy rekomendacji (event `RECOMMENDATION_SENT`).

### 3.2 Badge zegarowy — stany (jeden wzorzec dla Z-1..Z-3)

| Stan | Warunek | Etykieta (z datą) | Kolor |
|---|---|---|---|
| **CZEKA** (dotyczy Z-1 przed otwarciem okna) | dziś < `not_before` | „Ocena możliwa od 12.01” | info tint (`--status-contact-*`) — zegar jeszcze nie pilnuje |
| **NA CZAS** | okno otwarte, do terminu > 7 dni | „Na czas — do 19.01” | success tint (`--clock-ok-*`) |
| **ZBLIŻA SIĘ** | do terminu ≤ 7 dni | „Zbliża się — 3 dni do 19.01” | warning tint (`--clock-soon-*`) |
| **PO TERMINIE (soft)** | dziś > termin maksymalny | „Po terminie od 19.01 (nieblokujący)” | danger tint (`--clock-over-*`) — ale NIE error formularza; ikona zegara z wykrzyknikiem |

Zasady:
- Na liście spraw wiersz pokazuje **jeden** badge zegarowy — najpilniejszy z aktywnych (priorytet: PO TERMINIE > ZBLIŻA SIĘ > NA CZAS > CZEKA; przy remisie — zegar z wcześniejszym terminem maksymalnym). Tooltip (`title`/`aria-describedby`) wymienia pozostałe aktywne zegary.
- W szczegółach sprawy: sekcja „Zegary” — osobne wiersze per zegar, z datami `not_before/not_later` widocznymi + badge. Zegary nieaktywne dla danego statusu (np. Z-3 w statusie PROBATION) — uśpione („rozpocznie się po rekomendacji”), nie ukryte całkowicie, bo pokazują proces.
- W statusach grupy I zegarów brak — kolumna zegarowa na liście pokazuje „—” (nie em-dash tekstowy, tylko pusta komórka z aria-label „brak zegarów”).
- Zegary badge to **osobny element od badge'a statusu**: status PROBATION (warning) + zegar NA CZAS (success) to poprawne, częste zestawienie; czytelność zapewniają kropka i ikona zegara.

### 3.3 Timeline „co było” (przeszłość, nie prognoza)

Timeline w szczegółach sprawy pokazuje historię zdarzeń z `recruitment_case_events` (mapowanie poniżej) i **nie** pokazuje przyszłych statusów (te prezentują zegary i przyciski przejść).

| Event (enum) | Etykieta PL | Ikona |
|---|---|---|
| `CASE_CREATED` | Sprawa utworzona | ✉ |
| `STATUS_CHANGED` | Zmiana statusu: {poprzedni} → {nowy} | ➜ |
| `REMINDER_SENT` | Przypomnienie wysłane do kandydata/kandydatki | 🔔 |
| `ZK_NOTIFIED_PROBATION_START` | ZK powiadomione o początku okresu wstępnego | 🏛 |
| `DECLARATION_RECORDED` | Deklaracja zapisana ({form: elektroniczna/skan}) — zaczyna okres wstępny | 📄✓ |
| `RECOMMENDATION_SENT` | Rekomendacja ZO wysłana do ZK | 🏛➜ |
| `VOTE_STARTED` | Głosowanie ZK rozpoczęte | 🗳 |
| `DECISION_RECORDED` | Zapisano decyzję kolegialną {kind} (sygnatura {sig}, data) | ✒ |
| `CASE_CLOSED` | Sprawa zamknięta ({Przyjęta/Odrzucona}) | ✓/✕ |
| `NOTE_ADDED` | Notatka dodana | 💬 |

Wzór wpisu: ikona w kółku → etykieta PL + `(autor — data)` → treść (dla `NOTE_ADDED`: treść notatki; dla `DECLARATION_RECORDED`: forma deklaracji). Payloady zgodne z konwencją AuditPayload: same nazwy pól, bez wartości wrażliwych. **RODO:** w payloadach zdarzeń nigdy nie powtarzamy pełnego adresu e-mail ani PESEL; treść notatek może zawierać dane osobowe — widoczność notatek jest taka sama jak panelu „Dane kandydata” (gating uprawnieniem).

### 3.4 Decyzje kolegialne (panel „Decyzje” w szczegółach)

| Kind (enum) | Etykieta PL | Kto widzi | Wymogi forma |
|---|---|---|---|
| `ZO_ASSESSMENT_POSITIVE` | Ocena ZO — pozytywna | RECRUITMENT_READ | sygnatura + data uchwały (mono) |
| `ZO_ASSESSMENT_NEGATIVE` | Ocena ZO — negatywna | RECRUITMENT_READ | jw. |
| `PERIOD_EXTENSION` | Przedłużenie okresu wstępnego | RECRUITMENT_READ | data decyzji + data drugiej oceny (≤31 dni) |
| `REJECTION` | Odrzucenie | RECRUITMENT_READ | powód (reason) + opcjonalna notatka |
| `ZK_APPROVAL` | Opinia ZK | RECRUITMENT_READ | sygnatura protokołu ZK + data; dla przychylności milczącej domyślna notka: „termin minął bez opinii — art. 9 ust. 4 pkt 6” |
| `ZO_ADMISSION_RESOLUTION` | Uchwała ZO o przyjęciu | RECRUITMENT_READ | sygnatura uchwały (np. ZO/MZ/01/2026) + data (≤7 dni od oceny — badge zegarowy Z-3) |

Trigger PRZYJĘTA **wymaga obu**: `ZK_APPROVAL` + `ZO_ADMISSION_RESOLUTION` (trigger w 0.0.6). UI: w statusie ZK_VOTING panel decyzji pokazuje checklistę kolegialności: „Opinia ZK: zapisana / oczekuje · Uchwała ZO: zapisana / oczekuje” + Notice informujący o brakującym elemencie, jeśli użytkownik próbuje przejść do ACCEPTED. (Przejście do ACCEPTED jest dostępne wyłącznie ze statusu ZK_VOTING i zawsze ze sprawdzeniem kompletu decyzji po stronie API.)

## 4. Przejścia — wzorce UX

### 4.1 Pełna mapa przejść (z `RecruitmentRules.TRANSITIONS`)

| Ze stanu | Dozwolone cele | Uwagi UX |
|---|---|---|
| NEW | IN_PROGRESS, NO_CONTACT, REJECTED | REJECTED wymaga powodu (`FALSE_ENTRY`) |
| IN_PROGRESS | AWAITING_DECLARATION, NO_CONTACT, REJECTED | REJECTED wymaga powodu (`NOT_FIT`) |
| NO_CONTACT | IN_PROGRESS, REJECTED | REJECTED wymaga powodu (`NO_CONTACT`) |
| AWAITING_DECLARATION | DECLARATION_RECEIVED, REJECTED | DECLARATION_RECEIVED = zapisanie deklaracji (M4a weryfikuje); REJECTED: powód `NO_DECLARATION` |
| DECLARATION_RECEIVED | PROBATION | automatyczne po `DECLARATION_RECORDED` — w UI pokazywane jako potwierdzenie „Deklaracja zapisana — okres wstępny rozpoczęty”; bez ręcznego selecta |
| PROBATION | PROBATION_EXTENDED, ZO_RECOMMENDED, REJECTED | przedłużenie: jednorazowe (dialog pokazuje „wykorzystane”, gdy extension_count=1); REJECTED: `NOT_FIT` lub `ZK_BAN` |
| PROBATION_EXTENDED | ZO_RECOMMENDED, REJECTED | przed drugą oceną; REJECTED: `NOT_FIT` lub `ZK_BAN` |
| ZO_RECOMMENDED | ZK_VOTING | wejście do głosowania; wymaga rekomendacji i uchwały ZO (checklista kolegialności) |
| ZK_VOTING | ACCEPTED, REJECTED | ACCEPTED: trigger sprawdza komplet decyzji; REJECTED: `VOTE_FAILED` lub `ZK_BAN` |
| ACCEPTED | — | brak przejść; przycisk szczegółów „Otwórz rekord członkowski” |
| REJECTED | — | brak przejść; informacja o retencji („po 30 dniach dane sprawnie zostaną zanonimizowane”) |

### 4.2 Powody odrzucenia — słownik PL (per status źródłowy)

| Reason (enum) | Etykieta PL w selectcie | Dostępny ze statusów | Dialog tekst pomocniczy |
|---|---|---|---|
| `FALSE_ENTRY` | Wpis omyłkowy lub duplikat | NEW | „Sprawa nie dotyczy prawdziwej kandydatury.” |
| `NOT_FIT` | Nie spełnia wymagań (ocena) | IN_PROGRESS, PROBATION, PROBATION_EXTENDED | „Kandydat/kandydatka nie spełnia wymogów okresu wstępnego albo rozmowy wstępnej.” |
| `NO_CONTACT` | Brak kontaktu | NO_CONTACT | „Kandydat/kandydatka nie odpowiada na próby kontaktu.” |
| `NO_DECLARATION` | Deklaracja nie wpłynęła | AWAITING_DECLARATION | „Link wygasł, a deklaracja nie została wysłana.” |
| `VOTE_FAILED` | Głosowanie ZK negatywne | ZK_VOTING | „ZK nie przyjął kandydatury w głosowaniu.” |
| `ZK_BAN` | Zakaz przyjęcia (art. 9 ust. 5) | PROBATION, PROBATION_EXTENDED, ZK_VOTING | „Obowiązuje zakaz przyjęcia z uchwały ZK.” |

Select powodu w dialogu REJECTED: **obowiązkowy** (`aria-required`), aktualnie dostępne opcje wg statusu źródłowego (tabela wyżej), bez opcji grupy „…”; walidacja inline „Wybierz powód odrzucenia.” Pole notatki: opcjonalne, maxlength 2000 znaków (helper „Notatka trafi do historii sprawy”).

### 4.3 Wzorzec dialogu przejścia (wszystkie zmiany statusu ≥ znaczące)

1. **Trigger**: przycisk z jawną etykietą akcji, nie ikona sama; w RO (tylko RECRUITMENT_READ) — ukryty (nie szary).
2. **Dialog** (spec §4.8): tytuł „Zmień status sprawy” lub akcja konkretna („Zapisz deklarację”, „Rekomenduj przyjęcie”, „Rozpocznij głosowanie ZK”, „Odrzuć sprawę”); Notice o skutkach (zegary, powiadomienia ZK); select celu (tylko dozwolone); reason select (dla REJECTED); notatka opcjonalna; [Anuluj] [Akcja — destrukcyjny danger przy REJECTED].
3. **Kopie ostrzegawcze**: przejścia nieodwracalne → „Tej czynności nie można cofnąć. Zmiana zapisze się w historii sprawy.” Przedłużenie → „Przedłużenie jest możliwe tylko raz. Do kiedy zaplanować drugą ocenę?” (date input, max +31 dni). Przyjęcie → „System przydzieli numer członkowski i utworzy rekord członkowski.”
4. **Konflikt 409** (równoległa zmiana): wg SPEC §5.2 („Status sprawy się zmienił…”). State nie wymusza reload: „Odśwież szczegóły” — akcja w notice.
5. **Powołane przejście automatyczne** (DECLARATION_RECEIVED → PROBATION): bez dialogu; toast „Deklaracja zapisana. Okres wstępny rozpoczęty.” + przekierowanie na szczegół.

### 4.4 Macierz widoczności akcji per rola i uprawnienie

| Akcja | ZO (RECRUITMENT_READ, LOCAL okręg) | ZO (RECRUITMENT_MANAGE, LOCAL) | ZK (READ lub MANAGE, GLOBAL) |
|---|---|---|---|
| Przegląd listy i szczegółów spraw | ✓ własny okręg | ✓ własny okręg | ✓ wszystkie okręgi |
| Dane kandydata (PII: e-mail, telefon, data urodzenia, PESEL*) | ✓ | ✓ | ✓ |
| Wgląd PESEL (maska → odsłonięcie) | maska | ✓ (przycisk odsłonięcia) | ✓ |
| Zmiana statusu / decyzje / notatki / przypomnienia | tylko podgląd (RO) | ✓ | ✓ (globalnie) |
| Zakładanie sprawy | — | ✓ (własny okręg) | ✓ (dowolny okręg) |
| Filtr okręgu na liście | ukryty (scope zaszyty) | ukryty (scope zaszyty) | ✓ |

\* PESEL kandydata pojawia się w modelu dopiero w M4 (0.0.7); w 0.0.6 na sprawie jest data urodzenia. UI projektujemy pod pełny zestaw: pole z maską + odsłonięciem dla MANAGE.

Gating w UI: **nie renderujemy** przycisku akcji bez uprawnienia (nie disabled — patrz SPEC §4.8: nie straszymy stanami niedostępnymi). Rola ZK bez MANAGE zachowuje się jak ZO-READ, ale w zakresie globalnym.

## 5. Zasady RODO w warstwie statusów

- Statusy, zegary i przyczyny odrzucenia **są** informacjami procesowymi widocznymi w back-office zgodnie z §4.4 (uprawnienie + zakres okręgu).
- W publicznym flow kandydata nie pokazujemy statusu maszyny ani nazw powodów — kandydat widzi tylko własne stany wizarda (IA §3c).
- Komunikat dead-link nie zdradza, czy kiedykolwiek istniała sprawa (unifikacja treści — IA §3c).
- `REJECTED` + retencja: w szczegółach sprawy zamkniętej i po anonimizacji widoczna jest szkieletowa karta „Sprawa zanonimizowana (RODO)” — bez danych kandydata; timeline bez treści notatek (same nagłówki zdarzeń).

---
*Koniec STATES. Checklist odbioru: REVIEW-CHECKLIST §Kolumny statusów/zegary + §Dialogi przejść.*
