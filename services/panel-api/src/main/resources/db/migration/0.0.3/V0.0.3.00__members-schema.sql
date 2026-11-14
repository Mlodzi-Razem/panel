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

CREATE TYPE membership_type AS ENUM ('ORDINARY', 'SUPPORTING', 'HONORARY');

CREATE TYPE body_kind AS ENUM ('GENERAL_ASSEMBLY', 'NATIONAL_BOARD', 'PEER_COURT', 'AUDIT_COMMISSION',
    'ELECTORAL_COMMISSION', 'PRESS_SPOKESPERSON', 'TEAM', 'DISTRICT', 'DISTRICT_ASSEMBLY', 'DISTRICT_BOARD',
    'DISTRICT_TREASURER', 'DISTRICT_COMMISSIONER', 'LOCAL_CIRCLE');

CREATE TYPE affiliation_kind AS ENUM ('PRIMARY', 'SUPPORTING');

CREATE TYPE change_request_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED');

ALTER TABLE members
    ADD COLUMN formal_name_first TEXT,
    ADD COLUMN formal_name_last TEXT;

UPDATE members
SET formal_name_first = CASE
                            WHEN POSITION(' ' IN BTRIM(formal_name)) > 0
                                THEN SUBSTRING(BTRIM(formal_name) FROM '^(.*) [^ ]+$')
                            ELSE BTRIM(formal_name) END,
    formal_name_last  = CASE
                            WHEN POSITION(' ' IN BTRIM(formal_name)) > 0
                                THEN SUBSTRING(BTRIM(formal_name) FROM '([^ ]+)$')
                            ELSE BTRIM(formal_name) END;

ALTER TABLE members
    ALTER COLUMN formal_name_first SET NOT NULL,
    ALTER COLUMN formal_name_last SET NOT NULL;

ALTER TABLE members
    DROP COLUMN trgm_search;

ALTER TABLE members
    DROP COLUMN formal_name;

ALTER TABLE members
    ADD COLUMN formal_name TEXT GENERATED ALWAYS AS (formal_name_first || ' ' || formal_name_last) STORED;

ALTER TABLE members
    ADD COLUMN pronouns             TEXT,
    ADD COLUMN institution          TEXT,
    ADD COLUMN social_profile_url   TEXT,
    ADD COLUMN address_line         TEXT,
    ADD COLUMN declaration_reference TEXT,
    ADD COLUMN membership_type      membership_type NOT NULL DEFAULT 'ORDINARY';

ALTER TABLE members
    ALTER COLUMN address_country_code DROP NOT NULL,
    ALTER COLUMN address_postal_code DROP NOT NULL,
    ALTER COLUMN address_state DROP NOT NULL,
    ALTER COLUMN address_province DROP NOT NULL,
    ALTER COLUMN address_city DROP NOT NULL,
    ALTER COLUMN address_street DROP NOT NULL,
    ALTER COLUMN address_building DROP NOT NULL,
    ALTER COLUMN address_apartment DROP NOT NULL;

ALTER TABLE members
    ADD COLUMN trgm_search TEXT GENERATED ALWAYS AS (
        preferred_name_first || ' ' ||
        COALESCE(preferred_name_middle || ' ', '') ||
        preferred_name_last || ' ' ||
        formal_name_first || ' ' ||
        formal_name_last ||
        COALESCE(' ' || pseudonym, '') ||
        COALESCE(' ' || domain_email, '') ||
        ' ' || array_to_string_immutable(domain_email_aliases, ' ') ||
        ' ' || array_to_string_immutable(private_emails, ' ')
        ) STORED;

CREATE INDEX members_trgm_search_idx ON members USING gin (trgm_search gin_trgm_ops);

CREATE INDEX members_domain_email_lower_idx ON members (LOWER(domain_email));

CREATE INDEX members_birth_date_idx ON members (birth_date);

ALTER TABLE body_templates
    ADD COLUMN kind                   body_kind,
    ADD COLUMN max_consecutive_terms  INT,
    ADD COLUMN term_length_months     INT,
    ADD COLUMN max_term_months        INT,
    ADD COLUMN max_extensions         INT;

ALTER TABLE body_templates
    ALTER COLUMN kind SET NOT NULL;

ALTER TABLE body_templates
    ADD CONSTRAINT body_templates_name_key UNIQUE (name),
    ADD CONSTRAINT body_templates_terms_check CHECK (
        (max_consecutive_terms IS NULL OR (max_consecutive_terms > 0 AND term_length_months > 0))
            AND (max_term_months IS NULL OR max_term_months > 0)
            AND (max_extensions IS NULL OR (max_extensions >= 0 AND max_term_months IS NOT NULL))
        );

ALTER TABLE body_template_member_types
    ADD COLUMN code TEXT;

