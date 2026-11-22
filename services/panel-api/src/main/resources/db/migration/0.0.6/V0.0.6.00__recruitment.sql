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

-- Recruitment module (statute jednolity 3.10.2026, art. 9 ust. 2-5; flowchart "Procedura rekrutacyjna").
-- Case statuses mirror the as-is spreadsheet diagram; PROBATION_EXTENDED covers the statutory one-time
-- extension of the "okres wstępny" (art. 9 ust. 4 pkt 5-6). Candidates never land in members/member_emails
-- before PRZYJĘTA (GDPR minimisation): a case keeps only the contact data needed to run the process.

CREATE TYPE recruitment_case_status AS ENUM ('NEW', 'IN_PROGRESS', 'NO_CONTACT', 'AWAITING_DECLARATION',
    'DECLARATION_RECEIVED', 'PROBATION', 'PROBATION_EXTENDED', 'ZO_RECOMMENDED', 'ZK_VOTING', 'ACCEPTED',
    'REJECTED');

CREATE TYPE recruitment_rejection_reason AS ENUM ('NOT_FIT', 'NO_DECLARATION', 'NO_CONTACT', 'VOTE_FAILED',
    'FALSE_ENTRY', 'ZK_BAN');

CREATE TYPE recruitment_source AS ENUM ('GOOGLE_FORM', 'WEBSITE', 'MANUAL');

CREATE TYPE declaration_form AS ENUM ('SCAN', 'ELECTRONIC');

CREATE TYPE recruitment_assessment_track AS ENUM ('STANDARD', 'AFFILIATED', 'ENGAGED');

CREATE TYPE recruitment_decision_kind AS ENUM ('ZO_ASSESSMENT_POSITIVE', 'ZO_ASSESSMENT_NEGATIVE',
    'PERIOD_EXTENSION', 'REJECTION', 'ZK_APPROVAL', 'ZO_ADMISSION_RESOLUTION');

CREATE TYPE recruitment_case_event_type AS ENUM ('CASE_CREATED', 'STATUS_CHANGED', 'REMINDER_SENT',
    'ZK_NOTIFIED_PROBATION_START', 'DECLARATION_RECORDED', 'RECOMMENDATION_SENT', 'VOTE_STARTED',
    'DECISION_RECORDED', 'CASE_CLOSED', 'NOTE_ADDED');

-- Member-number allocation (PO-7): numbers are allocated automatically at PRZYJĘTA from this sequence.
-- The reserved test band 950000-999999 can never be produced (MAXVALUE 949999, NO CYCLE); application code
-- additionally rejects band values on manual member creation (MemberRules). The sequence starts beyond the
-- highest numeric member number present at migration time, so it does not collide with existing numbers.
CREATE SEQUENCE seq_member_number_allocation AS BIGINT
    MINVALUE 1 MAXVALUE 949999 START WITH 1 INCREMENT BY 1 CACHE 50 NO CYCLE;

DO
$$
DECLARE
    number     BIGINT;
    highest    BIGINT := 0;
BEGIN
    FOR number IN
        SELECT NULLIF(regexp_replace(member_id, '\D', '', 'g'), '')::BIGINT
        FROM members
        WHERE member_id IS NOT NULL
          AND regexp_replace(member_id, '\D', '', 'g') <> ''
          AND length(regexp_replace(member_id, '\D', '', 'g')) <= 15
        LOOP
            IF number < 950000 AND number > highest THEN
                highest := number;
            END IF;
        END LOOP;

    IF highest > 0 THEN
        PERFORM setval('seq_member_number_allocation', highest);
    END IF;
END;
$$;

CREATE SEQUENCE seq_recruitment_cases_id INCREMENT BY 1 CACHE 50;

