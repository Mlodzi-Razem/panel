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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;

import java.util.List;
import java.util.Map;

/**
 * Intake of recruitment cases (M2): everything a district board does with a candidate
 * from the first contact to the membership declaration. The declaration clock, the assessment,
 * the ZO/ZK decisions and the post-declaration statuses are milestone M3 and are not touched here.
 *
 * The service assumes the caller passed the permission check in the controller. Writes are audited
 * into {@code audited_operations} with field names only; the case timeline lives in
 * {@code recruitment_case_events}.
 */
public interface RecruitmentCaseService {
    /**
     * Opens a case in the given district at {@code NEW}. Requires the candidate first and last name;
     * validates the candidate age against ordinary membership when the birth date is known.
     * Rejects a second open case for the same e-mail in the district with {@code OPEN_CASE_EXISTS}.
     */
    TRecruitmentCase create(NewRecruitmentCase newCase, long openedByMemberId);

    /**
     * Edits candidate data of an open case; blank strings clear optional fields, absent fields stay.
     * Returns the names of the fields that really changed.
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.ConflictException CASE_NOT_OPEN for closed cases,
     *     OPEN_CASE_EXISTS when the new e-mail collides with another open case in the district
     */
    RecruitmentCaseUpdate update(long caseId, Map<RecruitmentCandidateField, @Nullable Object> changes,
                                 long actorMemberId);

    /**
     * Moves the case within the pre-declaration cluster: NEW→IN_PROGRESS, IN_PROGRESS⇄NO_CONTACT,
     * IN_PROGRESS→AWAITING_DECLARATION and an early REJECTED (with a reason). Closing a case sets
     * {@code closed_at} and {@code rejection_reason}. Any other edge, including the post-declaration
     * cluster, fails with {@code TRANSITION_NOT_ALLOWED}.
     */
    TRecruitmentCase transition(long caseId, RecruitmentCaseStatus target,
                                @Nullable RecruitmentRejectionReason reason, long actorMemberId);

    /**
     * Records a NOTE_ADDED timeline event. The note text is the recorded datum of the event; it is
     * capped at 2000 characters. Not allowed on anonymised cases.
     */
    TRecruitmentCaseEvent addNote(long caseId, String note, long actorMemberId);

    /**
     * Records a REMINDER_SENT timeline event. M2 only writes the marker; no e-mail is sent.
     * Allowed for open cases only.
     */
    TRecruitmentCaseEvent sendReminder(long caseId, long actorMemberId);

    /**
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException if there is no such case
     */
    TRecruitmentCase get(long caseId);

    /**
     * The case timeline, oldest first: create, status changes, notes, reminders.
     */
    List<TRecruitmentCaseEvent> timeline(long caseId);

    /**
     * The statutory targets of the current status, each with the verdict the transition endpoint
     * would give right now: allowed through the whole state machine, blocked with the matching
     * error code for closed cases and the declaration arrival (artifact-gated, M4a); the ACCEPTED
     * flip (the member admission, M6a) is enabled once both collegial decisions are recorded and
     * blocked with {@code BOTH_DECISIONS_REQUIRED} before. The
     * single source is {@link RecruitmentRules}, so the front end never keeps a local copy of the
     * state map.
     *
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException if there is no such case
     */
    List<RecruitmentCaseTransitionOption> availableTransitions(long caseId);
}
