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

-- Delivery log of transactional mail (module mail). Names only: the recipient's domain, never an address, a
-- subject or a body (bodies can carry one-time secrets). `external` marks recipients outside the association's
-- own domains and feeds the daily external-recipient limit (Exchange Online TERRL guard).

CREATE SEQUENCE seq_mail_deliveries_id INCREMENT BY 1 CACHE 1;

CREATE TABLE mail_deliveries
(
    id               BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_mail_deliveries_id'),
    purpose          TEXT        NOT NULL,
    reference        TEXT,
    recipient_domain TEXT        NOT NULL,
    external         BOOLEAN     NOT NULL,
    status           TEXT        NOT NULL CHECK (status IN ('SENT', 'FAILED', 'REFUSED_LIMIT', 'REFUSED_SENDER')),
    error_code       TEXT,
    created_at       timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mail_deliveries_external_created_at ON mail_deliveries (external, created_at);
