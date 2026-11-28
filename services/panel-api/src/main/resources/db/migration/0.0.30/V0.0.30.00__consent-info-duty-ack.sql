-- G-12 P2: consent kind confirming the information duty (art. 13 RODO) in the e-form.
-- Kept alone in its migration: PostgreSQL forbids using a value added by ALTER TYPE in the same transaction.
ALTER TYPE recruitment_consent_kind ADD VALUE IF NOT EXISTS 'INFO_DUTY_ACKNOWLEDGED';
