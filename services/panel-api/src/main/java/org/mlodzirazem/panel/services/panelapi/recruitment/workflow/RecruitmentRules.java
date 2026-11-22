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

import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * Statutory rules of the recruitment workflow (statute jednolity 3.10.2026) on top of the
 * schema clocks of V0.0.6.00. Single place for every constant the workflow engine validates against.
 */
public final class RecruitmentRules {
    /**
     * Okres wstępny is assessed by ZO and ZK no earlier than 62 days after the declaration
     * (statut jednolity 2026, art. 9 ust. 4 pkt 4).
     */
    public static final int STANDARD_ASSESSMENT_NOT_BEFORE_DAYS = 62;

    /**
     * Statut jednolity 2026, art. 9 ust. 4 pkt 4 — and the outer bound for every track: the shortcut
     * tracks (art. 9 ust. 4 pkt 9) shorten the minimum waiting time only.
     */
    public static final int ASSESSMENT_NOT_LATER_DAYS = 76;

    /**
     * Earlier assessment for candidates from affiliated organisations, after at least 14 days
     * (statut jednolity 2026, art. 9 ust. 4 pkt 9). Anchored on the declaration date — the statute
     * says „od wpłynięcia wniosku”, which the product owner still needs to confirm as anchor decision.
     */
    public static final int AFFILIATED_ASSESSMENT_NOT_BEFORE_DAYS = 14;

    /**
     * Earlier assessment for particularly engaged candidates, after at least 31 days
     * (statut jednolity 2026, art. 9 ust. 4 pkt 9). Same anchor caveat as {@link #AFFILIATED_ASSESSMENT_NOT_BEFORE_DAYS}.
     */
    public static final int ENGAGED_ASSESSMENT_NOT_BEFORE_DAYS = 31;

    /**
     * One-time extension of the initial period: the second assessment happens within 31 days of the
     * extension decision (statut jednolity 2026, art. 9 ust. 4 pkt 7).
     */
    public static final int SECOND_ASSESSMENT_NOT_LATER_DAYS = 31;

    /**
     * Admission resolution of ZO within 7 days of the positive assessments (statut jednolity 2026,
     * art. 9 ust. 4 pkt 8).
     */
    public static final int ADMISSION_RESOLUTION_NOT_LATER_DAYS = 7;

    /**
     * A missing ZK opinion within its deadline counts as a positive opinion (statut jednolity 2026,
     * art. 9 ust. 4 pkt 6). The note is always written onto tacit ZK approval decisions; their signature
     * is the protocol signature of the ZK meeting which states the silence.
     */
    public static final String TACIT_ZK_APPROVAL_NOTE = "termin minął bez opinii — art. 9 ust. 4 pkt 6";

    /**
     * Post-declaration states of the M3 workflow string. The pre-declaration cluster
     * (NEW, IN_PROGRESS, NO_CONTACT, AWAITING_DECLARATION) belongs to the M2 intake module.
     */
    public static final Set<RecruitmentCaseStatus> POST_DECLARATION_STATES = Set.of(
        RecruitmentCaseStatus.DECLARATION_RECEIVED, RecruitmentCaseStatus.PROBATION,
        RecruitmentCaseStatus.PROBATION_EXTENDED, RecruitmentCaseStatus.ZO_RECOMMENDED,
        RecruitmentCaseStatus.ZK_VOTING, RecruitmentCaseStatus.ACCEPTED, RecruitmentCaseStatus.REJECTED
    );

    /**
     * The full state machine as it is reconstructed from the as-is diagram [pas 2 of the flowchart] and
     * closes with statute art. 9 ust. 4 pkt 5–8. M2 own the edges out of NEW..AWAITING_DECLARATION; the M3
     * workflow engine implements everything from AWAITING_DECLARATION → DECLARATION_RECEIVED on.
     */
    private static final Map<RecruitmentCaseStatus, Set<RecruitmentCaseStatus>> TRANSITIONS = Map.ofEntries(
        entry(RecruitmentCaseStatus.NEW, Set.of(
            RecruitmentCaseStatus.IN_PROGRESS, RecruitmentCaseStatus.NO_CONTACT, RecruitmentCaseStatus.REJECTED)),
        entry(RecruitmentCaseStatus.IN_PROGRESS, Set.of(
            RecruitmentCaseStatus.AWAITING_DECLARATION, RecruitmentCaseStatus.REJECTED,
            RecruitmentCaseStatus.NO_CONTACT)),
        entry(RecruitmentCaseStatus.NO_CONTACT, Set.of(
            RecruitmentCaseStatus.IN_PROGRESS, RecruitmentCaseStatus.REJECTED)),
        entry(RecruitmentCaseStatus.AWAITING_DECLARATION, Set.of(
            RecruitmentCaseStatus.DECLARATION_RECEIVED, RecruitmentCaseStatus.REJECTED)),
        entry(RecruitmentCaseStatus.DECLARATION_RECEIVED, Set.of(RecruitmentCaseStatus.PROBATION)),
        entry(RecruitmentCaseStatus.PROBATION, Set.of(
            RecruitmentCaseStatus.PROBATION_EXTENDED, RecruitmentCaseStatus.ZO_RECOMMENDED,
            RecruitmentCaseStatus.REJECTED)),
        entry(RecruitmentCaseStatus.PROBATION_EXTENDED, Set.of(
            RecruitmentCaseStatus.ZO_RECOMMENDED, RecruitmentCaseStatus.REJECTED)),
        entry(RecruitmentCaseStatus.ZO_RECOMMENDED, Set.of(RecruitmentCaseStatus.ZK_VOTING)),
        entry(RecruitmentCaseStatus.ZK_VOTING, Set.of(
            RecruitmentCaseStatus.ACCEPTED, RecruitmentCaseStatus.REJECTED)),
        entry(RecruitmentCaseStatus.ACCEPTED, Set.of()),
        entry(RecruitmentCaseStatus.REJECTED, Set.of())
    );

