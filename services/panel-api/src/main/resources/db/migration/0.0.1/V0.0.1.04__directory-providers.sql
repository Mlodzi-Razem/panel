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

CREATE SEQUENCE seq_directory_provider_google_config_id START WITH 1 INCREMENT BY 1;
CREATE TABLE directory_provider_google_config
(
    id                 BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_directory_provider_google_config_id'),
    created_at         timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT,
    updated_by_user_id BIGINT,
    FOREIGN KEY (created_by_user_id) REFERENCES members (id),
    FOREIGN KEY (updated_by_user_id) REFERENCES members (id),

    domain             TEXT        NOT NULL,
    name               TEXT        NOT NULL,
    client_id          TEXT        NOT NULL,
    client_secret      TEXT        NOT NULL
);