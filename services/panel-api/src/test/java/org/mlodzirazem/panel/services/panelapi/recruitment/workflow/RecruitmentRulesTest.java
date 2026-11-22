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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecruitmentRulesTest {
    private static final LocalDate DECLARATION = LocalDate.of(2026, 9, 1);

    @Test
    void assessmentWindowStandardFollowsTheStatute() {
        assertThat(RecruitmentRules.assessmentNotBeforeOn(DECLARATION, RecruitmentAssessmentTrack.STANDARD))
            .isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(RecruitmentRules.assessmentNotLaterOn(DECLARATION)).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void assessmentWindowAffiliatedShortensOnlyTheMinimum() {
        assertThat(RecruitmentRules.assessmentNotBeforeOn(DECLARATION, RecruitmentAssessmentTrack.AFFILIATED))
            .isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(RecruitmentRules.assessmentNotLaterOn(DECLARATION)).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void assessmentWindowEngagedShortensOnlyTheMinimum() {
        assertThat(RecruitmentRules.assessmentNotBeforeOn(DECLARATION, RecruitmentAssessmentTrack.ENGAGED))
            .isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(RecruitmentRules.assessmentNotLaterOn(DECLARATION)).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void secondAssessmentIsCappedAtThirtyOneDaysAfterTheExtension() {
        LocalDate extensionDecidedOn = LocalDate.of(2026, 11, 10);

        assertThat(RecruitmentRules.secondAssessmentNotLaterOn(extensionDecidedOn))
            .isEqualTo(LocalDate.of(2026, 12, 11));
    }

    @Test
    void admissionResolutionDeadlineIsSevenDaysAfterTheRecommendation() {
        LocalDate recommendedOn = LocalDate.of(2026, 11, 20);

        assertThat(RecruitmentRules.admissionResolutionNotLaterOn(recommendedOn))
            .isEqualTo(LocalDate.of(2026, 11, 27));
    }

    @Test
    void assessmentBeforeTheStatutoryMinimumIsRejected() {
        assertThatThrownBy(() -> RecruitmentRules.assertAssessmentNotTooEarly(LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 2)))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code")
            .isEqualTo("ASSESSMENT_TOO_EARLY");

        RecruitmentRules.assertAssessmentNotTooEarly(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));
    }

    @Test
    void allowedEdgesCoverTheWholeDiagramAndTheStatutoryExtension() {
        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.NEW))
            .containsExactlyInAnyOrder(RecruitmentCaseStatus.IN_PROGRESS,
                RecruitmentCaseStatus.NO_CONTACT, RecruitmentCaseStatus.REJECTED);

        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.AWAITING_DECLARATION))
            .containsExactlyInAnyOrder(RecruitmentCaseStatus.DECLARATION_RECEIVED,
                RecruitmentCaseStatus.REJECTED);

        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.DECLARATION_RECEIVED))
            .containsExactly(RecruitmentCaseStatus.PROBATION);

        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.PROBATION))
            .containsExactlyInAnyOrder(RecruitmentCaseStatus.PROBATION_EXTENDED,
                RecruitmentCaseStatus.ZO_RECOMMENDED, RecruitmentCaseStatus.REJECTED);

        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.PROBATION_EXTENDED))
            .containsExactlyInAnyOrder(RecruitmentCaseStatus.ZO_RECOMMENDED,
                RecruitmentCaseStatus.REJECTED);

        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.ZO_RECOMMENDED))
            .containsExactly(RecruitmentCaseStatus.ZK_VOTING);

        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.ZK_VOTING))
            .containsExactlyInAnyOrder(RecruitmentCaseStatus.ACCEPTED, RecruitmentCaseStatus.REJECTED);
    }

    @Test
    void terminalStatesHaveNoOutgoingEdges() {
        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.ACCEPTED)).isEmpty();
        assertThat(RecruitmentRules.allowedTargets(RecruitmentCaseStatus.REJECTED)).isEmpty();
    }

    @Test
    void forbiddenEdgesAreRejected() {
        assertThat(RecruitmentRules.canTransition(RecruitmentCaseStatus.NEW, RecruitmentCaseStatus.ACCEPTED))
            .isFalse();
        assertThat(RecruitmentRules.canTransition(RecruitmentCaseStatus.NEW, RecruitmentCaseStatus.PROBATION))
            .isFalse();
        assertThat(RecruitmentRules.canTransition(RecruitmentCaseStatus.DECLARATION_RECEIVED,
            RecruitmentCaseStatus.REJECTED)).isFalse();
        assertThat(RecruitmentRules.canTransition(RecruitmentCaseStatus.PROBATION_EXTENDED,
            RecruitmentCaseStatus.PROBATION_EXTENDED)).isFalse();
        assertThat(RecruitmentRules.canTransition(RecruitmentCaseStatus.PROBATION_EXTENDED,
            RecruitmentCaseStatus.PROBATION)).isFalse();
        assertThat(RecruitmentRules.canTransition(RecruitmentCaseStatus.ZO_RECOMMENDED,
            RecruitmentCaseStatus.ACCEPTED)).isFalse();
        assertThat(RecruitmentRules.canTransition(RecruitmentCaseStatus.ACCEPTED,
            RecruitmentCaseStatus.REJECTED)).isFalse();
        assertThat(RecruitmentRules.canTransition(RecruitmentCaseStatus.REJECTED,
            RecruitmentCaseStatus.ACCEPTED)).isFalse();
    }

    @Test
    void rejectionReasonsFollowTheStates() {
        assertThat(RecruitmentRules.allowedRejectionReasons(RecruitmentCaseStatus.PROBATION))
            .containsExactlyInAnyOrder(RecruitmentRejectionReason.NOT_FIT, RecruitmentRejectionReason.ZK_BAN);
        assertThat(RecruitmentRules.allowedRejectionReasons(RecruitmentCaseStatus.PROBATION_EXTENDED))
            .containsExactlyInAnyOrder(RecruitmentRejectionReason.NOT_FIT, RecruitmentRejectionReason.ZK_BAN);
        assertThat(RecruitmentRules.allowedRejectionReasons(RecruitmentCaseStatus.ZK_VOTING))
            .containsExactlyInAnyOrder(RecruitmentRejectionReason.VOTE_FAILED, RecruitmentRejectionReason.ZK_BAN);

        assertThat(RecruitmentRules.allowedRejectionReasons(RecruitmentCaseStatus.DECLARATION_RECEIVED)).isEmpty();
        assertThat(RecruitmentRules.allowedRejectionReasons(RecruitmentCaseStatus.ZO_RECOMMENDED)).isEmpty();
        assertThat(RecruitmentRules.allowedRejectionReasons(RecruitmentCaseStatus.ACCEPTED)).isEmpty();
    }

    @Test
    void postDeclarationStatesCoverTheM3String() {
        Set<RecruitmentCaseStatus> postDeclaration = RecruitmentRules.POST_DECLARATION_STATES;

        assertThat(postDeclaration).containsExactlyInAnyOrder(
            RecruitmentCaseStatus.DECLARATION_RECEIVED, RecruitmentCaseStatus.PROBATION,
            RecruitmentCaseStatus.PROBATION_EXTENDED, RecruitmentCaseStatus.ZO_RECOMMENDED,
            RecruitmentCaseStatus.ZK_VOTING, RecruitmentCaseStatus.ACCEPTED,
            RecruitmentCaseStatus.REJECTED);

        assertThat(RecruitmentRules.TACIT_ZK_APPROVAL_NOTE)
            .contains("art. 9 ust. 4 pkt 6")
            .contains("termin minął bez opinii");
    }
}
