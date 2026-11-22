-- dev/seed/test-personas.sql — role and edge-case personas for local development
-- (recruitment-module fixtures per the analyst packet of 2026-11-22).
--
-- PERSONAS (all fabricated — GDPR-safe, see the README section on test people):
--   950001 Adam Testowy        ACTIVE  ZO Mazowieckiego coordinator      (District board, LOCAL)
--   950002 Beata Przykładowa   ACTIVE  ZO Mazowieckiego vice-coordinator (District board, LOCAL)
--   950003 Cezary Testowy      ACTIVE  ZO Dolnośląski coordinator       (District board, LOCAL) — isolation fixture:
--                              coordinator of district A must NOT see district B's people
--   950004 Dorota Przykładowa  ACTIVE  Zarząd Krajowy co-chair          (National board, GLOBAL)
--   950005 Edmund Testowy      ACTIVE  Zarząd Krajowy treasurer         (National board treasurer: FEES, BODIES_MANAGE)
--   950006 Felicja Skarbnicowa ACTIVE  Osoba Skarbnicza Okręgu Mazowieckiego (District treasurer:
--                              CONTACT_PERSONAL_DATA_READ + FEES_READ in the district)
--   950007 Grażyna Probacyjna PROBATION   initial-period clock fixture, declaration 65 days ago
--   950008 Henryk Deklaracyjny FORM_REQUEST declaration 10 days ago
--   950009 Irena Zawieszona    SUSPENDED   + suspensions row issued by ZO Mazowieckiego
--   950010 Janusz Wspierający  ACTIVE, membership_type SUPPORTING (adult)
--   950011 Katarzyna Rocznikowa ACTIVE, turns 26 in December of the current year ("ending-this-year" report)
--   950012 Leon Wykreślony    REMOVED
--
-- MEMBER-ID CONVENTION: every synthetic person added here uses the FIXTURE
-- band 950000–969999 (950001…), the sweepable subrange of the six-digit space
-- (dev/README.md §7). Real member numbers come only from the allocator below
-- 950000 (migration 0.0.6 caps seq_member_number_allocation at MAXVALUE 949999);
-- 970000–999999 is reserved for PROVISIONAL ids of REAL trial-period candidates
-- (Entra UPN/employeeId only, no members row until PRZYJĘTA) — never create
-- fixtures there and never purge that range by band. The legacy fixtures
-- 000205/000368 in test-members.sql PREDATE this convention and predate the
-- recruitment work — they stay untouched there (a live SSO test session
-- depends on them).
--
-- STATUTORY CONSTANTS (Statut MR, tekst jednolity z 3.10.2026; for comments and
-- future recruitment tests — the clock fixtures above exist so these windows are
-- testable the moment the module lands):
--   * okres wstępny: 62–76 dni od złożenia deklaracji        [art. 9 ust. 4 pkt 4]
--   * jednorazowe wydłużenie okresu (ZO albo ZK) + wtórna ocena w ≤ 31 dni [art. 9 ust. 4 pkt 5–7]
--   * uchwała o przyjęciu podejmuje ZO w ≤ 7 dni od pozytywnej oceny [art. 9 ust. 4 pkt 8];
--     test rows mimic board resolutions like suspensions.decision_signature below
--   * członkostwo zwyczajne: wiek 16–26                       [art. 7 ust. 1]
--   * członkostwo wspierające: wiek 16+                       [art. 8 ust. 1 pkt 1]
--   UWAGA (known divergence, to surface only in tests — a code/PO decision, do NOT
--   "fix" here): MemberRules.java today allows SUPPORTING from age 14
--   (SUPPORTING_MIN_AGE = 14), statut 2026 says 16+. 950010 is an adult, so both
--   readings accept it; write a testcase documenting the divergence itself.
--
-- Prerequisite: panel-api must have booted against this database at least once —
-- Flyway migrations create the tables and seed the bodies. This seed only uses
-- objects from migrations 0.0.1..0.0.5; the recruitment tables (migration 0.0.6,
-- recruitment_cases/recruitment_case_events/recruitment_decisions/admission_bans)
-- are NOT seeded here — per the members contract, a candidate never gets a
-- members row before the "PRZYJĘTA" policy decision, and their intake fixtures
-- belong to the e-declaration work (M4).
--
-- Idempotent: safe to re-run (ON CONFLICT DO NOTHING / NOT EXISTS, one transaction).
-- Look up bodies, templates and member types BY NAME/CODE — never by id (fresh-DB
-- ids like 13 / 42–44 are artefacts of seed order and can shift).

