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

CREATE TYPE body_scope AS ENUM ('LOCAL', 'GLOBAL');

CREATE SEQUENCE seq_body_templates_id INCREMENT BY 1 CACHE 50;

CREATE TABLE body_templates
(
    id                      BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_body_templates_id'),
    created_at              timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at              timestamptz,
    created_by_user_id      BIGINT REFERENCES members (id),
    updated_by_user_id      BIGINT REFERENCES members (id),

    scope                   body_scope  NOT NULL,
    name                    TEXT        NOT NULL,

    body_parent_template_id BIGINT REFERENCES body_templates (id)
);

CREATE SEQUENCE seq_body_template_member_types_id INCREMENT BY 1 CACHE 50;

CREATE TABLE body_template_member_types
(
    id                  BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_body_template_member_types_id'),
    created_at          timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    name                TEXT        NOT NULL,
    is_default          BOOLEAN     NOT NULL,

    body_template_id    BIGINT      NOT NULL REFERENCES body_templates (id),
    permission_group_id BIGINT      NOT NULL
);

CREATE SEQUENCE seq_bodies_id INCREMENT BY 1 CACHE 50;

CREATE TABLE bodies
(
    id                 BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_bodies_id'),
    created_at         timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    body_template_id   BIGINT REFERENCES body_templates (id),
    parent_body_id     BIGINT REFERENCES bodies (id),

    name               TEXT        NOT NULL,
    email              TEXT,

    trgm_search        TEXT GENERATED ALWAYS AS (name || ' ' || email) STORED
);

CREATE INDEX bodies_trgm_search_idx ON bodies USING gin (trgm_search gin_trgm_ops);

