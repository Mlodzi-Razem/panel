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

package org.mlodzirazem.panel.services.panelapi.recruitment;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBan;

/**
 * The admission-ban rules of statut jednolity 2026, art. 9 ust. 5: the ZK uchwała may ban a person
 * from being admitted for at most 365 days. The gap closed by this service (G1 audit §7 item 5):
 * the Panel enforced the ban at the e-declaration surface but offered no code path to record one.
 * History is never rewritten here — a conflicting or repeated ban is refused explicitly, an
 * existing one is never updated, closed or shortened.
 */
public interface AdmissionBanService {
    /**
     * Evaluates every statutory rule of the uchwała without writing anything: the PESEL (the
     * e-declaration checksum rules), the mandatory reason and signature, the period bounds
     * (at most 365 days, never running into more than a year from the recording date) and the
     * overlap with a recorded ban of the same person. Blank components are reported as rule
     * violations, never thrown here.
     */
    AdmissionBanCheck check(NewAdmissionBan ban);

    /**
     * Records the uchwała. A rule violation throws the code of the offending rule; an overlapping
     * recorded ban is a {@code BAN_OVERLAPS_EXISTING} conflict. Nothing is ever updated in place:
     * a repeated identical uchwała, a differing signature or reason on the same period — all of
     * it is refused here and classified by the caller (the importer reports identical re-runs as
     * {@code Unchanged}, differences as explicit row errors).
     *
     * <p>The audit trail carries the field names only — never the values (§0.5): the identifier
     * and the reason of the ban are personal data of art. 9 GDPR and stay inside the database row.
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException with the code of the first violated rule
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.ConflictException BAN_OVERLAPS_EXISTING when a recorded ban of the
     *     same person overlaps the requested period; BAN_CASE_IDENTIFIER_MISMATCH when the anchor case belongs to
     *     another person; CASE_NOT_FOUND when the anchor case does not exist
     */
    TAdmissionBan create(NewAdmissionBan ban, @Nullable Long actorMemberId);
}