    /**
     * Reasons per source status. The pre-declaration entries (NEW, IN_PROGRESS, NO_CONTACT,
     * AWAITING_DECLARATION) are the M2 intake territory; the engine validates only the post-decision part.
     */
    private static final Map<RecruitmentCaseStatus, Set<RecruitmentRejectionReason>> REJECTION_REASONS = Map.ofEntries(
        entry(RecruitmentCaseStatus.NEW, Set.of(RecruitmentRejectionReason.FALSE_ENTRY)),
        entry(RecruitmentCaseStatus.IN_PROGRESS, Set.of(RecruitmentRejectionReason.NOT_FIT)),
        entry(RecruitmentCaseStatus.NO_CONTACT, Set.of(RecruitmentRejectionReason.NO_CONTACT)),
        entry(RecruitmentCaseStatus.AWAITING_DECLARATION, Set.of(RecruitmentRejectionReason.NO_DECLARATION)),
        entry(RecruitmentCaseStatus.DECLARATION_RECEIVED, Set.of()),
        entry(RecruitmentCaseStatus.PROBATION,
            Set.of(RecruitmentRejectionReason.NOT_FIT, RecruitmentRejectionReason.ZK_BAN)),
        entry(RecruitmentCaseStatus.PROBATION_EXTENDED,
            Set.of(RecruitmentRejectionReason.NOT_FIT, RecruitmentRejectionReason.ZK_BAN)),
        entry(RecruitmentCaseStatus.ZO_RECOMMENDED, Set.of()),
        entry(RecruitmentCaseStatus.ZK_VOTING,
            Set.of(RecruitmentRejectionReason.VOTE_FAILED, RecruitmentRejectionReason.ZK_BAN)),
        entry(RecruitmentCaseStatus.ACCEPTED, Set.of()),
        entry(RecruitmentCaseStatus.REJECTED, Set.of())
    );

    private RecruitmentRules() {
    }

    public static boolean canTransition(RecruitmentCaseStatus from, RecruitmentCaseStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<RecruitmentCaseStatus> allowedTargets(RecruitmentCaseStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    public static Set<RecruitmentRejectionReason> allowedRejectionReasons(RecruitmentCaseStatus from) {
        return REJECTION_REASONS.getOrDefault(from, Set.of());
    }

    /** Earliest assessment date for the track; the "okres wstępny" clock anchors on the declaration date. */
    public static LocalDate assessmentNotBeforeOn(LocalDate declarationReceivedOn, RecruitmentAssessmentTrack track) {
        int days = switch (track) {
            case STANDARD -> STANDARD_ASSESSMENT_NOT_BEFORE_DAYS;
            case AFFILIATED -> AFFILIATED_ASSESSMENT_NOT_BEFORE_DAYS;
            case ENGAGED -> ENGAGED_ASSESSMENT_NOT_BEFORE_DAYS;
        };

        return declarationReceivedOn.plusDays(days);
    }

    /** Outer bound of the assessment window — identical for every track (art. 9 ust. 4 pkt 4). */
    public static LocalDate assessmentNotLaterOn(LocalDate declarationReceivedOn) {
        return declarationReceivedOn.plusDays(ASSESSMENT_NOT_LATER_DAYS);
    }

    /** Deadline of the second assessment after the one-time extension (art. 9 ust. 4 pkt 7). */
    public static LocalDate secondAssessmentNotLaterOn(LocalDate extensionDecidedOn) {
        return extensionDecidedOn.plusDays(SECOND_ASSESSMENT_NOT_LATER_DAYS);
    }

    /** Deadline for the ZO admission resolution after the positive assessments (art. 9 ust. 4 pkt 8). */
    public static LocalDate admissionResolutionNotLaterOn(LocalDate zoRecommendedOn) {
        return zoRecommendedOn.plusDays(ADMISSION_RESOLUTION_NOT_LATER_DAYS);
    }

    /**
     * The assessment must not happen before the statutory minimum: approving earlier takes the
     * candidate's full assessment window away (art. 9 ust. 4 pkt 4 i 9). Late assessment by ZO/ZK is
     * the association's own governance breach and is NOT blocked by the engine — the case would have
     * no exit otherwise; see the M3 report.
     */
    /** A collegial decision is recorded on or after the day it was taken, never ahead of the calendar. */
    public static void assertDecisionNotInFuture(LocalDate decidedOn, LocalDate today) {
        if (decidedOn.isAfter(today)) {
            throw new RuleViolationException("DECISION_DATE_IN_FUTURE",
                "The decision date cannot be in the future");
        }
    }

    public static void assertAssessmentNotTooEarly(LocalDate decidedOn, LocalDate assessmentNotBeforeOn) {
        if (decidedOn.isBefore(assessmentNotBeforeOn)) {
            throw new RuleViolationException("ASSESSMENT_TOO_EARLY",
                "The assessment cannot happen before %s (statute art. 9 ust. 4 pkt 4 or 9)".formatted(
                    assessmentNotBeforeOn));
        }
    }
}
