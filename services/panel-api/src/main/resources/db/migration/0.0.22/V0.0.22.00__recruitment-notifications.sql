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

-- Idempotency log of the recruitment notifications to the district boards (ZO) and the national board (ZK)
-- (module recruitment, package reminders). One row = one mail that Panel accepted for sending:
--   kind          which notification (ZO_DAILY_REMINDERS, ZK_PROBATION_DIGEST, ZK_RECOMMENDATION)
--   body_id       the board that received it
--   reference_key the day (digests) or `case:<id>` (one-off notification) - UNIQUE with kind and body, so the same
--                 day / the same case never produces a second mail, also under a race between two instances
--   covered_until for digests: the instant up to which the events were already reported (the next digest starts there)
-- Names and numbers only: no candidate data, no address, no text.

CREATE SEQUENCE seq_recruitment_notifications_id INCREMENT BY 1 CACHE 1;

CREATE TABLE recruitment_notifications
(
    id            BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_recruitment_notifications_id'),
    kind          TEXT        NOT NULL CHECK (kind IN ('ZO_DAILY_REMINDERS', 'ZK_PROBATION_DIGEST', 'ZK_RECOMMENDATION')),
    body_id       BIGINT      NOT NULL REFERENCES bodies (id),
    reference_key TEXT        NOT NULL,
    covered_until timestamptz,
    created_at    timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (kind, body_id, reference_key)
);
