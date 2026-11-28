-- G-10: model zawieszeń (art. 10 statutu). Numer roboczy 0.0.26; finalny nadaje Landing.
--
-- Tabela suspensions jest rejestrem append-only: wiersz nie zmienia tożsamości (członek, organ, start,
-- koniec, sygnatura, rodzaj, zakres), uchylenie i ustanie zdolności odwoławczej są jednorazowe, a unieważnienie
-- omyłkowego wpisu zostawia wiersz ze statusem VOIDED. Nie ma DELETE.

CREATE TYPE suspension_kind   AS ENUM ('ARREARS', 'VOLUNTARY', 'BY_ORGAN');
CREATE TYPE suspension_scope  AS ENUM ('FULL', 'PASSIVE_ELECTORAL', 'PARTIAL_OTHER');
CREATE TYPE suspension_status AS ENUM ('ACTIVE', 'VOIDED');

-- Wiersze sprzed tej migracji (tylko dev/seed): uznane za uchwałę organu, całość praw.
ALTER TABLE suspensions
    ADD COLUMN kind                    suspension_kind   NOT NULL DEFAULT 'BY_ORGAN',
    ADD COLUMN scope                   suspension_scope  NOT NULL DEFAULT 'FULL',
    ADD COLUMN scope_description       TEXT,
    ADD COLUMN status                  suspension_status NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN void_reason             TEXT,
    ADD COLUMN void_decision_reference TEXT,
    ADD COLUMN voided_at               TIMESTAMPTZ,
    ADD COLUMN voided_by_user_id       BIGINT REFERENCES members (id),
    ADD COLUMN appeal_ended_on         DATE,
    ADD COLUMN appeal_ended_reference  TEXT;

-- Defaults stay: legacy inserts (dev seed, existing tests) omit kind and scope and mean BY_ORGAN / FULL.

-- Data uchwały (decyzja właściciela W4, 2026-11-27): date of the resolution (ARREARS, BY_ORGAN) that the appeal
-- period hint is counted from. VOLUNTARY has no resolution: its effective date is the receipt of the statement,
-- i.e. suspension_start, so decision_date stays NULL there.
ALTER TABLE suspensions ADD COLUMN decision_date DATE;

-- Wiersze sprzed tej migracji: dla ARREARS/BY_ORGAN data uchwały nieznana, przyjmujemy start (jak w CHECK niżej).
UPDATE suspensions
SET decision_date = suspension_start
WHERE kind IN ('ARREARS', 'BY_ORGAN') AND decision_date IS NULL;
ALTER TABLE suspensions
    ADD CONSTRAINT suspensions_voluntary_full_chk CHECK (kind <> 'VOLUNTARY' OR scope = 'FULL'),
    ADD CONSTRAINT suspensions_decision_date_chk CHECK (
        (kind = 'VOLUNTARY' AND decision_date IS NULL)
        OR (kind IN ('ARREARS', 'BY_ORGAN') AND decision_date IS NOT NULL AND decision_date <= suspension_start)),
    ADD CONSTRAINT suspensions_scope_description_chk CHECK (
        (scope = 'PARTIAL_OTHER' AND scope_description IS NOT NULL AND btrim(scope_description) <> ''
            AND char_length(scope_description) <= 500)
        OR (scope <> 'PARTIAL_OTHER' AND scope_description IS NULL)),
    ADD CONSTRAINT suspensions_void_fields_chk CHECK (
        (status = 'VOIDED' AND void_reason IS NOT NULL AND voided_at IS NOT NULL)
        OR (status = 'ACTIVE' AND void_reason IS NULL AND void_decision_reference IS NULL
            AND voided_at IS NULL AND voided_by_user_id IS NULL)),
    ADD CONSTRAINT suspensions_appeal_chk CHECK (
        (appeal_ended_on IS NULL AND appeal_ended_reference IS NULL)
        OR (appeal_ended_on IS NOT NULL AND appeal_ended_on >= suspension_start)),
    ADD CONSTRAINT suspensions_lift_not_after_end_chk CHECK (
        lifted_at IS NULL OR suspension_end IS NULL OR lifted_at <= suspension_end),
    ADD CONSTRAINT suspensions_lengths_chk CHECK (
        (decision_signature IS NULL OR char_length(decision_signature) <= 200)
        AND (lift_decision_signature IS NULL OR char_length(lift_decision_signature) <= 200)
        AND (void_decision_reference IS NULL OR char_length(void_decision_reference) <= 200)
        AND (appeal_ended_reference IS NULL OR char_length(appeal_ended_reference) <= 200)
        AND (lifted_reason IS NULL OR char_length(lifted_reason) <= 500)
        AND (void_reason IS NULL OR char_length(void_reason) <= 500));

