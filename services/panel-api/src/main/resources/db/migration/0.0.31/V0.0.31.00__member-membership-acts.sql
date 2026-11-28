/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

-- G-12 P1: membership acts register (art. 9 ust. 1 pkt 1, art. 11 ust. 2, art. 12 ust. 3 statutu).
-- The number in the file name is a working one (0.0.26-0.0.28 belong to other packages); Landing assigns the final one.
--
-- Every change of status, membership type or removal date made by a membership act is a new row here. Rows are
-- append-only: no UPDATE, no DELETE. Rows written before this migration are not backfilled; the UI shows them as
-- "no basis recorded" (legacy).

CREATE TYPE membership_act_kind AS ENUM
    ('SUPPORTING_GRANT', 'HONORARY_GRANT', 'REMOVAL', 'REINSTATEMENT', 'MEMBERSHIP_TYPE_CHANGE');

CREATE TYPE membership_removal_ground AS ENUM (
    'DEATH', 'INCAPACITATION', 'LOSS_OF_PUBLIC_RIGHTS', 'RESIGNATION', 'AGE_26', 'EXCLUSION', 'HONORARY_REVOKED');

CREATE SEQUENCE seq_member_membership_acts_id;

CREATE TABLE member_membership_acts
(
    id                        BIGINT                  NOT NULL DEFAULT nextval('seq_member_membership_acts_id') PRIMARY KEY,
    member_id                 BIGINT                  NOT NULL REFERENCES members (id),
    act_kind                  membership_act_kind     NOT NULL,
    removal_ground            membership_removal_ground,
    previous_status           member_status           NOT NULL,
    -- NULL only for a grant that created a new person (there was no membership type before it)
    previous_membership_type  membership_type,
    -- type after a MEMBERSHIP_TYPE_CHANGE act (NULL for every other kind)
    membership_type_after     membership_type,
    effective_on              DATE                    NOT NULL,
    decision_reference        TEXT                    NOT NULL,
    reason                    TEXT,
    created_by_user_id        BIGINT REFERENCES members (id),
    created_at                TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_by_user_id        BIGINT REFERENCES members (id),
    updated_at                TIMESTAMPTZ,
    CONSTRAINT member_membership_acts_reference_chk CHECK (
        btrim(decision_reference) <> '' AND char_length(decision_reference) <= 200),
    CONSTRAINT member_membership_acts_ground_chk CHECK (
        (act_kind = 'REMOVAL') = (removal_ground IS NOT NULL)),
    CONSTRAINT member_membership_acts_reason_chk CHECK (
        (act_kind IN ('REINSTATEMENT', 'MEMBERSHIP_TYPE_CHANGE'))
            = (reason IS NOT NULL AND btrim(reason) <> '' AND char_length(reason) <= 500)),
    CONSTRAINT member_membership_acts_type_after_chk CHECK (
        (act_kind = 'MEMBERSHIP_TYPE_CHANGE')
            = (membership_type_after IS NOT NULL AND membership_type_after <> previous_membership_type)
        AND (act_kind <> 'MEMBERSHIP_TYPE_CHANGE' OR membership_type_after <> 'HONORARY'
            AND previous_membership_type <> 'HONORARY'))
);

CREATE INDEX member_membership_acts_member_idx ON member_membership_acts (member_id, id DESC);

CREATE FUNCTION member_membership_acts_append_only() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'membership acts are append-only' USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER member_membership_acts_append_only_trg BEFORE UPDATE OR DELETE ON member_membership_acts
    FOR EACH ROW EXECUTE FUNCTION member_membership_acts_append_only();
