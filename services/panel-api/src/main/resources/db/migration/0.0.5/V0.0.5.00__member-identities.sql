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

-- A member can sign in with more than one identity provider while the association moves from
-- Google Workspace to Microsoft 365. Each row links one provider's immutable subject
-- (the Google "sub" or the Entra "oid") to a member. It replaces members.directory_user_id.

CREATE TYPE identity_provider AS ENUM ('GOOGLE', 'ENTRA');

CREATE SEQUENCE seq_member_identities_id INCREMENT BY 1 CACHE 50;

CREATE TABLE member_identities
(
    id                 BIGINT            NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_member_identities_id'),
    created_at         timestamptz       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    member_id          BIGINT            NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    provider           identity_provider NOT NULL,
    subject            TEXT              NOT NULL CHECK (btrim(subject) <> ''),

    CONSTRAINT member_identities_provider_subject_key UNIQUE (provider, subject),
    CONSTRAINT member_identities_member_provider_key UNIQUE (member_id, provider)
);

CREATE INDEX member_identities_member_id_idx ON member_identities (member_id);

INSERT INTO member_identities (member_id, provider, subject)
SELECT id, 'GOOGLE', directory_user_id
FROM members
WHERE directory_user_id IS NOT NULL
  AND btrim(directory_user_id) <> '';

ALTER TABLE members
    DROP COLUMN directory_user_id;
