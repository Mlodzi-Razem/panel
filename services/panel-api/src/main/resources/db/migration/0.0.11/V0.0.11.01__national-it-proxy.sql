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

-- Pełnomocnik ZK ds. IT: the person who administers Panel and the association's Microsoft 365 tenant.
--
-- Statute (consolidated text of 3 October 2026): ZK grants a member a power of attorney for organisational
-- tasks by resolution (art. 21 ust. 2 pkt 7) and the RK approves it (art. 23 ust. 2 pkt 3). The proxy is not
-- an organ, so no incompatibility pair applies; the mandate is a body membership whose decision_reference
-- names both resolutions, and it ends like any other mandate (history is never overwritten).
--
-- Scope (owner's decision, 2026-11-23: full): everything the ZK treasurer holds, personal data of art. 9 RODO
-- and fees included, plus the recruitment read access. RECRUITMENT_APPROVE is left out on purpose: admissions
-- are a collegial decision of the ZK. is_admin stays FALSE: no code gives that flag a meaning.

INSERT INTO body_templates (scope, name, kind, body_parent_template_id, max_consecutive_terms, term_length_months,
                            max_term_months, max_extensions)
VALUES ('GLOBAL', 'Pełnomocnik ZK ds. IT', 'NATIONAL_IT_PROXY', NULL, NULL, NULL, NULL, NULL)
ON CONFLICT (name) DO NOTHING;

INSERT INTO permission_group (name, scope, is_admin)
VALUES ('National IT proxy', 'GLOBAL', FALSE)
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO permission_group_permissions (permission_group_id, permission_type)
SELECT g.id, p.permission_type::permission_type
FROM (VALUES ('CONTACT_PERSONAL_DATA_READ'),
             ('CONTACT_PERSONAL_DATA_MANAGE'),
             ('FORMAL_PERSONAL_DATA_READ'),
             ('FORMAL_PERSONAL_DATA_MANAGE'),
             ('BODIES_MANAGE'),
             ('DIRECTORY_GROUPS_MANAGE'),
             ('DIRECTORY_USERS_MANAGE'),
             ('FEES_READ'),
             ('FEES_MANAGE'),
             ('RECRUITMENT_READ'))
         AS p (permission_type)
         JOIN permission_group g ON g.scope = 'GLOBAL' AND g.name = 'National IT proxy'
ON CONFLICT DO NOTHING;

INSERT INTO body_template_member_types (name, code, is_default, body_template_id, permission_group_id)
SELECT 'IT proxy', 'PROXY', TRUE, t.id, g.id
FROM body_templates t
         JOIN permission_group g ON g.scope = 'GLOBAL' AND g.name = 'National IT proxy'
WHERE t.kind = 'NATIONAL_IT_PROXY'
ON CONFLICT (body_template_id, code) DO NOTHING;

INSERT INTO bodies (body_template_id, parent_body_id, name, code)
SELECT t.id, NULL, 'Pełnomocnik ZK ds. IT', 'national-it-proxy'
FROM body_templates t
WHERE t.kind = 'NATIONAL_IT_PROXY'
ON CONFLICT (code) DO NOTHING;
