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

-- Retention of rejected recruitment cases (M8, owner decision G-5): the job appends these timeline
-- events. Own transaction: PG cannot use a new enum value in the transaction that adds it.
-- Payloads stay names-only, as everywhere else in the module.

ALTER TYPE recruitment_case_event_type ADD VALUE IF NOT EXISTS 'ARTIFACTS_PURGED';
ALTER TYPE recruitment_case_event_type ADD VALUE IF NOT EXISTS 'CASE_ANONYMIZED';