BEGIN;

-- ---------------------------------------------------------------- members ---
-- Same column conventions as test-members.sql:
--   * member_id is the six-digit number stored as TEXT;
--   * preferred_name (and the combined preferred_name) are GENERATED — never insert them;
--   * status enum: ACTIVE, REMOVED, SUSPENDED, FORM_REQUEST, PROBATION;
--   * e-mail uniqueness is enforced across members by the member_emails sync trigger.
INSERT INTO members (status, member_id,
                     preferred_name_first, preferred_name_last,
                     formal_name_first, formal_name_last,
                     domain_email, domain_email_aliases, private_emails,
                     birth_date, membership_type,
                     probation_period_start_date, probation_period_end_date, request_date,
                     removed_at)
VALUES -- role personas
       ('ACTIVE', '950001',
        'Adam', 'Testowy',
        'Adam', 'Testowy',
        '950001@mlodzirazem.org', '{}', '{}',
        DATE '2006-06-15', 'ORDINARY', NULL, NULL, NULL, NULL),
       ('ACTIVE', '950002',
        'Beata', 'Przykładowa',
        'Beata', 'Przykładowa',
        '950002@mlodzirazem.org', '{}', '{}',
        DATE '2004-10-05', 'ORDINARY', NULL, NULL, NULL, NULL),
       ('ACTIVE', '950003',
        'Cezary', 'Testowy',
        'Cezary', 'Testowy',
        '950003@mlodzirazem.org', '{}', '{}',
        DATE '2005-01-28', 'ORDINARY', NULL, NULL, NULL, NULL),
       ('ACTIVE', '950004',
        'Dorota', 'Przykładowa',
        'Dorota', 'Przykładowa',
        '950004@mlodzirazem.org', '{}', '{}',
        DATE '2002-09-12', 'ORDINARY', NULL, NULL, NULL, NULL),
       ('ACTIVE', '950005',
        'Edmund', 'Testowy',
        'Edmund', 'Testowy',
        '950005@mlodzirazem.org', '{}', '{}',
        DATE '2001-04-30', 'ORDINARY', NULL, NULL, NULL, NULL),
       ('ACTIVE', '950006',
        'Felicja', 'Skarbnicowa',
        'Felicja', 'Skarbnicowa',
        '950006@mlodzirazem.org', '{}', '{}',
        DATE '2003-07-08', 'ORDINARY', NULL, NULL, NULL, NULL),
       -- edge-case personas
       ('PROBATION', '950007',
        'Grażyna', 'Probacyjna',
        'Grażyna', 'Probacyjna',
        '950007@mlodzirazem.org', '{}', '{}',
        DATE '2006-02-14', 'ORDINARY',
        -- declaration 65 days ago: day 62–76 of the initial period (62–76 dni od
        -- deklaracji) => the assessment window is OPEN right now, testable immediately
        -- after the initial-period clock is implemented.
        CURRENT_DATE - 65, CURRENT_DATE - 65 + 76, CURRENT_DATE - 65, NULL),
       ('FORM_REQUEST', '950008',
        'Henryk', 'Deklaracyjny',
        'Henryk', 'Deklaracyjny',
        '950008@mlodzirazem.org', '{}', '{}',
        DATE '2008-05-19', 'ORDINARY',
        NULL, NULL, CURRENT_DATE - 10, NULL),
       ('SUSPENDED', '950009',
        'Irena', 'Zawieszona',
        'Irena', 'Zawieszona',
        '950009@mlodzirazem.org', '{}', '{}',
        DATE '2003-12-03', 'ORDINARY', NULL, NULL, NULL, NULL),
       ('ACTIVE', '950010',
        'Janusz', 'Wspierający',
        'Janusz', 'Wspierający',
        '950010@mlodzirazem.org', '{}', '{}',
        -- adult SUPPORTING: fine under both statut 16+ and MemberRules' 14+ (see header note)
        DATE '1996-08-21', 'SUPPORTING', NULL, NULL, NULL, NULL),
       ('ACTIVE', '950011',
        'Katarzyna', 'Rocznikowa',
        'Katarzyna', 'Rocznikowa',
        '950011@mlodzirazem.org', '{}', '{}',
        -- born 15 Dec (current_year − 26): turns 26 on 15 December of the current
        -- year => the "membership ends this year" report fixture, fresh every year.
        make_date((EXTRACT(YEAR FROM CURRENT_DATE))::int - 26, 12, 15), 'ORDINARY',
        NULL, NULL, NULL, NULL),
       ('REMOVED', '950012',
        'Leon', 'Wykreślony',
        'Leon', 'Wykreślony',
        '950012@mlodzirazem.org', '{}', '{}',
        DATE '2000-03-07', 'ORDINARY', NULL, NULL, NULL, CURRENT_DATE - 14)