ALTER TABLE body_template_member_types
    ADD CONSTRAINT body_template_member_types_code_key UNIQUE (body_template_id, code);

CREATE INDEX body_template_member_types_template_idx ON body_template_member_types (body_template_id);

ALTER TABLE bodies
    ADD COLUMN code TEXT;

ALTER TABLE bodies
    ADD CONSTRAINT bodies_code_key UNIQUE (code);

CREATE INDEX bodies_template_idx ON bodies (body_template_id);
CREATE INDEX bodies_parent_idx ON bodies (parent_body_id);

CREATE TABLE body_template_incompatibilities
(
    body_template_id              BIGINT NOT NULL REFERENCES body_templates (id) ON DELETE CASCADE,
    other_body_template_id        BIGINT NOT NULL REFERENCES body_templates (id) ON DELETE CASCADE,
    single_member_exempt_template_id BIGINT REFERENCES body_templates (id),
    PRIMARY KEY (body_template_id, other_body_template_id),
    CHECK (body_template_id < other_body_template_id),
    CHECK (single_member_exempt_template_id IS NULL OR single_member_exempt_template_id IN (body_template_id, other_body_template_id))
);

CREATE TABLE permission_group_permissions
(
    permission_group_id BIGINT          NOT NULL REFERENCES permission_group (id) ON DELETE CASCADE,
    permission_type     permission_type NOT NULL,
    PRIMARY KEY (permission_group_id, permission_type)
);

CREATE SEQUENCE seq_body_memberships_id INCREMENT BY 1 CACHE 50;

CREATE TABLE body_memberships
(
    id                 BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_body_memberships_id'),
    created_at         timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    member_id          BIGINT      NOT NULL REFERENCES members (id),
    body_id            BIGINT      NOT NULL REFERENCES bodies (id),
    member_type_id     BIGINT      NOT NULL REFERENCES body_template_member_types (id),

    valid_from         DATE        NOT NULL,
    valid_to           DATE,

    CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX body_memberships_member_idx ON body_memberships (member_id);
CREATE INDEX body_memberships_body_idx ON body_memberships (body_id);
CREATE INDEX body_memberships_member_type_idx ON body_memberships (member_type_id);
CREATE UNIQUE INDEX body_memberships_open_unique_idx
    ON body_memberships (member_id, body_id, member_type_id) WHERE valid_to IS NULL;

CREATE SEQUENCE seq_member_district_affiliations_id INCREMENT BY 1 CACHE 50;

CREATE TABLE member_district_affiliations
(
    id                 BIGINT           NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_member_district_affiliations_id'),
    created_at         timestamptz      NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    member_id          BIGINT           NOT NULL REFERENCES members (id),
    district_body_id   BIGINT           NOT NULL REFERENCES bodies (id),
    kind               affiliation_kind NOT NULL,
    votes_here         BOOLEAN          NOT NULL             DEFAULT FALSE,

    valid_from         DATE             NOT NULL,
    valid_to           DATE,

    CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX member_district_affiliations_member_idx ON member_district_affiliations (member_id);
CREATE INDEX member_district_affiliations_district_idx ON member_district_affiliations (district_body_id);
CREATE UNIQUE INDEX member_district_affiliations_open_primary_idx
    ON member_district_affiliations (member_id) WHERE valid_to IS NULL AND kind = 'PRIMARY';
CREATE UNIQUE INDEX member_district_affiliations_open_votes_idx
    ON member_district_affiliations (member_id) WHERE valid_to IS NULL AND votes_here;
CREATE UNIQUE INDEX member_district_affiliations_open_district_idx
    ON member_district_affiliations (member_id, district_body_id) WHERE valid_to IS NULL;

CREATE SEQUENCE seq_member_change_requests_id INCREMENT BY 1 CACHE 50;

CREATE TABLE member_change_requests
(
    id                 BIGINT                NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_member_change_requests_id'),
    created_at         timestamptz           NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    member_id          BIGINT                NOT NULL REFERENCES members (id),
    requested_by_id    BIGINT                NOT NULL REFERENCES members (id),
    proposed_values    JSONB                 NOT NULL,
    snapshot           JSONB                 NOT NULL,
    reason             TEXT,
    status             change_request_status NOT NULL             DEFAULT 'PENDING',
    decided_by_id      BIGINT REFERENCES members (id),
    decided_at         timestamptz,
    decision_note      TEXT,

    CHECK (status = 'PENDING' OR decided_at IS NOT NULL),
    CHECK (status <> 'PENDING' OR (decided_by_id IS NULL AND decided_at IS NULL))
);

CREATE INDEX member_change_requests_member_idx ON member_change_requests (member_id);
CREATE INDEX member_change_requests_status_idx ON member_change_requests (status);
CREATE UNIQUE INDEX member_change_requests_pending_idx ON member_change_requests (member_id) WHERE status = 'PENDING';
