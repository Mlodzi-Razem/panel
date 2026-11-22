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

package org.mlodzirazem.panel.services.panelapi.recruitment.workflow;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;

import java.time.LocalDate;

/**
 * The statutory post-declaration string of a recruitment case (statut jednolity 2026, art. 9 ust. 4):
 * declaration → okres wstępny (PROBATION) → one-time extension → ZO recommendation → ZK voting →
 * ACCEPTED (uchwała ZO + prior ZK approval) or REJECTED. The pre-declaration intake cluster
 * (NEW, IN_PROGRESS, NO_CONTACT, AWAITING_DECLARATION and its rejections) is the M2 module and is not
 * served here.
 *
 * <p>Every mutation records a {@code recruitment_decisions} row (with signature and deciding body),
 * appends {@code recruitment_case_events} (payloads carry field names, never values) and writes the
 * module audit trail ({@code audited_operations}). Operations are audited with
 * {@code recruitment :: <operation>} identifiers.
 */
public interface RecruitmentWorkflowService {
    /**
     * AWAITING_DECLARATION → DECLARATION_RECEIVED. Records the membership declaration (form, track,
     * received date) and starts the statutory assessment clock of the okres wstępny
     * (art. 9 ust. 4 pkt 4, 9): {@code assessment_not_before_on} and {@code assessment_not_later_on}.
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException code {@code DECLARATION_DATE_INVALID} for a future receiving date
     */
    TRecruitmentCase recordDeclaration(
        long caseId,
        long actorMemberId,
        DeclarationForm form,
        RecruitmentAssessmentTrack track,
        LocalDate receivedOn
    );

    /**
     * DECLARATION_RECEIVED → PROBATION: the okres wstępny starts and ZK is informed of it
     * (art. 9 ust. 4 pkt 3) — the {@code ZK_NOTIFIED_PROBATION_START} event marks that statutory
     * notification point for the notifier module (M2/M3-API wire the channel).
     */
    TRecruitmentCase startProbation(long caseId, long actorMemberId);

    /**
     * PROBATION → PROBATION_EXTENDED: the one-time extension of the okres wstępny after a negative
     * assessment of ZO or ZK (art. 9 ust. 4 pkt 5–6); the second assessment must happen within
     * {@link RecruitmentRules#SECOND_ASSESSMENT_NOT_LATER_DAYS} days of the decision
     * (art. 9 ust. 4 pkt 7).
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.ConflictException code {@code EXTENSION_ALREADY_USED} when the case is already extended
     */
    TRecruitmentCase extendAssessment(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    );

    /**
     * PROBATION / PROBATION_EXTENDED → ZO_RECOMMENDED: positive assessment of ZO recorded with its
     * resolution; sets {@code zo_recommended_on} and the admission resolution deadline
     * (art. 9 ust. 4 pkt 8). A late assessment is not blocked (governance breach of the association),
     * an early one is (art. 9 ust. 4 pkt 4 i 9).
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException code {@code ASSESSMENT_TOO_EARLY} before the statutory minimum
     */
    TRecruitmentCase recordZoRecommendation(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    );

    /** ZO_RECOMMENDED → ZK_VOTING: ZK opens the voting over the admission (as-is diagram [pas 2]). */
    TRecruitmentCase startZkVoting(long caseId, long actorMemberId);

    /**
     * In ZK_VOTING: records the explicit ZK approval (art. 9 ust. 4 pkt 8). Only one ZK_APPROVAL
     * per case ({@code recruitment_decisions} unique key).
     */
    TRecruitmentCase recordZkApproval(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    );

    /**
     * In ZK_VOTING: records the tacit ZK approval — a missing ZK opinion within its deadline counts
     * as positive (art. 9 ust. 4 pkt 6). The note always carries {@link RecruitmentRules#TACIT_ZK_APPROVAL_NOTE};
     * the signature is the protocol signature of the ZK meeting which states the silence
     * ({@code decided_on} = today of the call).
     */
    TRecruitmentCase recordTacitZkApproval(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        String protocolSignature,
        @Nullable String note
    );

    /**
     * In ZK_VOTING: records the ZO admission resolution — nadanie członkostwa uchwałą ZO
     * (art. 9 ust. 2 pkt 2, ust. 4 pkt 8). Requires the prior ZK approval (explicit or tacit);
     * a resolution recorded later than {@code admission_resolution_not_later_on} is not blocked
     * (governance breach of the association, no candidate fault).
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.ConflictException code {@code ZK_APPROVAL_REQUIRED} without a prior ZK approval
     */
    TRecruitmentCase recordAdmissionResolution(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        String signature,
        @Nullable String note
    );

    /**
     * ZK_VOTING → ACCEPTED: closes the case as PRZYJĘTA. Requires both the ZK approval and the ZO
     * admission resolution (the DB trigger of V0.0.6.00 backs this at the schema level); decisions are
     * recorded first, the status flip last. The member row is linked here with its id — callers go through
     * {@link org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentAdmissionService}, which
     * allocates the member number and INSERTs into {@code members} in the same transaction.
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.ConflictException code {@code BOTH_DECISIONS_REQUIRED} when a decision is still missing
     */
    TRecruitmentCase accept(long caseId, long actorMemberId, LocalDate acceptedOn, long acceptedMemberId);

    /**
     * PROBATION / PROBATION_EXTENDED / ZK_VOTING → REJECTED: closes the case with a rejection decision
     * (uchwała o odrzuceniu wniosku, art. 9 ust. 4 pkt 5) or an admission ban case (art. 9 ust. 5).
     * Reason codes allowed per source status live in {@link RecruitmentRules#allowedRejectionReasons}.
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException code {@code REJECTION_REASON_NOT_ALLOWED} for a reason the
     *         source status does not permit
     */
    TRecruitmentCase reject(
        long caseId,
        long actorMemberId,
        long decidingBodyId,
        LocalDate decidedOn,
        RecruitmentRejectionReason reason,
        String signature,
        @Nullable String note
    );
}
