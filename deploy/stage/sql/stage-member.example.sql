-- stage-member.example.sql — dodaje JEDNEGO członka do bazy stage (RUNBOOK §10.1).
--
-- Po co: stage nie ma dev-seedów, więc konto Microsoft 365 nie ma z kim się
-- połączyć („Your account is not linked”). panel-api pobiera z Microsoft Graph
-- employeeId konta Entra i szuka członka o tym numerze (members.member_id,
-- porównanie liczbowe). Dlatego NUMER musi być prawdziwy i równy employeeId.
-- Pozostałe pola mogą być minimalne albo pseudonimowe, bo stage ma trzymać dane
-- syntetyczne (§10). Nazwisko i data urodzenia nie biorą udziału w łączeniu.
--
-- Statut: wpisz co najwyżej JEDEN mandat, który osoba faktycznie pełni.
-- Kolejne mandaty nadaje inna osoba w Panelu, który pilnuje par niepołączalnych
-- (np. ZK i ZO) i zakazu samonadania. Ten skrypt to wyłącznie bootstrap.
--
-- Użycie na VPS (szczegóły: RUNBOOK §10.1):
--   nano ~/stage-member.sql        # wklej ten plik, uzupełnij blok WYPEŁNIJ
--   microk8s kubectl -n stage exec -i cnpg-1 -c postgres -- psql -U postgres -d mrpanel < ~/stage-member.sql
--   rm ~/stage-member.sql
-- Skrypt działa w jednej transakcji: pierwszy błąd przerywa go bez zmian w bazie.

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';

-- ============================== WYPEŁNIJ ==============================
-- Komentarze muszą stać w osobnych liniach: psql skleja wszystko po \set.
--
-- numer: 6 cyfr, identyczny z employeeId konta w Entra.
\set numer '000000'
\set imie 'Imię'
\set nazwisko 'Nazwisko'
\set data_urodzenia '2000-01-01'
-- typ_czlonkostwa: ORDINARY (16–26 lat) albo SUPPORTING.
\set typ_czlonkostwa 'ORDINARY'
-- okreg: okręg główny, np. 'Okręg Mazowiecki'; czlonek_od: początek przynależności.
\set okreg 'Okręg Mazowiecki'
\set czlonek_od '2026-01-01'
-- Mandat (opcjonalny; puste organ = bez mandatu, tylko „Moje członkostwo”).
-- organ = nazwa istniejącego organu z tabeli bodies; rola = KOD roli, nie nazwa funkcji:
--   'Zarząd Krajowy'                        CO_CHAIR | VICE_CHAIR | SECRETARY -> National board (GLOBAL)
--   'Zarząd Krajowy'                        TREASURER                         -> National board treasurer (GLOBAL)
--   'Zarząd Okręgu Mazowieckiego' (itd.)    COORDINATOR | VICE_COORDINATOR    -> District board (LOCAL)
--   'Osoba Skarbnicza Okręgu Mazowieckiego' TREASURER                         -> District treasurer (LOCAL)
--   sądy, komisje krajowe                   CHAIR | MEMBER | ...              -> No permissions
-- Świeża baza stage ma tylko organy krajowe i okręgowe; komisji i zespołów
-- (np. IT) nie ma, a model nie zna roli pełnomocnika ZK.
\set organ ''
\set rola ''
\set mandat_od '2026-01-01'
-- uchwala: sygnatura uchwały powołującej (opcjonalnie).
\set uchwala ''
-- ======================================================================

BEGIN;

CREATE FUNCTION pg_temp.wymagaj(warunek boolean, komunikat text) RETURNS void
    LANGUAGE plpgsql AS
$$
BEGIN
    IF warunek IS NOT TRUE THEN
        RAISE EXCEPTION '%', komunikat;
    END IF;
END
$$;

SELECT pg_temp.wymagaj(:'numer' ~ '^[0-9]{6}$' AND :'numer' <> '000000',
                       'numer: wpisz swoje 6 cyfr (takie jak employeeId w Entra)') \g /dev/null
