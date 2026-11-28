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

package org.mlodzirazem.panel.services.panelapi.api.v1.recruitment;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@PostgresTest
class RecruitmentCaseViewsTest extends DomainTestSupport {
    private static final RecruitmentCaseSearch.Scope GLOBAL = new RecruitmentCaseSearch.Scope(true, Set.of());

    @Autowired
    RecruitmentCaseSearch search;

    @Autowired
    RecruitmentWorkflowService workflow;

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    @Test
    void everyProcedureViewSelectsItsCasesAndCountsMatch() {
        TRecruitmentCase fresh = newCase(RecruitmentCaseStatus.NEW);
        TRecruitmentCase waitingShort = newCase(RecruitmentCaseStatus.AWAITING_DECLARATION);
        TRecruitmentCase waitingLong = newCase(RecruitmentCaseStatus.AWAITING_DECLARATION);
        jdbc.update("UPDATE recruitment_cases SET created_at = now() - interval '30 days' WHERE id = ?",
            waitingLong.getId());
        TRecruitmentCase recommended = recommended();
        TRecruitmentCase voting = recommended();
        workflow.startZkVoting(voting.getId(), actor());
        TRecruitmentCase rejected = newCase(RecruitmentCaseStatus.IN_PROGRESS);
        jdbc.update("UPDATE recruitment_cases SET status = 'REJECTED', rejection_reason = 'NOT_FIT',"
            + " closed_at = current_date WHERE id = ?", rejected.getId());
        TRecruitmentCase anonymised = newCase(RecruitmentCaseStatus.IN_PROGRESS);
        jdbc.update("UPDATE recruitment_cases SET status = 'REJECTED', rejection_reason = 'NOT_FIT',"
            + " closed_at = current_date, anonymized_at = now(), candidate_email = NULL, candidate_birth_date = NULL,"
            + " candidate_first_name = NULL, candidate_last_name = NULL WHERE id = ?", anonymised.getId());

        assertThat(ids(RecruitmentCaseView.TO_CONDUCT)).contains(fresh.getId(), waitingShort.getId(),
            waitingLong.getId(), recommended.getId(), voting.getId())
            .doesNotContain(rejected.getId(), anonymised.getId());
        assertThat(ids(RecruitmentCaseView.PROBABLY_NO_DECLARATION)).containsExactly(waitingLong.getId());
        assertThat(ids(RecruitmentCaseView.RECOMMENDED)).containsExactly(recommended.getId());
        assertThat(ids(RecruitmentCaseView.ZK_VOTING)).containsExactly(voting.getId());
        // closed today and the retention period is 365 days: nothing is overdue yet
        assertThat(ids(RecruitmentCaseView.TO_DELETE)).isEmpty();
        jdbc.update("UPDATE recruitment_cases SET closed_at = current_date - 366 WHERE id = ?", rejected.getId());
        assertThat(ids(RecruitmentCaseView.TO_DELETE)).containsExactly(rejected.getId());

        Map<RecruitmentCaseView, Long> counts = search.viewCounts(GLOBAL, null);

        assertThat(counts).containsEntry(RecruitmentCaseView.PROBABLY_NO_DECLARATION, 1L)
            .containsEntry(RecruitmentCaseView.RECOMMENDED, 1L)
            .containsEntry(RecruitmentCaseView.ZK_VOTING, 1L)
            .containsEntry(RecruitmentCaseView.TO_DELETE, 1L)
            .containsEntry(RecruitmentCaseView.TO_CONDUCT, 5L);
    }

    @Test
    void toDeleteViewStartsOnTheRetentionDayAndSkipsAnonymisedCases() {
        TRecruitmentCase rejected = newCase(RecruitmentCaseStatus.IN_PROGRESS);
        jdbc.update("UPDATE recruitment_cases SET status = 'REJECTED', rejection_reason = 'NOT_FIT',"
            + " closed_at = current_date - 364 WHERE id = ?", rejected.getId());
        assertThat(ids(RecruitmentCaseView.TO_DELETE)).doesNotContain(rejected.getId());

        jdbc.update("UPDATE recruitment_cases SET closed_at = current_date - 365 WHERE id = ?", rejected.getId());
        assertThat(ids(RecruitmentCaseView.TO_DELETE)).contains(rejected.getId());

        jdbc.update("UPDATE recruitment_cases SET anonymized_at = now(), candidate_email = NULL,"
            + " candidate_birth_date = NULL, candidate_first_name = NULL, candidate_last_name = NULL WHERE id = ?",
            rejected.getId());
        assertThat(ids(RecruitmentCaseView.TO_DELETE)).doesNotContain(rejected.getId());
    }

    @Test
    void viewsRespectTheDistrictScopeOfTheCaller() {
        newCase(RecruitmentCaseStatus.NEW);
        RecruitmentCaseSearch.Scope other = new RecruitmentCaseSearch.Scope(false, Set.of(-1L));

        assertThat(search.search(other, new RecruitmentCaseSearch.Filter(null, null, null,
            RecruitmentCaseView.TO_CONDUCT), 0, 25).ids()).isEmpty();
        assertThat(search.viewCounts(other, null).values()).containsOnly(0L);
    }

    private List<Long> ids(RecruitmentCaseView view) {
        return search.search(GLOBAL, new RecruitmentCaseSearch.Filter(null, null, null, view), 0, 100).ids();
    }

    private TRecruitmentCase newCase(RecruitmentCaseStatus status) {
        TRecruitmentCase candidate = new TRecruitmentCase();
        candidate.setDistrict(district("mazowieckie"));
        candidate.setCandidateFirstName("Kolendra");
        candidate.setCandidateLastName("Kowalska");
        candidate.setCandidateEmail("widok" + System.nanoTime() + "@example.org");
        candidate.setCandidateBirthDate(LocalDate.of(2007, 4, 1));
        candidate.setStatus(status);
        return caseRepository.saveAndFlush(candidate);
    }

    private long actor() {
        TMember member = newMember("Antoni", "Okręgowy", "mazowieckie");
        grant(member, board("mazowieckie"), "COORDINATOR");
        return member.getId();
    }

    private TRecruitmentCase recommended() {
        long actor = actor();
        TRecruitmentCase waiting = newCase(RecruitmentCaseStatus.AWAITING_DECLARATION);
        TRecruitmentCase declared = workflow.recordDeclaration(waiting.getId(), actor, DeclarationForm.SCAN,
            RecruitmentAssessmentTrack.STANDARD, LocalDate.now().minusDays(80));
        TRecruitmentCase probation = workflow.startProbation(declared.getId(), actor);

        return workflow.recordZoRecommendation(probation.getId(), actor, board("mazowieckie").getId(),
            declared.getAssessmentNotBeforeOn().plusDays(3), "ZO/MZ/REC/V", null);
    }
}