-- Wymóg niepustej decision_signature egzekwuje serwis: NOT VALID nie obejmuje wierszy historycznych,
-- a wymuszenie na nich blokowałoby ich późniejsze uchylenie.

CREATE INDEX suspensions_member_open_idx ON suspensions (suspended_user_id)
    WHERE status = 'ACTIVE' AND lifted_at IS NULL;

-- Historia nienadpisywalna: tożsamość wpisu niezmienna; pola uchylenia, ustania odwołania i unieważnienia
-- są jednorazowe.
CREATE FUNCTION suspensions_history_guard() RETURNS trigger AS
$$
BEGIN
    IF NEW.suspended_user_id <> OLD.suspended_user_id
        OR NEW.suspending_body_id <> OLD.suspending_body_id
        OR NEW.suspension_start <> OLD.suspension_start
        OR NEW.suspension_end IS DISTINCT FROM OLD.suspension_end
        OR NEW.decision_signature IS DISTINCT FROM OLD.decision_signature
        OR NEW.kind <> OLD.kind
        OR NEW.scope <> OLD.scope
        OR NEW.scope_description IS DISTINCT FROM OLD.scope_description
        OR NEW.decision_date IS DISTINCT FROM OLD.decision_date
        OR NEW.created_at <> OLD.created_at
        OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id THEN
        RAISE EXCEPTION 'suspensions are append-only: an entry cannot be rewritten' USING ERRCODE = '23514';
    END IF;
    IF OLD.lifted_at IS NOT NULL AND (NEW.lifted_at IS DISTINCT FROM OLD.lifted_at
        OR NEW.lifted_reason IS DISTINCT FROM OLD.lifted_reason
        OR NEW.lifted_body IS DISTINCT FROM OLD.lifted_body
        OR NEW.lift_decision_signature IS DISTINCT FROM OLD.lift_decision_signature) THEN
        RAISE EXCEPTION 'a lifted suspension cannot be changed' USING ERRCODE = '23514';
    END IF;
    IF OLD.appeal_ended_on IS NOT NULL AND (NEW.appeal_ended_on IS DISTINCT FROM OLD.appeal_ended_on
        OR NEW.appeal_ended_reference IS DISTINCT FROM OLD.appeal_ended_reference) THEN
        RAISE EXCEPTION 'the end of the appeal period is recorded once' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'VOIDED' THEN
        RAISE EXCEPTION 'a voided suspension is never touched again' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER suspensions_history_guard_trg BEFORE UPDATE ON suspensions
    FOR EACH ROW EXECUTE FUNCTION suspensions_history_guard();

CREATE FUNCTION suspensions_no_delete() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'suspensions are never deleted' USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER suspensions_no_delete_trg BEFORE DELETE ON suspensions
    FOR EACH ROW EXECUTE FUNCTION suspensions_no_delete();

-- Związek „zawieszenie -> mandaty zakończone na jego podstawie” (append-only). Mandat nadal kończy wyłącznie
-- BodyMembershipService.end z uchwałą; ta tabela służy blokadzie unieważnienia i licznikom.
CREATE SEQUENCE seq_suspension_mandate_endings_id;

CREATE TABLE suspension_mandate_endings
(
    id                        BIGINT      NOT NULL DEFAULT nextval('seq_suspension_mandate_endings_id') PRIMARY KEY,
    suspension_id             BIGINT      NOT NULL REFERENCES suspensions (id),
    body_membership_ending_id BIGINT      NOT NULL UNIQUE REFERENCES body_membership_endings (id),
    created_by_user_id        BIGINT REFERENCES members (id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX suspension_mandate_endings_suspension_idx ON suspension_mandate_endings (suspension_id);

CREATE FUNCTION suspension_mandate_endings_append_only() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'suspension mandate endings are append-only' USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER suspension_mandate_endings_append_only_trg BEFORE UPDATE OR DELETE ON suspension_mandate_endings
    FOR EACH ROW EXECUTE FUNCTION suspension_mandate_endings_append_only();
