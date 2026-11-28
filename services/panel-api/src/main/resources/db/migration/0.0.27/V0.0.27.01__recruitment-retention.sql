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

-- Retention of rejected recruitment cases (M8, owner decision G-5, statute is silent on rejected candidates):
--   * declaration artifacts (template PDF, signed PDF, scan) are purged ARTIFACT days after the case closed,
--   * the candidate's personal data is wiped and the case is marked anonymized RETENTION days after it closed.
-- Case events and decisions stay (append-only history); only free text that could carry personal data is
-- redacted by the job. Admission bans (art. 9 ust. 5) have their own lifecycle and are not touched.

ALTER TABLE recruitment_cases
    ADD COLUMN artifacts_purged_at timestamptz;

ALTER TABLE declaration_artifacts
    ADD COLUMN purged_at timestamptz;

-- a purged artifact keeps its trace (kind, hashes, signature type, validation check names) but no longer
-- points at a stored file and no longer carries the signer's name
ALTER TABLE declaration_artifacts
    ALTER COLUMN storage_ref DROP NOT NULL;

DO
$$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'declaration_artifacts'::regclass
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) LIKE '%signer_name%'
        LOOP
            EXECUTE format('ALTER TABLE declaration_artifacts DROP CONSTRAINT %I', constraint_name);
        END LOOP;
END;
$$;

ALTER TABLE declaration_artifacts
    ADD CONSTRAINT declaration_artifacts_signature_metadata_ck
        CHECK (artifact_kind = 'TEMPLATE_PDF' OR purged_at IS NOT NULL
            OR (signature_type IS NOT NULL AND signer_name IS NOT NULL AND validated_at IS NOT NULL)),
    ADD CONSTRAINT declaration_artifacts_purged_ck
        CHECK ((purged_at IS NULL) = (storage_ref IS NOT NULL)),
    ADD CONSTRAINT declaration_artifacts_purged_signer_ck
        CHECK (purged_at IS NULL OR signer_name IS NULL);

-- schema-level backstops of the anonymization
-- Order "artifacts purged before anonymization" is enforced by the retention job only, not by a CHECK
-- (owner decision 2026-11-27): older code paths anonymize a case without purging its artifacts.
ALTER TABLE recruitment_cases
    ADD CONSTRAINT recruitment_cases_anonymized_rejected_ck
        CHECK (anonymized_at IS NULL OR status = 'REJECTED'),
    ADD CONSTRAINT recruitment_cases_artifacts_purged_rejected_ck
        CHECK (artifacts_purged_at IS NULL OR status = 'REJECTED'),
    ADD CONSTRAINT recruitment_cases_anonymized_names_ck
        CHECK (anonymized_at IS NULL OR (candidate_first_name IS NULL AND candidate_last_name IS NULL
            AND candidate_formal_first_name IS NULL AND candidate_formal_last_name IS NULL
            AND candidate_preferred_first_name IS NULL AND candidate_preferred_last_name IS NULL
            AND candidate_pronouns IS NULL));

-- the daily job walks rejected cases that are not anonymized yet
CREATE INDEX recruitment_cases_retention_idx
    ON recruitment_cases (closed_at)
    WHERE status = 'REJECTED' AND anonymized_at IS NULL;
