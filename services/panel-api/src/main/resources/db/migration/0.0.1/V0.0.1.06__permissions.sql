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

CREATE TYPE permission_scope AS ENUM ('LOCAL', 'GLOBAL', 'BODY');

CREATE TYPE permission_type AS ENUM ( 'FEES_READ', 'FEES_MANAGE', 'FORMAL_PERSONAL_DATA_READ', 'FORMAL_PERSONAL_DATA_MANAGE', 'CONTACT_PERSONAL_DATA_READ', 'CONTACT_PERSONAL_DATA_MANAGE', 'DIRECTORY_GROUPS_MANAGE', 'DIRECTORY_USERS_MANAGE', 'BODIES_MANAGE' );

CREATE SEQUENCE seq_permission_group_id INCREMENT 1 CACHE 50;

CREATE TABLE permission_group
(
    id                 BIGINT           NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_permission_group_id'),
    created_at         timestamptz      NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT,
    updated_by_user_id BIGINT,
    FOREIGN KEY (created_by_user_id) REFERENCES members (id),
    FOREIGN KEY (updated_by_user_id) REFERENCES members (id),

    name               TEXT             NOT NULL,
    scope              permission_scope NOT NULL,
    is_admin           BOOLEAN          NOT NULL             DEFAULT FALSE,
    email              TEXT,

    UNIQUE (scope, name)
);
