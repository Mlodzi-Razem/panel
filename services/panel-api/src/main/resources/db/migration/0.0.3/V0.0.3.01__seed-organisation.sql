/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
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

INSERT INTO body_templates (scope, name, kind, body_parent_template_id, max_consecutive_terms, term_length_months,
                            max_term_months, max_extensions)
VALUES ('GLOBAL', 'Walne Zebranie Osób Członkowskich / Zgromadzenie Delegatów', 'GENERAL_ASSEMBLY', NULL, NULL, NULL,
        NULL, NULL),
       ('GLOBAL', 'Zarząd Krajowy', 'NATIONAL_BOARD', NULL, 2, 12, NULL, NULL),
       ('GLOBAL', 'Krajowy Sąd Koleżeński', 'PEER_COURT', NULL, 2, 12, NULL, NULL),
       ('GLOBAL', 'Krajowa Komisja Rewizyjna', 'AUDIT_COMMISSION', NULL, 2, 12, NULL, NULL),
       ('GLOBAL', 'Krajowa Komisja Wyborcza', 'ELECTORAL_COMMISSION', NULL, 2, 12, NULL, NULL),
       ('GLOBAL', 'Osoba Rzecznicza ds. Prasowych', 'PRESS_SPOKESPERSON', NULL, NULL, NULL, NULL, NULL),
       ('GLOBAL', 'Komisja / Zespół / Koło edukacyjne / Grupa', 'TEAM', NULL, NULL, NULL, NULL, NULL),
       ('LOCAL', 'Okręg', 'DISTRICT', NULL, NULL, NULL, NULL, NULL)
ON CONFLICT (name) DO NOTHING;

INSERT INTO body_templates (scope, name, kind, body_parent_template_id, max_consecutive_terms, term_length_months,
                            max_term_months, max_extensions)
SELECT 'LOCAL', v.name, v.kind::body_kind, d.id, v.max_consecutive_terms, v.term_length_months, v.max_term_months,
       v.max_extensions
FROM (VALUES ('Walne Zebranie Okręgu', 'DISTRICT_ASSEMBLY', NULL::INT, NULL::INT, NULL::INT, NULL::INT),
             ('Zarząd Okręgu', 'DISTRICT_BOARD', 2, 12, NULL, NULL),
             ('Osoba Skarbnicza Okręgu', 'DISTRICT_TREASURER', NULL, NULL, NULL, NULL),
             ('Osoba Komisarska Okręgu', 'DISTRICT_COMMISSIONER', NULL, NULL, 3, 2),
             ('Koło terenowe', 'LOCAL_CIRCLE', NULL, NULL, NULL, NULL))
         AS v (name, kind, max_consecutive_terms, term_length_months, max_term_months, max_extensions)
         JOIN body_templates d ON d.name = 'Okręg'
ON CONFLICT (name) DO NOTHING;

