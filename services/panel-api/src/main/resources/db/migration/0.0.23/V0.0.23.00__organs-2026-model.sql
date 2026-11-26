/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

-- Organs and functions exactly as the statute of 3 October 2026 knows them (A4a, decision D12).
--
-- D12 (owner, 2026-11-23): mandates and organs follow the 2026 model only. The panel has no history from before
-- 2026, so the 2024 elements are REMOVED rather than flagged historical:
--   * the 17 separate organs "Osoba Skarbnicza Okręgu" and their template/type (art. 43 ust. 3 of the 2026 text:
--     the district treasurer is chosen by the ZO from among its members, so the function lives in the ZO);
--   * the ZK function VICE_CHAIR (art. 22 ust. 1: two co-chairs, a secretary, a treasurer, three members);
--   * the ZO function VICE_COORDINATOR (art. 43 ust. 2: coordinator and treasurer, the rest are plain members).
-- The migration aborts with a readable message when anything still references an element it removes.
--
-- Added: Rada Krajowa (art. 14 ust. 2 pkt 3, art. 23-24), the plain MEMBER function of ZK and ZO and the TREASURER
-- function of ZO (it takes over the permission group "District treasurer" of the removed organs). In the panel the
-- RK is informational only (owner decision, 2026-11-25): no permissions, approvals, limits or rules.
--
-- Member limits (ZK 7, KSK 9, KKR 5, KKW 5, ZO art. 43, komisaryczny 2 and 186 days, koło >= 6) are NOT stored:
-- they are statute constants enforced in DefaultBodyMembershipService by (body kind, function code), see A4b.
--
-- Not changed on purpose (policy, owner's decision G-2): the single-member exemption of the pairs
-- KSK/KKR/KKW x ZO in body_template_incompatibilities. The statute (art. 18 ust. 1 pkt 6) has no such exception,
-- but it allows a one-person ZO (art. 43 ust. 1 pkt 1, ust. 4), and without the exception nobody in the smallest
-- districts could ever sit in KKR, KSK or KKW. The ZK x ZO pair has no exemption. RK is outside the group of
-- art. 18 ust. 1 pkt 6, so no incompatibility pair is added for it.

-- 1. Guard: nothing that is going away may be referenced.
DO
$$
    DECLARE
        fk            RECORD;
        sub           TEXT;
        n             BIGINT;
        problems      TEXT := '';
        target_bodies CONSTANT TEXT := $q$SELECT b.id
                                          FROM bodies b
                                                   JOIN body_templates t ON t.id = b.body_template_id
                                          WHERE t.kind = 'DISTRICT_TREASURER'$q$;
        target_types  CONSTANT TEXT := $q$SELECT mt.id
                                          FROM body_template_member_types mt
                                                   JOIN body_templates t ON t.id = mt.body_template_id
                                          WHERE t.kind = 'DISTRICT_TREASURER'
                                             OR (t.kind = 'NATIONAL_BOARD' AND mt.code = 'VICE_CHAIR')
                                             OR (t.kind = 'DISTRICT_BOARD' AND mt.code = 'VICE_COORDINATOR')$q$;
        target_tmpls  CONSTANT TEXT := $q$SELECT t.id FROM body_templates t WHERE t.kind = 'DISTRICT_TREASURER'$q$;
    BEGIN
        FOR fk IN
            SELECT c.conrelid::regclass::TEXT AS tbl, a.attname::TEXT AS col, c.confrelid::regclass::TEXT AS ref
            FROM pg_constraint c
                     JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
            WHERE c.contype = 'f'
              AND array_length(c.conkey, 1) = 1
              AND c.confrelid IN ('bodies'::regclass, 'body_template_member_types'::regclass,
                                  'body_templates'::regclass)
            LOOP
                -- rows of the elements that are removed together are not references to them
                CONTINUE WHEN fk.ref = 'body_templates'
                    AND (fk.tbl IN ('bodies', 'body_template_member_types')
                        OR (fk.tbl = 'body_template_incompatibilities'
                            AND fk.col <> 'single_member_exempt_template_id'));

                sub := CASE fk.ref
                           WHEN 'bodies' THEN target_bodies
                           WHEN 'body_template_member_types' THEN target_types
                           ELSE target_tmpls END;

                EXECUTE format('SELECT count(*) FROM %s WHERE %I IN (%s)', fk.tbl, fk.col, sub) INTO n;

                IF n > 0 THEN
                    problems := problems || format(' %s.%s: %s row(s);', fk.tbl, fk.col, n);
                END IF;
            END LOOP;

        IF problems <> '' THEN
            RAISE EXCEPTION 'The statute 2026 model migration (D12) cannot remove the 2024 organs and functions'
                ' (district treasurer organs, ZK VICE_CHAIR, ZO VICE_COORDINATOR): they are still referenced by%'
                ' Close or move those mandates deliberately first - the history is never overwritten.', problems;
        END IF;
    END
$$;

-- 2. Remove the 2024 elements (D12).
DELETE
FROM bodies
WHERE body_template_id IN (SELECT id FROM body_templates WHERE kind = 'DISTRICT_TREASURER');

DELETE
FROM body_template_member_types
WHERE body_template_id IN (SELECT id FROM body_templates WHERE kind = 'DISTRICT_TREASURER');

DELETE
FROM body_templates
WHERE kind = 'DISTRICT_TREASURER';

DELETE
FROM body_template_member_types mt USING body_templates t
WHERE t.id = mt.body_template_id
  AND ((t.kind = 'NATIONAL_BOARD' AND mt.code = 'VICE_CHAIR')
    OR (t.kind = 'DISTRICT_BOARD' AND mt.code = 'VICE_COORDINATOR'));

-- 3. body_kind without DISTRICT_TREASURER and with NATIONAL_COUNCIL. PostgreSQL cannot drop an enum label, so the
--    type is rebuilt (only body_templates.kind uses it); a freshly created type may be used in this transaction.
ALTER TYPE body_kind RENAME TO body_kind_2024;

CREATE TYPE body_kind AS ENUM ('GENERAL_ASSEMBLY', 'NATIONAL_BOARD', 'NATIONAL_COUNCIL', 'PEER_COURT',
    'AUDIT_COMMISSION', 'ELECTORAL_COMMISSION', 'PRESS_SPOKESPERSON', 'NATIONAL_IT_PROXY', 'TEAM', 'DISTRICT',
    'DISTRICT_ASSEMBLY', 'DISTRICT_BOARD', 'DISTRICT_COMMISSIONER', 'LOCAL_CIRCLE');

ALTER TABLE body_templates
    ALTER COLUMN kind TYPE body_kind USING kind::TEXT::body_kind;

DROP TYPE body_kind_2024;

-- 4. Commissioner board (art. 44 ust. 3: at most 186 days, once) replaces the 2024 "3 months + 2 extensions"
--    (about 275 days). The columns are NOT cleared: the code still reads them, and NULL would switch the limit off.
--    Six calendar months are 181-184 days, so "6 months, no extension" stays inside the statute. The exact 186-day
--    rule belongs to the code (A4b), which no longer reads these columns.
UPDATE body_templates
SET max_term_months = 6,
    max_extensions  = 0
WHERE kind = 'DISTRICT_COMMISSIONER';

-- 5. Rada Krajowa.
INSERT INTO body_templates (scope, name, kind, body_parent_template_id, max_consecutive_terms, term_length_months,
                            max_term_months, max_extensions)
VALUES ('GLOBAL', 'Rada Krajowa', 'NATIONAL_COUNCIL', NULL, NULL, NULL, NULL, NULL)
ON CONFLICT (name) DO NOTHING;

-- ZK: plain member, three of seven (art. 22 ust. 1). ZO: plain member and treasurer (art. 43 ust. 2-3).
-- RK: seats that follow from another organ (art. 24 ust. 1) and the five-person Presidium (art. 24 ust. 2); the RK
-- itself is not elected (art. 16 ust. 1), so it has no term. Its functions are labels and hold no permissions.
INSERT INTO body_template_member_types (name, code, is_default, body_template_id, permission_group_id)
SELECT r.name, r.code, r.is_default, t.id, g.id
FROM (VALUES ('NATIONAL_BOARD', 'Member', 'MEMBER', TRUE, 'GLOBAL', 'National board'),
             ('DISTRICT_BOARD', 'Treasurer', 'TREASURER', FALSE, 'LOCAL', 'District treasurer'),
             ('DISTRICT_BOARD', 'Member', 'MEMBER', TRUE, 'LOCAL', 'District board'),
             ('NATIONAL_COUNCIL', 'ZO representative', 'ZO_REPRESENTATIVE', TRUE, 'BODY', 'No permissions'),
             ('NATIONAL_COUNCIL', 'ZK member', 'ZK_MEMBER', FALSE, 'BODY', 'No permissions'),
             ('NATIONAL_COUNCIL', 'Affiliated-party representative', 'AFFILIATE_REPRESENTATIVE', FALSE, 'BODY',
              'No permissions'),
             ('NATIONAL_COUNCIL', 'Presidium co-chair (ZK)', 'PRESIDIUM_CO_CHAIR', FALSE, 'BODY', 'No permissions'),
             ('NATIONAL_COUNCIL', 'Presidium member', 'PRESIDIUM_MEMBER', FALSE, 'BODY', 'No permissions'))
         AS r (kind, name, code, is_default, group_scope, group_name)
         JOIN body_templates t ON t.kind = r.kind::body_kind
         JOIN permission_group g ON g.scope = r.group_scope::permission_scope AND g.name = r.group_name
ON CONFLICT (body_template_id, code) DO NOTHING;

-- seeded (0.0.13): part of the statutory structure, the panel never deletes it.
INSERT INTO bodies (body_template_id, parent_body_id, name, code, seeded)
SELECT t.id, NULL, 'Rada Krajowa', 'national-council', TRUE
FROM body_templates t
WHERE t.kind = 'NATIONAL_COUNCIL'
ON CONFLICT (code) DO NOTHING;

-- 6. The district treasurer is a member of the ZO (art. 43 ust. 3), so the function may never give less than a plain
--    ZO membership (owner decision, 2026-11-25): "District treasurer" gets everything "District board" holds, on top
--    of its own. Both groups stay LOCAL; only the ZO TREASURER function uses this group after step 2.
INSERT INTO permission_group_permissions (permission_group_id, permission_type)
SELECT treasurer.id, p.permission_type
FROM permission_group_permissions p
         JOIN permission_group board ON board.id = p.permission_group_id
         JOIN permission_group treasurer ON treasurer.scope = 'LOCAL' AND treasurer.name = 'District treasurer'
WHERE board.scope = 'LOCAL'
  AND board.name = 'District board'
ON CONFLICT DO NOTHING;

COMMENT ON COLUMN body_template_incompatibilities.single_member_exempt_template_id IS
    'Policy of the association (owner decision G-2), not a statute rule: while this organ has only one member, the pair is not enforced (KSK/KKR/KKW x one-person ZO, art. 43 ust. 1 pkt 1). ZK x ZO has no exemption.';
