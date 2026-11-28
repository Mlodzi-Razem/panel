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

-- Provisioning of Microsoft Entra accounts and group memberships (package B2): the transactional outbox and the
-- registry of the accounts the Panel manages.
--
-- Neither table holds personal data: tasks reference a member or a recruitment case by id, a group mapping and a
-- business occurrence by id, and keep only an operation, a state, counters and a short error code. Names, e-mail
-- addresses and districts are read when a task executes. Neither a Temporary Access Pass nor a Graph error message
-- is ever stored. member_id / case_id / mapping_id carry no foreign keys on purpose: anonymising a case or retiring
-- a mapping must never be blocked by, or rewrite, the history of what was provisioned.

CREATE SEQUENCE seq_directory_provisioning_tasks_id INCREMENT BY 1 CACHE 1;

CREATE TABLE directory_provisioning_tasks
(
    id                      BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_directory_provisioning_tasks_id'),
    created_at              timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    created_by_member_id    BIGINT,

    operation               TEXT        NOT NULL
        CHECK (operation IN ('CREATE_TRIAL_ACCOUNT', 'CONVERT_TRIAL_TO_MEMBER', 'CREATE_MEMBER_ACCOUNT',
                             'ADOPT_MEMBER_ACCOUNT', 'DISABLE_ACCOUNT', 'ENABLE_ACCOUNT', 'DELETE_ACCOUNT',
                             'ADD_GROUP_MEMBER', 'REMOVE_GROUP_MEMBER')),
    subject_kind            TEXT        NOT NULL CHECK (subject_kind IN ('MEMBER', 'CANDIDATE')),
    subject_id              BIGINT      NOT NULL,
    -- CONVERT_TRIAL_TO_MEMBER: the recruitment case whose trial account becomes the member's account
    related_case_id         BIGINT,
    mapping_id              BIGINT,
    occurrence_id           BIGINT,
    idempotency_key         TEXT        NOT NULL UNIQUE,

    state                   TEXT        NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'RUNNING', 'HELD', 'DONE', 'FAILED', 'SUPERSEDED')),
    attempts                INT         NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at         timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- the moment from which the task counts as fresh: the creation, or the last retry / release by a person
    ready_since             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- a person released the task from HELD: it bypasses the age and hourly-limit holds
    released                BOOLEAN     NOT NULL DEFAULT FALSE,
    released_by_member_id   BIGINT,
    lease_owner             TEXT,
    lease_until             timestamptz,
    started_at              timestamptz,
    finished_at             timestamptz,
    last_error_code         TEXT CHECK (last_error_code ~ '^[A-Z0-9_.-]{1,64}$'),
    superseded_by           BIGINT REFERENCES directory_provisioning_tasks (id),

    CHECK ((state IN ('DONE', 'FAILED', 'SUPERSEDED')) = (finished_at IS NOT NULL)),
    CHECK ((state = 'RUNNING') = (lease_owner IS NOT NULL))
);

CREATE INDEX directory_provisioning_tasks_due_idx ON directory_provisioning_tasks (state, next_attempt_at);
CREATE INDEX directory_provisioning_tasks_subject_idx ON directory_provisioning_tasks (subject_kind, subject_id, id);
CREATE INDEX directory_provisioning_tasks_destructive_idx ON directory_provisioning_tasks (started_at)
    WHERE operation IN ('DISABLE_ACCOUNT', 'DELETE_ACCOUNT', 'REMOVE_GROUP_MEMBER');

CREATE SEQUENCE seq_directory_accounts_id INCREMENT BY 1 CACHE 1;

-- The accounts the Panel manages: created by it, or explicitly adopted. Only these are ever written to in Entra.
-- Technical identifiers only: the Entra object id and the six digits of the user principal name.
CREATE TABLE directory_accounts
(
    id              BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_directory_accounts_id'),
    created_at      timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,

    member_id       BIGINT UNIQUE,
    case_id         BIGINT UNIQUE,
    kind            TEXT        NOT NULL CHECK (kind IN ('TRIAL', 'MEMBER')),
    origin          TEXT        NOT NULL CHECK (origin IN ('CREATED', 'ADOPTED')),
    account_number  CHAR(6)     NOT NULL UNIQUE CHECK (account_number ~ '^[0-9]{6}$'),
    entra_object_id UUID        NOT NULL UNIQUE,
    status          TEXT        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED')),

    CHECK ((member_id IS NULL) <> (case_id IS NULL)),
    CHECK ((kind = 'MEMBER') = (member_id IS NOT NULL))
);
