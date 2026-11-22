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

-- Recruitment permission types (PO-9): deliberately separate from FORMAL_PERSONAL_DATA_* so that
-- access to candidate data is audited and granted independently of the member registry.
-- Kept in its own migration because PostgreSQL refuses to use a newly added enum value
-- within the same transaction that added it; the seed lives in V0.0.6.02.

ALTER TYPE permission_type ADD VALUE 'RECRUITMENT_READ';
ALTER TYPE permission_type ADD VALUE 'RECRUITMENT_MANAGE';
ALTER TYPE permission_type ADD VALUE 'RECRUITMENT_APPROVE';