INSERT INTO permission_group (name, scope, is_admin)
VALUES ('District board', 'LOCAL', FALSE),
       ('District treasurer', 'LOCAL', FALSE),
       ('National board', 'GLOBAL', FALSE),
       ('National board treasurer', 'GLOBAL', FALSE),
       ('No permissions', 'BODY', FALSE)
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO permission_group_permissions (permission_group_id, permission_type)
SELECT g.id, p.permission_type::permission_type
FROM (VALUES ('LOCAL', 'District board', 'CONTACT_PERSONAL_DATA_READ'),
             ('LOCAL', 'District board', 'CONTACT_PERSONAL_DATA_MANAGE'),
             ('LOCAL', 'District board', 'FORMAL_PERSONAL_DATA_READ'),
             ('LOCAL', 'District board', 'FORMAL_PERSONAL_DATA_MANAGE'),
             ('LOCAL', 'District treasurer', 'CONTACT_PERSONAL_DATA_READ'),
             ('LOCAL', 'District treasurer', 'FEES_READ'),
             ('GLOBAL', 'National board', 'CONTACT_PERSONAL_DATA_READ'),
             ('GLOBAL', 'National board', 'CONTACT_PERSONAL_DATA_MANAGE'),
             ('GLOBAL', 'National board', 'FORMAL_PERSONAL_DATA_READ'),
             ('GLOBAL', 'National board', 'FORMAL_PERSONAL_DATA_MANAGE'),
             ('GLOBAL', 'National board', 'BODIES_MANAGE'),
             ('GLOBAL', 'National board', 'DIRECTORY_GROUPS_MANAGE'),
             ('GLOBAL', 'National board', 'DIRECTORY_USERS_MANAGE'),
             ('GLOBAL', 'National board treasurer', 'CONTACT_PERSONAL_DATA_READ'),
             ('GLOBAL', 'National board treasurer', 'CONTACT_PERSONAL_DATA_MANAGE'),
             ('GLOBAL', 'National board treasurer', 'FORMAL_PERSONAL_DATA_READ'),
             ('GLOBAL', 'National board treasurer', 'FORMAL_PERSONAL_DATA_MANAGE'),
             ('GLOBAL', 'National board treasurer', 'BODIES_MANAGE'),
             ('GLOBAL', 'National board treasurer', 'DIRECTORY_GROUPS_MANAGE'),
             ('GLOBAL', 'National board treasurer', 'DIRECTORY_USERS_MANAGE'),
             ('GLOBAL', 'National board treasurer', 'FEES_READ'),
             ('GLOBAL', 'National board treasurer', 'FEES_MANAGE'))
         AS p (scope, group_name, permission_type)
         JOIN permission_group g ON g.scope = p.scope::permission_scope AND g.name = p.group_name
ON CONFLICT DO NOTHING;

INSERT INTO body_template_member_types (name, code, is_default, body_template_id, permission_group_id)
SELECT r.name, r.code, r.is_default, t.id, g.id
FROM (VALUES ('Walne Zebranie Osób Członkowskich / Zgromadzenie Delegatów', 'Delegate', 'DELEGATE', TRUE,
              'BODY', 'No permissions'),
             ('Zarząd Krajowy', 'Co-chair', 'CO_CHAIR', FALSE, 'GLOBAL', 'National board'),
             ('Zarząd Krajowy', 'Vice-chair', 'VICE_CHAIR', FALSE, 'GLOBAL', 'National board'),
             ('Zarząd Krajowy', 'Secretary', 'SECRETARY', FALSE, 'GLOBAL', 'National board'),
             ('Zarząd Krajowy', 'Treasurer', 'TREASURER', FALSE, 'GLOBAL', 'National board treasurer'),
             ('Krajowy Sąd Koleżeński', 'Chair', 'CHAIR', FALSE, 'BODY', 'No permissions'),
             ('Krajowy Sąd Koleżeński', 'Vice-chair', 'VICE_CHAIR', FALSE, 'BODY', 'No permissions'),
             ('Krajowy Sąd Koleżeński', 'Secretary', 'SECRETARY', FALSE, 'BODY', 'No permissions'),
             ('Krajowy Sąd Koleżeński', 'Member', 'MEMBER', TRUE, 'BODY', 'No permissions'),
             ('Krajowa Komisja Rewizyjna', 'Chair', 'CHAIR', FALSE, 'BODY', 'No permissions'),
             ('Krajowa Komisja Rewizyjna', 'Vice-chair', 'VICE_CHAIR', FALSE, 'BODY', 'No permissions'),
             ('Krajowa Komisja Rewizyjna', 'Secretary', 'SECRETARY', FALSE, 'BODY', 'No permissions'),
             ('Krajowa Komisja Rewizyjna', 'Member', 'MEMBER', TRUE, 'BODY', 'No permissions'),
             ('Krajowa Komisja Wyborcza', 'Chair', 'CHAIR', FALSE, 'BODY', 'No permissions'),
             ('Krajowa Komisja Wyborcza', 'Vice-chair', 'VICE_CHAIR', FALSE, 'BODY', 'No permissions'),
             ('Krajowa Komisja Wyborcza', 'Secretary', 'SECRETARY', FALSE, 'BODY', 'No permissions'),
             ('Krajowa Komisja Wyborcza', 'Member', 'MEMBER', TRUE, 'BODY', 'No permissions'),
             ('Osoba Rzecznicza ds. Prasowych', 'Spokesperson', 'SPOKESPERSON', TRUE, 'BODY', 'No permissions'),
             ('Komisja / Zespół / Koło edukacyjne / Grupa', 'Leader', 'LEADER', FALSE, 'BODY', 'No permissions'),
             ('Komisja / Zespół / Koło edukacyjne / Grupa', 'Member', 'MEMBER', TRUE, 'BODY', 'No permissions'),
             ('Zarząd Okręgu', 'Coordinator', 'COORDINATOR', FALSE, 'LOCAL', 'District board'),
             ('Zarząd Okręgu', 'Vice-coordinator', 'VICE_COORDINATOR', TRUE, 'LOCAL', 'District board'),
             ('Osoba Skarbnicza Okręgu', 'Treasurer', 'TREASURER', TRUE, 'LOCAL', 'District treasurer'),
             ('Osoba Komisarska Okręgu', 'Commissioner', 'COMMISSIONER', TRUE, 'LOCAL', 'District board'),
             ('Koło terenowe', 'Coordinator', 'COORDINATOR', FALSE, 'BODY', 'No permissions'),
             ('Koło terenowe', 'Member', 'MEMBER', TRUE, 'BODY', 'No permissions'))
         AS r (template_name, name, code, is_default, group_scope, group_name)
         JOIN body_templates t ON t.name = r.template_name
         JOIN permission_group g ON g.scope = r.group_scope::permission_scope AND g.name = r.group_name
