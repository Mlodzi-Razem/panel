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

CREATE SEQUENCE seq_suspensions_id INCREMENT BY 1 CACHE 50;

CREATE TABLE suspensions
(
    id                      BIGINT      NOT NULL PRIMARY KEY,
    created_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              timestamptz,
    created_by_user_id      BIGINT,
    updated_by_user_id      BIGINT,
    FOREIGN KEY (created_by_user_id) REFERENCES members (id),
    FOREIGN KEY (updated_by_user_id) REFERENCES members (id),

    suspended_user_id       BIGINT      NOT NULL,
    suspending_body_id      BIGINT      NOT NULL REFERENCES bodies (id),

    suspension_start        DATE        NOT NULL,
    suspension_end          DATE,

    decision_signature      TEXT,

    lifted_at               DATE,
    lifted_reason           TEXT,
    lifted_body             BIGINT REFERENCES bodies (id),
    lift_decision_signature TEXT,

    CHECK ((suspension_end IS NULL) OR (suspension_end > suspension_start)),
    CHECK ((lifted_at IS NULL AND lifted_reason IS NULL) OR
           (lifted_at IS NOT NULL AND lifted_reason IS NOT NULL AND lifted_at >= suspension_start))
);

CREATE INDEX suspensions_active_idx ON suspensions (suspension_end, lifted_at);