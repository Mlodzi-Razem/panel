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

-- Spring Modulith 2.0.1 event publication registry (schema v2, the default: use-legacy-structure=false).
-- DDL taken from spring-modulith-events-jdbc-2.0.1.jar, schemas/v2/schema-postgresql.sql, with the events. prefix.
-- IF NOT EXISTS: stage/dev may already have the table, created by the Modulith init DDL before this migration.
CREATE SCHEMA IF NOT EXISTS events;

CREATE TABLE IF NOT EXISTS events.event_publication
(
  id                     UUID                     NOT NULL,
  listener_id            TEXT                     NOT NULL,
  event_type             TEXT                     NOT NULL,
  serialized_event       TEXT                     NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON events.event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON events.event_publication (completion_date);
