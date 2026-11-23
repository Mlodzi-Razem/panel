/*
 * Copyright (C)  2025 Stowarzyszenie Młodzi Razem
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

package org.mlodzirazem.panel.services.panelapi.recruitment.eform;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;

import java.time.ZonedDateTime;

/**
 * The electronic declaration flow (M4a): a magic-link form the candidate fills in without an
 * account, a server-rendered PDF (versioned template), and a PAdES-qualified signature validated
 * server-side, which lands in the statutory workflow through
 * {@code RecruitmentWorkflowService.recordDeclaration} (the clock starts there). The paper-scan
 * fallback stores a SCAN artifact and records the declaration the same way.
 */
public interface RecruitmentEformService {
    /**
     * The generic refusal of a declaration (an active admission ban, an existing member or a
     * duplicate open case behind the same identifier): one message, never the reason — the reason
     * stays in the server log for the okręg board to see (GDPR-safe, case-level aggregates).
     */
    static RuleViolationException declarationBlocked() {
        return new RuleViolationException("DECLARATION_BLOCKED", "Deklaracja nie może być przyjęta");
    }

    /** Creates a live magic link for a case in AWAITING_DECLARATION (RECRUITMENT_MANAGE, okręg-scope). */
    DeclarationLink createDeclarationLink(long caseId, long actorMemberId);

    /** Anonymous landing information for the token holder. */
    DeclarationForm getForm(String token);

    /** Fields of the e-form; formal names and the personal identifier are required. */
    record DeclarationFormSubmission(
        @Nullable String formalFirstName,
        @Nullable String formalLastName,
        @Nullable String preferredFirstName,
        @Nullable String preferredLastName,
        @Nullable String pronouns,
        @Nullable String personalId,
        @Nullable String phone,
        @Nullable String email,
        boolean imageConsent,
        boolean specialCategoryConsent
    ) {
    }

    /** Validates, fills the case columns, records the consents and renders the template PDF. */
    DeclarationForm submitForm(String token, DeclarationFormSubmission submission);

    /** The stored template artifact rendered at submit time (stays stable for signing). */
    byte[] declarationPdf(String token);

    /** PAdES upload: validates the signature, stores the artifact and records the declaration. */
    DeclarationForm attachSignedDeclaration(String token, byte[] pdfBytes);

    /**
     * Paper fallback for the back office: stores the scan and records the declaration. A case that has
     * no formal data yet takes it from {@code data} (validated exactly like the e-form; the consents are
     * recorded as given on paper); a case that already has it (the candidate filled the e-form) refuses
     * data with 409 {@code FORMAL_DATA_PRESENT} — changes go through {@link #correctCandidateData}.
     * Live e-form sessions of the case are expired.
     */
    DeclarationForm attachScan(long caseId, long actorMemberId, byte[] pdfBytes,
                               @Nullable DeclarationFormSubmission data);

    /**
     * Reasoned correction of the candidate's formal data (RECRUITMENT_MANAGE in the case district or a
     * global RECRUITMENT_APPROVE). Only non-null fields change; the event of the case history carries the
     * changed column names and the reason, never the values.
     */
    void correctCandidateData(long caseId, long actorMemberId, CandidateDataCorrection correction);

    /**
     * @param formalFirstName    with the last name: replaces the formal names (never cleared)
     * @param preferredFirstName with the last name: replaces the preferred names, blank for both clears them
     * @param pronouns           blank clears
     * @param personalId         re-validated like on the declaration
     * @param reason             required, 1-500 characters
     */
    record CandidateDataCorrection(
        @Nullable String formalFirstName,
        @Nullable String formalLastName,
        @Nullable String preferredFirstName,
        @Nullable String preferredLastName,
        @Nullable String pronouns,
        @Nullable String personalId,
        @Nullable String reason
    ) {
    }

    /**
     * @param emailDispatch a {@code MailDispatch} name for the e-mail carrying the link, or {@code NO_EMAIL}
     *                      when the case has no candidate address
     */
    record DeclarationLink(String token, String formUrl, ZonedDateTime expiresAt, String emailDispatch) {
        /** The dispatch value for a case without a candidate address. */
        public static final String NO_EMAIL = "NO_EMAIL";
    }

    record DeclarationForm(String status, @Nullable ZonedDateTime expiresAt, @Nullable String greeting,
                           @Nullable String pdfUrl) {
    }
}
