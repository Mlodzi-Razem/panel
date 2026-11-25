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

-- Mandate status (A3: hygiene of bodies and mandates, statute 2026, PLAN-WORKFLOW.md).
--
-- VOIDED marks a mandate that was recorded by mistake ("wpis omyłkowy"): it never happened, so it
-- must stop counting as a function (composition, permissions, statutory checks) — but the history
-- is never overwritten, let alone deleted. The row stays in body_memberships with status VOIDED,
-- the void reason and the audit entry ("bodies :: membership-void") live in the audit log.
-- Ending a mandate (valid_to) stays a separate concept: a ended mandate still happened.
--
-- The partial unique index on open mandates has to re-open the voided slot, otherwise a member
-- whose mistaken mandate was voided could never be assigned the function again (23505 on insert).

CREATE TYPE body_membership_status AS ENUM ('ACTIVE', 'VOIDED');

ALTER TABLE body_memberships
    ADD COLUMN status     body_membership_status NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN void_reason TEXT,
    -- Reference of the resolution that voids the mandate. Separate from decision_reference (the
    -- appointing resolution), which a void never overwrites (A3-2).
    ADD COLUMN void_decision_reference TEXT;

ALTER TABLE body_memberships
    ADD CONSTRAINT body_memberships_void_reason_required_chk CHECK (status <> 'VOIDED' OR void_reason IS NOT NULL);

ALTER TABLE body_memberships
    ADD CONSTRAINT body_memberships_void_reason_only_after_void_chk
        CHECK (status = 'VOIDED' OR void_reason IS NULL);

ALTER TABLE body_memberships
    ADD CONSTRAINT body_memberships_void_decision_reference_only_after_void_chk
        CHECK (status = 'VOIDED' OR void_decision_reference IS NULL);

DROP INDEX body_memberships_open_unique_idx;

CREATE UNIQUE INDEX body_memberships_open_unique_idx
    ON body_memberships (member_id, body_id, member_type_id)
    WHERE valid_to IS NULL AND status = 'ACTIVE';

-- Seeded bodies are the association's statutory structure installed by the migrations; the panel
-- never deletes them (A3). Bodies inserted so far without a creating member are exactly the seed
-- catalogue. Every future seed migration must mark its bodies with seeded = TRUE explicitly —
-- this UPDATE cannot know catalogues that arrive later.
ALTER TABLE bodies
    ADD COLUMN seeded BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE bodies
SET seeded = TRUE
WHERE created_by_user_id IS NULL;
