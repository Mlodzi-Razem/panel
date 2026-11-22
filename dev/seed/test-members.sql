-- dev/seed/test-members.sql — local development seed for panel-dev (the 5433 stack).
--
-- Adds two FABRICATED test members (000205, 000368) with a PRIMARY district
-- affiliation to "Okręg Mazowiecki". All personal data below is invented for
-- local development; the member numbers are the organisation's hand-made test
-- accounts. This file must never contain real member data or real secrets.
--
-- Prerequisite: panel-api must have booted against this database at least
-- once — Flyway migrations 0.0.1..0.0.5 create the members, bodies and
-- member_district_affiliations tables (and seed the bodies). Applying this
-- seed to an empty database fails on "relation ... does not exist".
--
-- Idempotent: safe to re-run (ON CONFLICT DO NOTHING inside one transaction).
-- Re-running leaves row counts and already-assigned ids unchanged.

BEGIN;

-- ---------------------------------------------------------------- members ---
-- Columns worth noting:
--   * member_id is the six-digit membership number stored as TEXT ('000205');
--   * preferred_name is a GENERATED VIRTUAL column — never insert it;
--   * membership_type defaults to 'ORDINARY' (enum: ORDINARY|SUPPORTING|HONORARY);
--   * status enum values: ACTIVE, REMOVED, SUSPENDED, FORM_REQUEST, PROBATION;
--   * domain-email uniqueness is enforced across members by the
--     member_emails sync trigger (migration 0.0.2.00).
INSERT INTO members (status, member_id,
                     preferred_name_first, preferred_name_last,
                     formal_name_first, formal_name_last,
                     domain_email, domain_email_aliases, private_emails,
                     birth_date, membership_type)
VALUES ('ACTIVE', '000205',
        'Anna', 'Testowa',
        'Anna', 'Testowa',
        '000205@mlodzirazem.org', '{}', '{}',
        DATE '1990-05-01', 'ORDINARY'),
       ('ACTIVE', '000368',
        'Barbara', 'Przykładowa',
        'Barbara', 'Przykładowa',
        '000368@mlodzirazem.org', '{}', '{}',
        DATE '1992-11-30', 'ORDINARY')
ON CONFLICT (member_id) DO NOTHING;

-- ---------------------------------------------- district affiliations (PRIMARY) ---
-- "Okręg Mazowiecki" is seeded by Flyway migration V0.0.3.01__seed-organisation.sql
-- (bodies named 'Okręg ' || nominative, code 'district-mazowieckie'). In a freshly
-- migrated database it lands on id 13 (bodies 1–6 are the national ones; the 17
-- districts take 7–23 and Mazowiecki is the 7th). Do NOT rely on that number:
-- resolve the id by name, so the seed keeps working if ids shift.
--
-- The arbiter matches the partial unique index member_district_affiliations_open_district_idx
-- (member_id, district_body_id) WHERE valid_to IS NULL, so a re-run does nothing
-- when the open affiliation already exists.
INSERT INTO member_district_affiliations (member_id, district_body_id, kind, votes_here, valid_from)
SELECT m.id, ob.id, 'PRIMARY', TRUE, DATE '2015-05-01'
FROM (VALUES ('000205'), ('000368')) AS seeded (member_id)
         JOIN members m ON m.member_id = seeded.member_id
         CROSS JOIN (SELECT id FROM bodies WHERE name = 'Okręg Mazowiecki') AS ob
ON CONFLICT (member_id, district_body_id) WHERE valid_to IS NULL DO NOTHING;

COMMIT;
