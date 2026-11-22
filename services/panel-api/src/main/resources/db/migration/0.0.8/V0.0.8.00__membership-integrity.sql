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

-- 0.0.8: statutory integrity of body memberships + history immutability.
--
-- 1) body_memberships.decision_reference — the reference of the resolution
--    (uchwała) or other document that JUSTIFIES a retroactive correction of a
--    closed mandate. Mandatory at the service layer whenever valid_to is set
--    to a past date (history must never change without a recorded reason).
--    The reference is kept by name only — no document contents here.
--
-- 2) The statutory incompatibility group as DATA (idempotent re-seed):
--    Statut Stowarzyszenia Młodzi Razem (3.10.2026), art. 18 ust. 1 pkt 6 —
--    holding more than one position within the group {ZK, KSK, KKR, KKW, ZO}
--    is not allowed; taking a second position ends the earlier one.
--    Template mapping: ZK = Zarząd Krajowy (NATIONAL_BOARD), KSK = Krajowy Sąd
--    Koleżeński (PEER_COURT), KKR = Krajowa Komisja Rewizyjna (AUDIT_COMMISSION),
--    KKW = Krajowa Komisja Wyborcza (ELECTORAL_COMMISSION), ZO = Zarząd Okręgu
--    (DISTRICT_BOARD). All 10 pairs are re-asserted here; the insert is a no-op
--    on databases that already ran 0.0.3 (pairs were seeded there), and it
--    cannot clobber the single-member exemption rows (ON CONFLICT DO NOTHING,
--    keyed on the template pair).

ALTER TABLE body_memberships
    ADD COLUMN decision_reference TEXT;

INSERT INTO body_template_incompatibilities (body_template_id, other_body_template_id,
                                              single_member_exempt_template_id)
SELECT DISTINCT LEAST(a.id, b.id), GREATEST(a.id, b.id), NULL::BIGINT
FROM body_templates a
         JOIN body_templates b ON a.kind <> b.kind
WHERE a.kind IN ('NATIONAL_BOARD', 'PEER_COURT', 'AUDIT_COMMISSION',
                 'ELECTORAL_COMMISSION', 'DISTRICT_BOARD')
  AND b.kind IN ('NATIONAL_BOARD', 'PEER_COURT', 'AUDIT_COMMISSION',
                 'ELECTORAL_COMMISSION', 'DISTRICT_BOARD')
ON CONFLICT DO NOTHING;
