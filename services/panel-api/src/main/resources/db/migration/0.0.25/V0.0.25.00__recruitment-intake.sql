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

-- Public "I want to join" submissions (recruitment intake). A submission is NOT a recruitment case: it is an
-- unverified request that waits in a queue until the district accepts it (then a case is created, source WEBSITE)
-- or it is discarded. Spam therefore never reaches the append-only case history.
-- Personal data is erased by retention; the row stays as a data-free tombstone (id, district, dates, status,
-- reason, consent version and time), so what happened to a submission is never rewritten.

CREATE SEQUENCE seq_recruitment_intakes_id INCREMENT BY 1 CACHE 1;

CREATE TABLE recruitment_intakes
(
    id                 BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_recruitment_intakes_id'),
    created_at         timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    -- NULL: the candidate does not know the district; such a submission carries a locality instead
    district_body_id   BIGINT REFERENCES bodies (id),

    first_name         TEXT,
    last_name          TEXT,
    email              TEXT,
    phone              TEXT,
    birth_date         DATE,
    locality           TEXT,

    consent_version    TEXT        NOT NULL,
    consented_at       timestamptz NOT NULL,

    status             TEXT        NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DISCARDED', 'EXPIRED')),
    discard_reason     TEXT
        CHECK (discard_reason IN ('SPAM', 'DUPLICATE', 'NOT_ELIGIBLE', 'NO_CONTACT', 'WITHDRAWN', 'OTHER')),
    closed_at          timestamptz,
    case_id            BIGINT REFERENCES recruitment_cases (id),
    data_erased_at     timestamptz,

    CHECK ((status = 'PENDING') = (closed_at IS NULL)),
    CHECK ((status = 'DISCARDED') = (discard_reason IS NOT NULL)),
    CHECK ((status = 'ACCEPTED') = (case_id IS NOT NULL)),
    CHECK (status <> 'PENDING' OR data_erased_at IS NULL),
    -- accepted data lives on in the case and expired data has no further purpose: both are erased at once
    CHECK (status NOT IN ('ACCEPTED', 'EXPIRED') OR data_erased_at IS NOT NULL),
    CHECK (data_erased_at IS NOT NULL
        OR (first_name IS NOT NULL AND last_name IS NOT NULL AND email IS NOT NULL AND birth_date IS NOT NULL)),
    CHECK (data_erased_at IS NULL
        OR (first_name IS NULL AND last_name IS NULL AND email IS NULL AND phone IS NULL AND birth_date IS NULL
            AND locality IS NULL))
);

-- one waiting submission per e-mail; a repeated one is silently ignored by the service
CREATE UNIQUE INDEX recruitment_intakes_pending_email_idx
    ON recruitment_intakes (LOWER(email)) WHERE status = 'PENDING';
CREATE INDEX recruitment_intakes_district_status_idx ON recruitment_intakes (district_body_id, status);

CREATE SEQUENCE seq_recruitment_intake_events_id INCREMENT BY 1 CACHE 1;

-- append-only history of a submission; the payload carries field names and codes only, never values
CREATE TABLE recruitment_intake_events
(
    id                 BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_recruitment_intake_events_id'),
    created_at         timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id BIGINT REFERENCES members (id),
    intake_id          BIGINT      NOT NULL REFERENCES recruitment_intakes (id),
    event_type         TEXT        NOT NULL
        CHECK (event_type IN ('RECEIVED', 'DISTRICT_ASSIGNED', 'ACCEPTED', 'DISCARDED', 'EXPIRED', 'DATA_ERASED')),
    payload            JSONB
);

CREATE INDEX recruitment_intake_events_intake_idx ON recruitment_intake_events (intake_id, id);

-- Global limits of the public form, shared by all instances and free of personal data. EVERY valid submission
-- takes a slot, also an ignored repeat of a waiting e-mail: otherwise somebody watching when the limit is reached
-- could tell whether an address had already applied. The slot of a stored submission is released when it is closed;
-- the slot of an ignored repeat just ages out.
CREATE SEQUENCE seq_recruitment_intake_slots_id INCREMENT BY 1 CACHE 1;

CREATE TABLE recruitment_intake_slots
(
    id          BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_recruitment_intake_slots_id'),
    created_at  timestamptz NOT NULL,
    intake_id   BIGINT REFERENCES recruitment_intakes (id),
    released_at timestamptz
);

CREATE INDEX recruitment_intake_slots_created_idx ON recruitment_intake_slots (created_at);
