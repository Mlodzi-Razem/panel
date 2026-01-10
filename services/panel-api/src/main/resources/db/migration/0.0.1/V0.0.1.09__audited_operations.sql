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
CREATE SEQUENCE seq_audited_operations_id INCREMENT BY 50;

CREATE TABLE audited_operations
(
    id                  BIGINT PRIMARY KEY DEFAULT NEXTVAL('seq_audited_operations_id'),

    operation_id        TEXT      NOT NULL,
    operation_trace_id  TEXT      NOT NULL,
    operation_timestamp TIMESTAMP NOT NULL,
    operation_payload   jsonb     NOT NULL,

    triggered_by_id     BIGINT,
    triggered_by_ip     TEXT
);