SELECT pg_temp.wymagaj(:'numer' < '950000',
                       'numer: 950000–999999 to pasma fixtures i trial, nie prawdziwe numery') \g /dev/null
SELECT pg_temp.wymagaj(NOT EXISTS (SELECT 1 FROM members WHERE member_id = :'numer'),
                       'członek o numerze ' || :'numer' || ' już jest w bazie stage') \g /dev/null
SELECT pg_temp.wymagaj(EXISTS (SELECT 1 FROM bodies WHERE name = :'okreg'),
                       'nie ma okręgu: ' || :'okreg') \g /dev/null

INSERT INTO members (status, member_id,
                     preferred_name_first, preferred_name_last,
                     formal_name_first, formal_name_last,
                     domain_email, domain_email_aliases, private_emails,
                     birth_date, membership_type)
VALUES ('ACTIVE', :'numer',
        :'imie', :'nazwisko',
        :'imie', :'nazwisko',
        :'numer' || '@mlodzirazem.org', '{}', '{}',
        :'data_urodzenia', :'typ_czlonkostwa');

INSERT INTO member_district_affiliations (member_id, district_body_id, kind, votes_here, valid_from)
SELECT m.id, b.id, 'PRIMARY', TRUE, :'czlonek_od'::date
FROM members m
         JOIN bodies b ON b.name = :'okreg'
WHERE m.member_id = :'numer';

SELECT :'organ' <> '' AS z_mandatem \gset
\if :z_mandatem
SELECT pg_temp.wymagaj(EXISTS (SELECT 1 FROM bodies WHERE name = :'organ'),
                       'nie ma organu „' || :'organ' || '” na stage; organy krajowe: '
                           || (SELECT string_agg(name, ', ' ORDER BY name) FROM bodies WHERE parent_body_id IS NULL
                                                                                       AND name NOT LIKE 'Okręg %')
                           || '; okręgowe mają nazwy typu „Zarząd Okręgu Mazowieckiego”') \g /dev/null
SELECT pg_temp.wymagaj(EXISTS (SELECT 1
                               FROM bodies b
                                        JOIN body_template_member_types mt
                                             ON mt.body_template_id = b.body_template_id AND mt.code = :'rola'
                               WHERE b.name = :'organ'),
                       'nie ma roli „' || :'rola' || '” w organie „' || :'organ' || '”; dostępne kody: '
                           || (SELECT string_agg(mt.code, ', ' ORDER BY mt.code)
                               FROM bodies b
                                        JOIN body_template_member_types mt ON mt.body_template_id = b.body_template_id
                               WHERE b.name = :'organ')) \g /dev/null

INSERT INTO body_memberships (member_id, body_id, member_type_id, valid_from, decision_reference)
SELECT m.id, b.id, mt.id, :'mandat_od'::date, NULLIF(:'uchwala', '')
FROM members m
         JOIN bodies b ON b.name = :'organ'
         JOIN body_template_member_types mt ON mt.body_template_id = b.body_template_id AND mt.code = :'rola'
WHERE m.member_id = :'numer';
\endif

-- Podgląd tego, co zostanie zapisane.
SELECT m.member_id,
       m.status,
       m.membership_type,
       (SELECT string_agg(b.name, ', ')
        FROM member_district_affiliations a
                 JOIN bodies b ON b.id = a.district_body_id
        WHERE a.member_id = m.id)                                        AS okreg,
       (SELECT string_agg(b.name || ' / ' || mt.code || ' -> ' || pg.name, ', ')
        FROM body_memberships bm
                 JOIN bodies b ON b.id = bm.body_id
                 JOIN body_template_member_types mt ON mt.id = bm.member_type_id
                 JOIN permission_group pg ON pg.id = mt.permission_group_id
        WHERE bm.member_id = m.id)                                       AS mandat
FROM members m
WHERE m.member_id = :'numer';

COMMIT;
