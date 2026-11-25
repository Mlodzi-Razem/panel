-- Admission bans (statut jednolity 2026, art. 9 ust. 5) — creation path and statutory bounds.
--
-- The G1 statute-2026 compliance audit (docs/notes/zgodnosc-statut-2026.md, matrix row for
-- art. 9 ust. 5 and §7 item 5) found the ban enforced at the e-declaration surface but with
-- no code path to create one, and the table missing the 365-day ceiling of the ZK uchwała.
-- This migration adds the mandatory reason of the uchwała and the period bound; the creation
-- path itself (AdmissionBanService + the PANEL_BAN_FILE importer) lands with the same change.
-- NOTE: the folder holds a provisional version number — the senior renumbers at merge time.

-- §7 item 5 (b): the uchwała may ban admission "na okres maksymalnie 365 dni".
-- The order of the dates is already enforced since 0.0.6.00 (effective_until > effective_from);
-- the ceiling makes the constraint self-contained: the ban period runs between 0 and 365 days.
ALTER TABLE admission_bans
    ADD CONSTRAINT admission_bans_max_duration_ck
        CHECK (effective_until - effective_from BETWEEN 0 AND 365);

-- §7 item 5 (a): the reason of the uchwała is statutory input and is recorded with the ban
-- (never exposed to the public side — the declaration stays generically blocked).
-- The table is fresh: bans were never written by any service, so a NOT NULL column adds
-- without a default. An environment holding a manually inserted row fails this migration
-- loudly on purpose — that row needs its uchwała reason filled in first (§0.6, no silent fixes).
ALTER TABLE admission_bans
    ADD COLUMN reason TEXT NOT NULL;

ALTER TABLE admission_bans
    ADD CONSTRAINT admission_bans_reason_present_ck
        CHECK (length(btrim(reason)) > 0);
