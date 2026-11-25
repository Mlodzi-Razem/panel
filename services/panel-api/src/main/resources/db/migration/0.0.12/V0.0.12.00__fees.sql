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

CREATE TYPE fee_due_status AS ENUM ('OPEN', 'PAID', 'CANCELLED');

CREATE TYPE fee_adjustment_kind AS ENUM ('EXEMPTION', 'DISCOUNT', 'DEFERRAL');

CREATE TYPE fee_adjustment_reason AS ENUM ('FINANCIAL_HARDSHIP', 'HEALTH', 'OTHER');

CREATE TYPE fee_payment_source AS ENUM ('BANK', 'CASH', 'OTHER');

CREATE SEQUENCE seq_fee_tiers_id INCREMENT BY 1 CACHE 50;

CREATE TABLE fee_tiers
(
    id                 BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_fee_tiers_id'),
    created_at         timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    code               TEXT        NOT NULL,
    name               TEXT        NOT NULL,
    is_default         BOOLEAN     NOT NULL             DEFAULT FALSE,
    active             BOOLEAN     NOT NULL             DEFAULT TRUE,

    UNIQUE (code),
    CHECK (code = BTRIM(code) AND code <> ''),
    CHECK (BTRIM(name) <> ''),
    CHECK (NOT is_default OR active)
);

CREATE UNIQUE INDEX fee_tiers_single_default_idx ON fee_tiers (is_default) WHERE is_default;

CREATE SEQUENCE seq_fee_tier_rates_id INCREMENT BY 1 CACHE 50;

CREATE TABLE fee_tier_rates
(
    id                   BIGINT         NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_fee_tier_rates_id'),
    created_at           timestamptz    NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamptz,
    created_by_user_id   BIGINT REFERENCES members (id),
    updated_by_user_id   BIGINT REFERENCES members (id),

    tier_id              BIGINT         NOT NULL REFERENCES fee_tiers (id),
    monthly_amount       NUMERIC(12, 2) NOT NULL,
    currency             CHAR(3)        NOT NULL             DEFAULT 'PLN',
    valid_from           DATE           NOT NULL,
    resolution_reference TEXT           NOT NULL,

    UNIQUE (tier_id, valid_from),
    CHECK (monthly_amount > 0),
    CHECK (currency = 'PLN'),
    CHECK (EXTRACT(DAY FROM valid_from) = 1),
    CHECK (BTRIM(resolution_reference) <> '')
);

CREATE SEQUENCE seq_member_fee_tiers_id INCREMENT BY 1 CACHE 50;

CREATE TABLE member_fee_tiers
(
    id                 BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_member_fee_tiers_id'),
    created_at         timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    member_id          BIGINT      NOT NULL REFERENCES members (id),
    tier_id            BIGINT      NOT NULL REFERENCES fee_tiers (id),
    valid_from         DATE        NOT NULL,

    UNIQUE (member_id, valid_from),
    CHECK (EXTRACT(DAY FROM valid_from) = 1)
);

CREATE INDEX member_fee_tiers_tier_idx ON member_fee_tiers (tier_id);

CREATE SEQUENCE seq_fee_dues_id INCREMENT BY 1 CACHE 50;

CREATE TABLE fee_dues
(
    id                 BIGINT         NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_fee_dues_id'),
    created_at         timestamptz    NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    member_id          BIGINT         NOT NULL REFERENCES members (id),
    due_month          DATE           NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           CHAR(3)        NOT NULL             DEFAULT 'PLN',
    tier_id            BIGINT         NOT NULL REFERENCES fee_tiers (id),
    status             fee_due_status NOT NULL             DEFAULT 'OPEN',

    UNIQUE (member_id, due_month),
    CHECK (amount > 0),
    CHECK (currency = 'PLN'),
    CHECK (EXTRACT(DAY FROM due_month) = 1)
);

CREATE INDEX fee_dues_tier_month_idx ON fee_dues (tier_id, due_month);
CREATE INDEX fee_dues_open_idx ON fee_dues (due_month) WHERE status = 'OPEN';

CREATE SEQUENCE seq_fee_adjustments_id INCREMENT BY 1 CACHE 50;