ON CONFLICT (body_template_id, code) DO NOTHING;

INSERT INTO body_template_incompatibilities (body_template_id, other_body_template_id, single_member_exempt_template_id)
SELECT LEAST(a.id, b.id), GREATEST(a.id, b.id), CASE WHEN v.exempt THEN zo.id END
FROM (VALUES ('NATIONAL_BOARD', 'PEER_COURT', FALSE),
             ('NATIONAL_BOARD', 'AUDIT_COMMISSION', FALSE),
             ('NATIONAL_BOARD', 'ELECTORAL_COMMISSION', FALSE),
             ('NATIONAL_BOARD', 'DISTRICT_BOARD', FALSE),
             ('PEER_COURT', 'DISTRICT_BOARD', TRUE),
             ('AUDIT_COMMISSION', 'DISTRICT_BOARD', TRUE),
             ('ELECTORAL_COMMISSION', 'DISTRICT_BOARD', TRUE)) AS v (kind_a, kind_b, exempt)
         JOIN body_templates a ON a.kind = v.kind_a::body_kind
         JOIN body_templates b ON b.kind = v.kind_b::body_kind
         JOIN body_templates zo ON zo.kind = 'DISTRICT_BOARD'
ON CONFLICT DO NOTHING;

INSERT INTO bodies (body_template_id, parent_body_id, name, code)
SELECT t.id, NULL, v.name, v.code
FROM (VALUES ('GENERAL_ASSEMBLY', 'Walne Zebranie Osób Członkowskich', 'general-assembly'),
             ('NATIONAL_BOARD', 'Zarząd Krajowy', 'national-board'),
             ('PEER_COURT', 'Krajowy Sąd Koleżeński', 'peer-court'),
             ('AUDIT_COMMISSION', 'Krajowa Komisja Rewizyjna', 'audit-commission'),
             ('ELECTORAL_COMMISSION', 'Krajowa Komisja Wyborcza', 'electoral-commission'),
             ('PRESS_SPOKESPERSON', 'Osoba Rzecznicza ds. Prasowych', 'press-spokesperson')) AS v (kind, name, code)
         JOIN body_templates t ON t.kind = v.kind::body_kind
