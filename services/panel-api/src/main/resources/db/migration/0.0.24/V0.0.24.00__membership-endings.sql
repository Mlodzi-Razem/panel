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

-- Mandate endings (A3-3). The number in the file name is a working one; Landing assigns the final one.
--
-- body_memberships.decision_reference is the appointing resolution (older rows may hold the resolution of
-- a correction, written there before this migration; they are left as they are). Ending a mandate never
-- touches it: every ending is a new row here, with the resolution that justified it and the end date it
-- replaced. A mandate can be shortened more than once, so one column would be overwritten by the second
-- resolution; the rows are only ever inserted.
CREATE SEQUENCE seq_body_membership_endings_id;

CREATE TABLE body_membership_endings
(
    id                 BIGINT      NOT NULL DEFAULT nextval('seq_body_membership_endings_id') PRIMARY KEY,
    body_membership_id BIGINT      NOT NULL REFERENCES body_memberships (id),
    previous_valid_to  DATE,
    valid_to           DATE        NOT NULL,
    decision_reference TEXT,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ
);

CREATE INDEX body_membership_endings_membership_idx ON body_membership_endings (body_membership_id);
