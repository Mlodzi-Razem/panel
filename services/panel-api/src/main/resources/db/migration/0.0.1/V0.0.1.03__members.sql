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

CREATE TYPE member_status AS ENUM ('ACTIVE', 'REMOVED', 'SUSPENDED', 'FORM_REQUEST', 'PROBATION');

CREATE SEQUENCE seq_members_id MINVALUE 0 INCREMENT 1 CACHE 50;

CREATE TABLE members
(
    id                          BIGINT        NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_members_id'),
    created_at                  timestamptz   NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at                  timestamptz,
    created_by_user_id          BIGINT,
    updated_by_user_id          BIGINT,
    FOREIGN KEY (created_by_user_id) REFERENCES members (id),
    FOREIGN KEY (updated_by_user_id) REFERENCES members (id),

    status                      member_status NOT NULL,
    member_id                   TEXT,
    preferred_name_first        TEXT          NOT NULL,
    preferred_name_middle       TEXT,
    preferred_name_last         TEXT          NOT NULL,
    preferred_name              TEXT GENERATED ALWAYS AS (REPLACE(
        (preferred_name_first || ' ' || COALESCE(preferred_name_middle, '') || ' ' || preferred_name_last), '  ',
        ' ')) VIRTUAL,
    formal_name                 TEXT          NOT NULL,
    pseudonym                   TEXT,
    domain_email                TEXT,
    domain_email_aliases        TEXT[]        NOT NULL             DEFAULT '{}',
    private_emails              TEXT[]        NOT NULL             DEFAULT '{}',
    phone_numbers               TEXT[]        NOT NULL             DEFAULT '{}',
    probation_period_start_date DATE,
    probation_period_end_date   DATE,
    request_date                DATE,
    member_since                DATE,
    removed_at                  DATE,
    formal_identifier           TEXT,
    birth_date                  DATE          NOT NULL,

    address_country_code        CHAR(3)       NOT NULL,
    address_postal_code         TEXT          NOT NULL,
    address_state               TEXT          NOT NULL,
    address_province            TEXT          NOT NULL,
    address_city                TEXT          NOT NULL,
    address_street              TEXT          NOT NULL,
    address_building            TEXT          NOT NULL,
    address_apartment           TEXT          NOT NULL,

    trgm_search                 TEXT GENERATED ALWAYS AS (
        preferred_name_first || COALESCE(preferred_name_middle, '') || ' ' || preferred_name_last || ' ' ||
        COALESCE(domain_email, '') || ' ' || pseudonym || ' ' || array_to_string_immutable(domain_email_aliases, ',') ||
        formal_name ) STORED,

    UNIQUE (member_id),
    UNIQUE (domain_email),
    UNIQUE (formal_identifier),
    UNIQUE (private_emails)
);

CREATE INDEX members_trgm_search_idx ON members USING gin (trgm_search gin_trgm_ops);

CREATE INDEX members_status_idx ON members (status);