CREATE TABLE recruitment_cases
(
    id                  BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_recruitment_cases_id'),
    created_at          timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    district_body_id    BIGINT                       NOT NULL REFERENCES bodies (id),

    candidate_first_name   TEXT,
    candidate_last_name    TEXT,
    candidate_email        TEXT,
    candidate_phone        TEXT,
    candidate_social_profile_url TEXT,
    candidate_birth_date   DATE,

    source              recruitment_source           NOT NULL DEFAULT 'MANUAL',
    status              recruitment_case_status      NOT NULL DEFAULT 'NEW',
    rejection_reason    recruitment_rejection_reason,

    declaration_form    declaration_form,
    declaration_reference TEXT,
    declaration_received_on DATE,

    assessment_track    recruitment_assessment_track NOT NULL DEFAULT 'STANDARD',
    assessment_not_before_on DATE,
    assessment_not_later_on  DATE,
    extension_count     INT                          NOT NULL DEFAULT 0,
    extension_decided_on    DATE,
    second_assessment_not_later_on DATE,
    admission_resolution_not_later_on DATE,
    zo_recommended_on   DATE,

    accepted_on         DATE,
    accepted_member_id  BIGINT REFERENCES members (id),
    closed_at           DATE,
    anonymized_at       timestamptz,

    -- names and contact data: GDPR split so that anonymising a closed case in M8 is mechanical
    CHECK ((candidate_first_name IS NULL) = (candidate_last_name IS NULL)),
    CHECK (anonymized_at IS NOT NULL OR candidate_first_name IS NOT NULL),
    CHECK (anonymized_at IS NULL OR (candidate_email IS NULL AND candidate_phone IS NULL
        AND candidate_social_profile_url IS NULL AND candidate_birth_date IS NULL)),

    -- retention/purge lens (PO-3): a case is purged when closed long enough
    CHECK ((status IN ('ACCEPTED', 'REJECTED')) = (closed_at IS NOT NULL)),

    -- rejection bookkeeping
    CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),

    -- statutory clock of the "okres wstępny": set once the declaration arrives (art. 9 ust. 4 pkt 4, 9)
    CHECK (declaration_form IS NULL OR declaration_received_on IS NOT NULL),
    CHECK (assessment_not_before_on IS NULL OR declaration_received_on IS NOT NULL),
    CHECK (assessment_not_later_on IS NULL OR assessment_not_before_on IS NOT NULL),
    CHECK (assessment_not_before_on IS NULL OR assessment_not_later_on >= assessment_not_before_on),
    CHECK (status NOT IN ('DECLARATION_RECEIVED', 'PROBATION', 'PROBATION_EXTENDED', 'ZO_RECOMMENDED',
                          'ZK_VOTING', 'ACCEPTED')
        OR (declaration_received_on IS NOT NULL AND declaration_form IS NOT NULL
            AND assessment_not_before_on IS NOT NULL AND assessment_not_later_on IS NOT NULL)),
    CHECK (status NOT IN ('NEW', 'IN_PROGRESS', 'NO_CONTACT', 'AWAITING_DECLARATION')
        OR (declaration_received_on IS NULL AND declaration_form IS NULL
            AND assessment_not_before_on IS NULL AND assessment_not_later_on IS NULL)),

    -- extension of the initial period happens at most once (art. 9 ust. 4 pkt 5-7)
    CHECK (extension_count BETWEEN 0 AND 1),
    CHECK ((extension_count = 1) = (extension_decided_on IS NOT NULL)),
    CHECK ((extension_count = 1) = (second_assessment_not_later_on IS NOT NULL)),
    CHECK (second_assessment_not_later_on IS NULL OR second_assessment_not_later_on >= extension_decided_on),

    -- recommendation and the 7-day admission resolution deadline (art. 9 ust. 4 pkt 8)
    CHECK (admission_resolution_not_later_on IS NULL OR zo_recommended_on IS NOT NULL),
    CHECK (admission_resolution_not_later_on IS NULL OR admission_resolution_not_later_on >= zo_recommended_on),
    CHECK (status NOT IN ('ZO_RECOMMENDED', 'ZK_VOTING', 'ACCEPTED')
        OR (zo_recommended_on IS NOT NULL AND admission_resolution_not_later_on IS NOT NULL)),

    -- acceptance bookkeeping (PO-7: member number allocated then, accepted member row linked)
    CHECK ((status = 'ACCEPTED') = (accepted_on IS NOT NULL)),
    CHECK ((status = 'ACCEPTED') = (accepted_member_id IS NOT NULL)),
    CHECK (accepted_on IS NULL OR (declaration_received_on IS NOT NULL AND zo_recommended_on IS NOT NULL
        AND accepted_on > declaration_received_on))
);