CREATE TABLE fee_adjustments
(
    id                   BIGINT                NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_fee_adjustments_id'),
    created_at           timestamptz           NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamptz,
    created_by_user_id   BIGINT REFERENCES members (id),
    updated_by_user_id   BIGINT REFERENCES members (id),

    member_id            BIGINT                NOT NULL REFERENCES members (id),
    kind                 fee_adjustment_kind   NOT NULL,
    from_month           DATE                  NOT NULL,
    to_month             DATE,
    discount_amount      NUMERIC(12, 2),
    reason_category      fee_adjustment_reason NOT NULL,
    justification        TEXT                  NOT NULL,
    resolution_reference TEXT,
    granted_by_id        BIGINT                NOT NULL REFERENCES members (id),
    revoked_at           timestamptz,
    revoked_by_id        BIGINT REFERENCES members (id),
    revoked_from_month   DATE,

    CHECK (EXTRACT(DAY FROM from_month) = 1),
    CHECK (to_month IS NULL OR (EXTRACT(DAY FROM to_month) = 1 AND to_month >= from_month)),
    CHECK ((kind = 'DISCOUNT') = (discount_amount IS NOT NULL)),
    CHECK (discount_amount IS NULL OR discount_amount > 0),
    CHECK (BTRIM(justification) <> ''),
    CHECK ((revoked_at IS NULL) = (revoked_by_id IS NULL) AND (revoked_at IS NULL) = (revoked_from_month IS NULL)),
    CHECK (revoked_from_month IS NULL OR EXTRACT(DAY FROM revoked_from_month) = 1)
);

CREATE INDEX fee_adjustments_member_idx ON fee_adjustments (member_id);

CREATE SEQUENCE seq_fee_payments_id INCREMENT BY 1 CACHE 50;

CREATE TABLE fee_payments
(
    id                  BIGINT             NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_fee_payments_id'),
    created_at          timestamptz        NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamptz,
    created_by_user_id  BIGINT REFERENCES members (id),
    updated_by_user_id  BIGINT REFERENCES members (id),

    member_id           BIGINT             NOT NULL REFERENCES members (id),
    amount              NUMERIC(12, 2)     NOT NULL,
    currency            CHAR(3)            NOT NULL             DEFAULT 'PLN',
    paid_on             DATE               NOT NULL,
    source              fee_payment_source NOT NULL,
    bank_transaction_id BIGINT,
    note                TEXT,
    recorded_by_id      BIGINT             NOT NULL REFERENCES members (id),
    reversed_at         timestamptz,
    reversed_by_id      BIGINT REFERENCES members (id),
    reversal_reason     TEXT,

    CHECK (amount > 0),
    CHECK (currency = 'PLN'),
    CHECK (bank_transaction_id IS NULL OR source = 'BANK'),
    CHECK ((reversed_at IS NULL) = (reversed_by_id IS NULL) AND (reversed_at IS NULL) = (reversal_reason IS NULL))
);

CREATE INDEX fee_payments_member_idx ON fee_payments (member_id);
CREATE INDEX fee_payments_bank_transaction_idx ON fee_payments (bank_transaction_id) WHERE bank_transaction_id IS NOT NULL;

CREATE SEQUENCE seq_fee_payment_allocations_id INCREMENT BY 1 CACHE 50;

CREATE TABLE fee_payment_allocations
(
    id                 BIGINT         NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_fee_payment_allocations_id'),
    created_at         timestamptz    NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    payment_id         BIGINT         NOT NULL REFERENCES fee_payments (id),
    due_id             BIGINT         NOT NULL REFERENCES fee_dues (id),
    amount             NUMERIC(12, 2) NOT NULL,

    UNIQUE (payment_id, due_id),
    CHECK (amount > 0)
);

CREATE INDEX fee_payment_allocations_due_idx ON fee_payment_allocations (due_id);

CREATE SEQUENCE seq_fee_sender_ibans_id INCREMENT BY 1 CACHE 50;

CREATE TABLE fee_sender_ibans
(
    id                 BIGINT      NOT NULL PRIMARY KEY DEFAULT NEXTVAL('seq_fee_sender_ibans_id'),
    created_at         timestamptz NOT NULL             DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamptz,
    created_by_user_id BIGINT REFERENCES members (id),
    updated_by_user_id BIGINT REFERENCES members (id),

    iban               TEXT        NOT NULL,
    member_id          BIGINT      NOT NULL REFERENCES members (id),

    UNIQUE (iban),
    CHECK (iban = UPPER(iban) AND iban !~ '\s')
);

CREATE INDEX fee_sender_ibans_member_idx ON fee_sender_ibans (member_id);