ON CONFLICT (code) DO NOTHING;

INSERT INTO bodies (body_template_id, parent_body_id, name, code)
SELECT t.id, NULL, 'Okręg ' || d.nominative, 'district-' || d.slug
FROM (VALUES ('dolnoslaskie', 'Dolnośląski', 'Dolnośląskiego'),
             ('kujawsko-pomorskie', 'Kujawsko-Pomorski', 'Kujawsko-Pomorskiego'),
             ('lubelskie', 'Lubelski', 'Lubelskiego'),
             ('lubuskie', 'Lubuski', 'Lubuskiego'),
             ('lodzkie', 'Łódzki', 'Łódzkiego'),
             ('malopolskie', 'Małopolski', 'Małopolskiego'),
             ('mazowieckie', 'Mazowiecki', 'Mazowieckiego'),
             ('opolskie', 'Opolski', 'Opolskiego'),
             ('podkarpackie', 'Podkarpacki', 'Podkarpackiego'),
             ('podlaskie', 'Podlaski', 'Podlaskiego'),
             ('pomorskie', 'Pomorski', 'Pomorskiego'),
             ('slaskie', 'Śląski', 'Śląskiego'),
             ('swietokrzyskie', 'Świętokrzyski', 'Świętokrzyskiego'),
             ('warminsko-mazurskie', 'Warmińsko-Mazurski', 'Warmińsko-Mazurskiego'),
             ('wielkopolskie', 'Wielkopolski', 'Wielkopolskiego'),
             ('zachodniopomorskie', 'Zachodniopomorski', 'Zachodniopomorskiego'),
             ('zagranica', 'Zagranica', 'Zagranica')) AS d (slug, nominative, genitive)
         JOIN body_templates t ON t.kind = 'DISTRICT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO bodies (body_template_id, parent_body_id, name, code)
SELECT t.id,
       district.id,
       v.name_prefix || ' ' || d.genitive,
       'district-' || d.slug || '-' || v.code_suffix
FROM (VALUES ('dolnoslaskie', 'Dolnośląskiego'),
             ('kujawsko-pomorskie', 'Kujawsko-Pomorskiego'),
             ('lubelskie', 'Lubelskiego'),
             ('lubuskie', 'Lubuskiego'),
             ('lodzkie', 'Łódzkiego'),
             ('malopolskie', 'Małopolskiego'),
             ('mazowieckie', 'Mazowieckiego'),
             ('opolskie', 'Opolskiego'),
             ('podkarpackie', 'Podkarpackiego'),
             ('podlaskie', 'Podlaskiego'),
             ('pomorskie', 'Pomorskiego'),
             ('slaskie', 'Śląskiego'),
             ('swietokrzyskie', 'Świętokrzyskiego'),
             ('warminsko-mazurskie', 'Warmińsko-Mazurskiego'),
             ('wielkopolskie', 'Wielkopolskiego'),
             ('zachodniopomorskie', 'Zachodniopomorskiego'),
             ('zagranica', 'Zagranica')) AS d (slug, genitive)
         CROSS JOIN (VALUES ('DISTRICT_ASSEMBLY', 'Walne Zebranie Okręgu', 'assembly'),
                            ('DISTRICT_BOARD', 'Zarząd Okręgu', 'board'),
                            ('DISTRICT_TREASURER', 'Osoba Skarbnicza Okręgu', 'treasurer'))
    AS v (kind, name_prefix, code_suffix)
         JOIN body_templates t ON t.kind = v.kind::body_kind
         JOIN bodies district ON district.code = 'district-' || d.slug
ON CONFLICT (code) DO NOTHING;
