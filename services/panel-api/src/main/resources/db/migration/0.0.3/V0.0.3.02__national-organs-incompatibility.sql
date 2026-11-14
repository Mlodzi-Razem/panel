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

INSERT INTO body_template_incompatibilities (body_template_id, other_body_template_id, single_member_exempt_template_id)
SELECT LEAST(a.id, b.id), GREATEST(a.id, b.id), NULL
FROM (VALUES ('PEER_COURT', 'AUDIT_COMMISSION'),
             ('PEER_COURT', 'ELECTORAL_COMMISSION'),
             ('AUDIT_COMMISSION', 'ELECTORAL_COMMISSION')) AS v (kind_a, kind_b)
         JOIN body_templates a ON a.kind = v.kind_a::body_kind
         JOIN body_templates b ON b.kind = v.kind_b::body_kind
ON CONFLICT DO NOTHING;
