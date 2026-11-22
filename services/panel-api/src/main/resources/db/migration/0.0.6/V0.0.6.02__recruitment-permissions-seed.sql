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

-- Roles in a district board (Coordinator, Vice-coordinator, Commissioner) manage and read their okręg's
-- recruitment cases; the district treasurer may read them (FEES_READ parity); the national board reads
-- everything and records the ZK approval (RECRUITMENT_APPROVE).

INSERT INTO permission_group_permissions (permission_group_id, permission_type)
SELECT g.id, p.permission_type::permission_type
FROM (VALUES ('LOCAL', 'District board', 'RECRUITMENT_READ'),
             ('LOCAL', 'District board', 'RECRUITMENT_MANAGE'),
             ('LOCAL', 'District treasurer', 'RECRUITMENT_READ'),
             ('GLOBAL', 'National board', 'RECRUITMENT_READ'),
             ('GLOBAL', 'National board', 'RECRUITMENT_APPROVE'))
         AS p (scope, group_name, permission_type)
         JOIN permission_group g ON g.scope = p.scope::permission_scope AND g.name = p.group_name
ON CONFLICT DO NOTHING;
