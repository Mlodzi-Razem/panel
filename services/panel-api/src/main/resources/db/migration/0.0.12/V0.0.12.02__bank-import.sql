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

CREATE TYPE bank_transaction_status AS ENUM ('MATCHED', 'UNMATCHED', 'IGNORED', 'OUTGOING');

CREATE TYPE bank_match_method AS ENUM ('REFERENCE', 'IBAN', 'MANUAL');

CREATE SEQUENCE seq_bank_statement_imports_id INCREMENT BY 1 CACHE 50;

CREATE TABLE bank_statement_imports
(
    id                     BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_bank_statement_imports_id'),
    created_at             timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at             timestamptz,
    created_by_user_id     BIGINT REFERENCES members (id),
    updated_by_user_id     BIGINT REFERENCES members (id),

    file_name              TEXT        NOT NULL,
    file_sha256            TEXT        NOT NULL,
    format                 TEXT        NOT NULL,
    account_iban           TEXT        NOT NULL,
    currency               TEXT        NOT NULL,
    statement_id           TEXT,
    period_from            DATE,
    period_to              DATE,
    transactions_total     INTEGER     NOT NULL,
    transactions_new       INTEGER     NOT NULL,
    transactions_duplicate INTEGER     NOT NULL,
    transactions_matched   INTEGER     NOT NULL,
    transactions_unmatched INTEGER     NOT NULL,
    transactions_outgoing  INTEGER     NOT NULL,
    imported_by_id         BIGINT      NOT NULL REFERENCES members (id),

    UNIQUE (account_iban, file_sha256),
    CHECK (format IN ('CAMT_053', 'MT940')),
    CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (transactions_total >= 0 AND transactions_new >= 0 AND transactions_duplicate >= 0)
);

CREATE SEQUENCE seq_bank_transactions_id INCREMENT BY 1 CACHE 50;

CREATE TABLE bank_transactions
(
    id                 BIGINT                  NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_bank_transactions_id'),
    created_at         timestamptz             NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    import_id          BIGINT                  NOT NULL REFERENCES bank_statement_imports (id),
    account_iban       TEXT                    NOT NULL,
    stable_key         TEXT                    NOT NULL,
    booking_date       DATE                    NOT NULL,
    value_date         DATE,
    amount             NUMERIC(14, 2)          NOT NULL,
    currency           TEXT                    NOT NULL,
    counterparty_name  TEXT,
    counterparty_iban  TEXT,
    title              TEXT                    NOT NULL,
    bank_reference     TEXT,
    status             bank_transaction_status NOT NULL,
    match_method       bank_match_method,
    ignored_note       TEXT,
    decided_by_id      BIGINT REFERENCES members (id),
    decided_at         timestamptz,

    UNIQUE (account_iban, stable_key),
    CHECK ((status = 'MATCHED') = (match_method IS NOT NULL)),
    CHECK (status = 'IGNORED' OR ignored_note IS NULL)
);

CREATE INDEX bank_transactions_import_idx ON bank_transactions (import_id);
CREATE INDEX bank_transactions_status_idx ON bank_transactions (status, booking_date DESC);

ALTER TABLE fee_payments
    ADD CONSTRAINT fee_payments_bank_transaction_fk FOREIGN KEY (bank_transaction_id) REFERENCES bank_transactions (id);