ON CONFLICT (member_id) DO NOTHING;

-- ---------------------------------------------- district affiliations (PRIMARY) ---
-- District bodies are seeded by migration V0.0.3.01 ('Okręg ' || nominative);
-- resolve ids BY NAME, never hardcode (in a fresh DB Okręg Mazowiecki = 13).
-- Role personas get the district they operate in; 950009 (Irena) belongs to
-- Mazowsze too — the suspending body below is her own district board. The
-- arbiter matches member_district_affiliations_open_district_idx (see
-- test-members.sql).
INSERT INTO member_district_affiliations (member_id, district_body_id, kind, votes_here, valid_from)
SELECT m.id, ob.id, 'PRIMARY', TRUE, DATE '2026-01-01'
FROM (VALUES ('950001', 'Okręg Mazowiecki'),
             ('950002', 'Okręg Mazowiecki'),
             ('950003', 'Okręg Dolnośląski'),
             ('950006', 'Okręg Mazowiecki'),
             ('950009', 'Okręg Mazowiecki')) AS seeded (member_id, district_name)
         JOIN members m ON m.member_id = seeded.member_id
         JOIN bodies ob ON ob.name = seeded.district_name
ON CONFLICT (member_id, district_body_id) WHERE valid_to IS NULL DO NOTHING;

-- ------------------------------------------------ body memberships (mandates) ---
-- Member types resolve through the body's own template (body_templates.name =
-- 'Zarząd Okręgu' etc. × body_template_member_types.code) — the same tables the
-- api uses. Resulting permission groups (seeded with V0.0.3.01):
--   COORDINATOR / VICE_COORDINATOR on a district board  -> 'District board'
--     (LOCAL): FORMAL + CONTACT personal-data read/manage inside that district —
--     these are the recruitment-pipeline operators;
--   CO_CHAIR on Zarząd Krajowy                          -> 'National board' (GLOBAL);
--   TREASURER on Zarząd Krajowy                         -> 'National board treasurer'
--     (GLOBAL): FORMAL/CONTACT read+manage, FEES_READ/FEES_MANAGE, BODIES_MANAGE,
--     DIRECTORY_*_MANAGE — for ZK approvals and the fees integration;
--   TREASURER on Osoba Skarbnicza Okręgu                -> 'District treasurer'
--     (LOCAL): CONTACT_PERSONAL_DATA_READ + FEES_READ.
-- Arbiter matches body_memberships_open_unique_idx
-- (member_id, body_id, member_type_id) WHERE valid_to IS NULL.
INSERT INTO body_memberships (member_id, body_id, member_type_id, valid_from)
SELECT m.id, b.id, mt.id, DATE '2026-01-01'
FROM (VALUES ('950001', 'Zarząd Okręgu Mazowieckiego', 'COORDINATOR'),
             ('950002', 'Zarząd Okręgu Mazowieckiego', 'VICE_COORDINATOR'),
             ('950003', 'Zarząd Okręgu Dolnośląskiego', 'COORDINATOR'),
             ('950004', 'Zarząd Krajowy', 'CO_CHAIR'),
             ('950005', 'Zarząd Krajowy', 'TREASURER'),
             ('950006', 'Osoba Skarbnicza Okręgu Mazowieckiego', 'TREASURER')) AS seeded (member_id, body_name, type_code)
         JOIN members m ON m.member_id = seeded.member_id
         JOIN bodies b ON b.name = seeded.body_name
         JOIN body_template_member_types mt ON mt.body_template_id = b.body_template_id AND mt.code = seeded.type_code
