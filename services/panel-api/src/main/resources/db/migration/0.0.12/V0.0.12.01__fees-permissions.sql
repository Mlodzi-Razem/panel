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

INSERT INTO permission_group (name, scope, is_admin)
VALUES ('Audit commission', 'GLOBAL', FALSE)
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO permission_group_permissions (permission_group_id, permission_type)
SELECT g.id, 'FEES_READ'::permission_type
FROM permission_group g
WHERE (g.scope = 'GLOBAL' AND g.name IN ('National board', 'Audit commission'))
   OR (g.scope = 'LOCAL' AND g.name = 'District board')
ON CONFLICT DO NOTHING;

UPDATE body_template_member_types mt
SET permission_group_id = audit.id
FROM body_templates t,
     permission_group audit,
     permission_group current_group
WHERE t.id = mt.body_template_id
  AND t.kind = 'AUDIT_COMMISSION'
  AND audit.scope = 'GLOBAL'
  AND audit.name = 'Audit commission'
  AND current_group.id = mt.permission_group_id
  AND current_group.scope = 'BODY'
  AND current_group.name = 'No permissions';