CREATE INDEX recruitment_cases_district_status_idx ON recruitment_cases (district_body_id, status);
CREATE INDEX recruitment_cases_open_status_idx ON recruitment_cases (status) WHERE closed_at IS NULL;
CREATE INDEX recruitment_cases_assessment_due_idx
    ON recruitment_cases (assessment_not_later_on)
    WHERE closed_at IS NULL AND assessment_not_later_on IS NOT NULL;
CREATE INDEX recruitment_cases_accepted_member_idx
    ON recruitment_cases (accepted_member_id) WHERE accepted_member_id IS NOT NULL;

-- one open case per e-mail per okręg; re-applying is possible after the previous case is closed
CREATE UNIQUE INDEX recruitment_cases_open_email_district_idx
    ON recruitment_cases (district_body_id, LOWER(candidate_email))
    WHERE closed_at IS NULL AND candidate_email IS NOT NULL;

-- admission of a case (PO-2, art. 9 ust. 2 pkt 2) requires BOTH the prior ZK approval
-- and the ZO admission resolution; guarded at the schema level
CREATE FUNCTION recruitment_cases_require_both_decisions() RETURNS TRIGGER AS
$$
DECLARE
    approvals   INT;
    resolutions INT;
BEGIN
    IF NEW.status = 'ACCEPTED' THEN
        SELECT COUNT(*) INTO approvals
        FROM recruitment_decisions
        WHERE case_id = NEW.id AND decision_kind = 'ZK_APPROVAL';

        SELECT COUNT(*) INTO resolutions
        FROM recruitment_decisions
        WHERE case_id = NEW.id AND decision_kind = 'ZO_ADMISSION_RESOLUTION';

        IF approvals = 0 OR resolutions = 0 THEN
            RAISE EXCEPTION 'accepting a recruitment case requires both the ZK approval and the ZO admission resolution'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER recruitment_cases_require_both_decisions
    BEFORE INSERT OR UPDATE OF status, accepted_member_id
    ON recruitment_cases
    FOR EACH ROW
EXECUTE FUNCTION recruitment_cases_require_both_decisions();

CREATE SEQUENCE seq_recruitment_case_events_id INCREMENT BY 1 CACHE 50;

CREATE TABLE recruitment_case_events
(
    id                  BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_recruitment_case_events_id'),
    created_at          timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    case_id             BIGINT                        NOT NULL REFERENCES recruitment_cases (id),
    event_type          recruitment_case_event_type    NOT NULL,
    payload             JSONB
);

CREATE INDEX recruitment_case_events_case_idx ON recruitment_case_events (case_id, created_at);

CREATE SEQUENCE seq_recruitment_decisions_id INCREMENT BY 1 CACHE 50;

CREATE TABLE recruitment_decisions
(
    id                  BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_recruitment_decisions_id'),
    created_at          timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    case_id             BIGINT                     NOT NULL REFERENCES recruitment_cases (id),
    deciding_body_id    BIGINT                     NOT NULL REFERENCES bodies (id),
    decision_kind       recruitment_decision_kind  NOT NULL,
    decision_signature  TEXT                       NOT NULL,
    decided_on           DATE                       NOT NULL,
    note                TEXT,

    UNIQUE (case_id, decision_kind)
);

CREATE INDEX recruitment_decisions_body_idx ON recruitment_decisions (deciding_body_id, decided_on);

CREATE SEQUENCE seq_admission_bans_id INCREMENT BY 1 CACHE 50;

CREATE TABLE admission_bans
(
    id                  BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_admission_bans_id'),
    created_at          timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    case_id             BIGINT REFERENCES recruitment_cases (id),
    formal_identifier   TEXT,
    decision_signature  TEXT NOT NULL,
    effective_from      DATE NOT NULL,
    effective_until     DATE NOT NULL,
    note                TEXT,

    CHECK (effective_until > effective_from)
);

CREATE INDEX admission_bans_active_idx ON admission_bans (formal_identifier) WHERE formal_identifier IS NOT NULL;
CREATE INDEX admission_bans_case_idx ON admission_bans (case_id) WHERE case_id IS NOT NULL;
