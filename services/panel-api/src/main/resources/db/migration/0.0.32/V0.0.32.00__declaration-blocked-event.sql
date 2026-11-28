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

-- Refused declarations (Q-25): a block rule that refuses a declaration leaves a timeline event for the district
-- board. The event is written after the refusing transaction rolled back, so it cannot be undone with it.
-- Own transaction: PG cannot use a new enum value in the transaction that adds it.
-- Payload: reason and channel codes only, never values.

ALTER TYPE recruitment_case_event_type ADD VALUE IF NOT EXISTS 'DECLARATION_BLOCKED';