ON CONFLICT (member_id, body_id, member_type_id) WHERE valid_to IS NULL DO NOTHING;

-- ---------------------------------------------------------------- suspension ---
-- Suspended member with a documented suspension issued by her district board.
-- The board body is looked up BY NAME ('Zarząd Okręgu Mazowieckiego',
-- code district-mazowieckie-board). decision_signature follows the board-resolution
-- pattern that recruitment decisions (uchwała o przyjęciu, ≤ 7 dni od oceny,
-- art. 9 ust. 4 pkt 8) are expected to copy. suspensions has no natural unique
-- key, so idempotency uses NOT EXISTS instead of ON CONFLICT.
INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start, decision_signature)
SELECT m.id, zb.id, CURRENT_DATE - 30, 'ZO/MZ/01/2026'
FROM members m
         JOIN bodies zb ON zb.name = 'Zarząd Okręgu Mazowieckiego'
WHERE m.member_id = '950009'
  AND NOT EXISTS (SELECT 1
                  FROM suspensions s
                  WHERE s.suspended_user_id = m.id
                    AND s.suspending_body_id = zb.id
                    AND s.decision_signature = 'ZO/MZ/01/2026');

COMMIT;

-- ------------------------------------------- post-seed verification (run ad hoc) ---
-- Role/permission matrix:
--   SELECT m.member_id, m.preferred_name_first, b.name AS body, mt.code AS role,
--          pg.name AS permission_group, pg.scope
--   FROM members m
--            JOIN body_memberships bm ON bm.member_id = m.id
--            JOIN bodies b ON b.id = bm.body_id
--            JOIN body_template_member_types mt ON mt.id = bm.member_type_id
--            JOIN permission_group pg ON pg.id = mt.permission_group_id
--   WHERE m.member_id LIKE '95%'
--   ORDER BY m.member_id;
--   -> 6 rows: 950001/950002 District board (LOCAL), 950003 District board (LOCAL,
--      Dolnośląski — isolation fixture), 950004 National board (GLOBAL),
--      950005 National board treasurer (GLOBAL), 950006 District treasurer (LOCAL).
--
-- District affiliations:
--   SELECT m.member_id, b.name AS district, a.kind, a.votes_here
--   FROM members m
--            JOIN member_district_affiliations a ON a.member_id = m.id
--            JOIN bodies b ON b.id = a.district_body_id
--   ORDER BY m.member_id;
--   -> 950001/950002/950006/950009 Okręg Mazowiecki, 950003 Okręg Dolnośląski.
--
-- Initial-period clock (950007): declaration 65 days ago, window 62–76 open:
--   SELECT member_id, status, request_date, probation_period_start_date,
--          probation_period_end_date,
--          CURRENT_DATE - request_date AS day_of_initial_period
--   FROM members WHERE member_id = '950007';
--
-- Suspension:
--   SELECT m.member_id, s.suspension_start, s.decision_signature, zb.name AS suspending_body
--   FROM suspensions s
--            JOIN members m ON m.id = s.suspended_user_id
--            JOIN bodies zb ON zb.id = s.suspending_body_id;
--   -> one row: 950009, 'ZO/MZ/01/2026', Zarząd Okręgu Mazowieckiego.
