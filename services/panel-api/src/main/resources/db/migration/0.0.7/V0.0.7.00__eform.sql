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

-- Electronic declaration (M4a). Additive only on top of 0.0.6.
-- Owner decisions (0.0.6-era backlog + 2026-11-22 pack): magic-link forms, no address block in the e-form,
-- canonical server timestamp (no "miejscowość" field), template "2026" without the <16 parent block,
-- supporting-membership variant only as a template marker (outside the workflow engine), bans checked
-- at declaration submission and blocked without details.

-- Candidate data collected by the e-declaration form (paper form fields A.1-A.6, B.7-B.8).
-- Formal names carry over into the NOT NULL members formal_name columns at PRZYJĘTA (M6 hand-over).
ALTER TABLE recruitment_cases
    ADD COLUMN candidate_formal_first_name TEXT,
    ADD COLUMN candidate_formal_last_name  TEXT,
    ADD COLUMN candidate_preferred_first_name TEXT,
    ADD COLUMN candidate_preferred_last_name  TEXT,
    ADD COLUMN candidate_pronouns TEXT,
    -- candidate PESEL, same idiom as members.formal_identifier; GDPR Art. 9 — never logged, never in
    -- event/audit payloads (names-only rule), value dusted at case anonymization (M8 must clear it too)
    ADD COLUMN candidate_formal_identifier TEXT,
    -- provisional trial-period account id (97xxxx-99xxxx per dev/README §7.3); UPN/employeeId marker only,
    -- provisioning/rename/delete lifecycle belongs to M6; stays NULL until DEKLARACJA DOSZŁA
    ADD COLUMN trial_account_number BIGINT;

ALTER TABLE recruitment_cases
    ADD CONSTRAINT recruitment_cases_formal_name_pair_ck
        CHECK ((candidate_formal_first_name IS NULL) = (candidate_formal_last_name IS NULL)),
    ADD CONSTRAINT recruitment_cases_preferred_name_pair_ck
        CHECK ((candidate_preferred_first_name IS NULL) = (candidate_preferred_last_name IS NULL)),
    ADD CONSTRAINT recruitment_cases_trial_account_band_ck
        CHECK (trial_account_number IS NULL OR (trial_account_number BETWEEN 970000 AND 999999));

-- one open case per candidate PESEL: a second open case with the same formal identifier is not accepted
-- (duplicate submission surfaces as a generic block, like an admission ban; pre-existing members are
-- checked by the service against members.formal_identifier)
CREATE UNIQUE INDEX recruitment_cases_open_pesel_idx
    ON recruitment_cases (candidate_formal_identifier)
    WHERE closed_at IS NULL AND candidate_formal_identifier IS NOT NULL;

-- GDPR: candidate PESEL cannot survive the anonymization of the case (M8 wipes it mechanically
-- with the other contact data covered by V0.0.6.00 checks on anonymized_at)
ALTER TABLE recruitment_cases
    ADD CONSTRAINT recruitment_cases_anonymized_pesel_ck
        CHECK (anonymized_at IS NULL OR candidate_formal_identifier IS NULL);

-- (backlog (b)) a ban must be anchored to at least one identifier: the banned person's case or their
-- formal identifier (art. 9 ust. 5 bans outlive the case)
ALTER TABLE admission_bans
    ADD CONSTRAINT admission_bans_identifier_required_ck
        CHECK (case_id IS NOT NULL OR formal_identifier IS NOT NULL);

CREATE TYPE declaration_session_status AS ENUM ('CREATED', 'FILLED', 'SIGNED', 'EXPIRED');

-- Magic-link sessions of the e-declaration form. The token itself never reaches the database: only its
-- SHA-256 hash, so a database leak cannot be replayed against the public form endpoints.
CREATE SEQUENCE seq_declaration_form_sessions_id INCREMENT BY 1 CACHE 50;

CREATE TABLE declaration_form_sessions
(
    id                  BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_declaration_form_sessions_id'),
    created_at          timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    case_id             BIGINT                     NOT NULL REFERENCES recruitment_cases (id),
    token_hash          TEXT                       NOT NULL UNIQUE,
    status              declaration_session_status NOT NULL DEFAULT 'CREATED',
    expires_at          timestamptz                NOT NULL,
    filled_at           timestamptz,
    signed_at           timestamptz,
    -- names of the form fields the candidate submitted (names-only rule; the values live on the case row)
    submitted_fields    JSONB,

    CHECK (status <> 'FILLED' OR filled_at IS NOT NULL),
    CHECK (status <> 'SIGNED' OR signed_at IS NOT NULL),
    CHECK (status IN ('SIGNED', 'EXPIRED') OR signed_at IS NULL)
);

-- one live link per case: creating a new link supersedes the previous session
CREATE UNIQUE INDEX declaration_form_sessions_live_case_idx
    ON declaration_form_sessions (case_id)
    WHERE status IN ('CREATED', 'FILLED');

CREATE INDEX declaration_form_sessions_case_idx ON declaration_form_sessions (case_id);

CREATE TYPE declaration_artifact_kind AS ENUM ('TEMPLATE_PDF', 'SIGNED_PDF', 'SCAN');
CREATE TYPE declaration_signature_type AS ENUM ('QES', 'PZ', 'SCAN');

-- Declaration artifacts: the rendered template PDF, the signed PAdES file, or the paper-scan fallback.
-- validation_result keeps only the names of the performed checks (GDPR discipline), never raw payloads.
CREATE SEQUENCE seq_declaration_artifacts_id INCREMENT BY 1 CACHE 50;

CREATE TABLE declaration_artifacts
(
    id                  BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_declaration_artifacts_id'),
    created_at          timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    case_id             BIGINT                    NOT NULL REFERENCES recruitment_cases (id),
    session_id          BIGINT                    REFERENCES declaration_form_sessions (id),
    artifact_kind       declaration_artifact_kind NOT NULL,
    content_sha256      TEXT                      NOT NULL,
    storage_ref         TEXT                      NOT NULL,
    document_sha256     TEXT,
    template_version    TEXT,
    signature_type      declaration_signature_type,
    signer_name         TEXT,
    validated_at        timestamptz,
    validation_result   JSONB,

    -- signed artifacts and scans carry the signature metadata, the rendered template does not
    CHECK (artifact_kind = 'TEMPLATE_PDF'
        OR (signature_type IS NOT NULL AND signer_name IS NOT NULL AND validated_at IS NOT NULL)),
    CHECK (artifact_kind <> 'TEMPLATE_PDF' OR signature_type IS NULL)
);

CREATE INDEX declaration_artifacts_case_idx ON declaration_artifacts (case_id);

CREATE TYPE recruitment_consent_kind AS ENUM ('IMAGE', 'SPECIAL_CATEGORY_9_2D');

-- Consents given with the declaration (Zgoda form, 16+ block only; the <16 parent branch is dropped
-- in the 2026 template per the owner's decision). Withdrawal of the image consent stays a later flow:
-- withdrawn_at is reserved for it.
CREATE SEQUENCE seq_recruitment_consents_id INCREMENT BY 1 CACHE 50;

CREATE TABLE recruitment_consents
(
    id                  BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_recruitment_consents_id'),
    created_at          timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    case_id             BIGINT                   NOT NULL REFERENCES recruitment_cases (id),
    consent_kind        recruitment_consent_kind NOT NULL,
    granted_by          TEXT                     NOT NULL,
    granted_at          timestamptz              NOT NULL,
    artifact_id         BIGINT                   REFERENCES declaration_artifacts (id),
    withdrawn_at        timestamptz,

    UNIQUE (case_id, consent_kind)
);
