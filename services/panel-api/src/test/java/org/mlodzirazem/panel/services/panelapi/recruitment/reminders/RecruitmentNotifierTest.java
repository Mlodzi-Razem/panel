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

package org.mlodzirazem.panel.services.panelapi.recruitment.reminders;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailDispatch;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailMessage;
import org.mlodzirazem.panel.services.panelapi.mail.api.TransactionalMail;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@PostgresTest
class RecruitmentNotifierTest extends DomainTestSupport {
    private static final String ZO_ADDRESS = "zo-mazowieckie@board.example";
    private static final String ZK_ADDRESS = "zk@board.example";

    @Autowired
    RecruitmentNotifier notifier;

    @Autowired
    RecruitmentWorkflowService workflow;

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    @MockitoBean
    TransactionalMail mail;

    @BeforeEach
    void setUpBoards() {
        jdbc.execute("DELETE FROM recruitment_notifications");
        setEmail("district-mazowieckie-board", ZO_ADDRESS);
        setEmail("national-board", ZK_ADDRESS);
        when(mail.sendAfterCommit(any())).thenReturn(MailDispatch.SCHEDULED);
    }

    @AfterEach
    void tearDownBoards() {
        jdbc.execute("DELETE FROM recruitment_notifications");
        setEmail("district-mazowieckie-board", null);
        setEmail("national-board", null);
    }

    @Test
    void oneMailPerBoardPerDayListsEveryDueCaseWithoutPersonalData() {
        LocalDate today = LocalDate.now();
        long opens = probationCase("a@kandydat.example", today.minusDays(62)).getId();
        long closing = probationCase("b@kandydat.example", today.minusDays(70)).getId();
        clearInvocations(mail);

        RecruitmentNotifier.Report first = notifier.sendZoDailyReminders(today);

        assertThat(first.scheduled()).isEqualTo(1);
        MailMessage message = singleMessage("recruitment-zo-reminders");
        assertThat(message.recipient()).isEqualTo(ZO_ADDRESS);
        assertThat(message.textBody())
            .contains("sprawa nr " + opens, "sprawa nr " + closing, "/rekrutacja/" + opens, "za 6 dni")
            .doesNotContain("Kolendra", "Kowalska", "kandydat.example", "1990");

        RecruitmentNotifier.Report second = notifier.sendZoDailyReminders(today);

        assertThat(second.alreadySent()).isEqualTo(1);
        assertThat(second.scheduled()).isZero();
        verify(mail, org.mockito.Mockito.times(1)).sendAfterCommit(any());
    }

    @Test
    void windowWarningBoundaryIsSevenDaysInclusive() {
        LocalDate today = LocalDate.now();
        long edge = probationCase("granica@kandydat.example", today.minusDays(69)).getId(); // +76 = today + 7
        long outside = probationCase("poza@kandydat.example", today.minusDays(68)).getId(); // today + 8
        clearInvocations(mail);

        notifier.sendZoDailyReminders(today);

        MailMessage message = singleMessage("recruitment-zo-reminders");
        assertThat(message.textBody()).contains("sprawa nr " + edge, "za 7 dni")
            .doesNotContain("sprawa nr " + outside);
    }

    @Test
    void overdueCasesStayOnTheListAsSoftReminder() {
        LocalDate today = LocalDate.now();
        long late = probationCase("spozniona@kandydat.example", today.minusDays(80)).getId();
        clearInvocations(mail);

        notifier.sendZoDailyReminders(today);

        assertThat(singleMessage("recruitment-zo-reminders").textBody())
            .contains("sprawa nr " + late, "po terminie o 4 dni");
    }

    @Test
    void admissionResolutionDeadlineIsListedThreeDaysAhead() {
        LocalDate today = LocalDate.now();
        // recommended 5 days ago -> resolution due in 2 days (7 - 5)
        long recommended = recommendedCase("uchwala@kandydat.example", today.minusDays(100), today.minusDays(5));
        clearInvocations(mail);

        notifier.sendZoDailyReminders(today);

        assertThat(singleMessage("recruitment-zo-reminders").textBody())
            .contains("uchwały ZO", "sprawa nr " + recommended, "za 2 dni");
    }

    @Test
    void nothingDueMeansNoMail() {
        LocalDate today = LocalDate.now();
        probationCase("spokojna@kandydat.example", today.minusDays(10));
        clearInvocations(mail);

        RecruitmentNotifier.Report report = notifier.sendZoDailyReminders(today);

        assertThat(report.bodiesWithDue()).isZero();
        verify(mail, never()).sendAfterCommit(any());
    }

    @Test
    void boardWithoutAddressGetsNothingAndIsCounted() {
        LocalDate today = LocalDate.now();
        probationCase("bez-adresu@kandydat.example", today.minusDays(62));
        setEmail("district-mazowieckie-board", null);
        clearInvocations(mail);

        RecruitmentNotifier.Report report = notifier.sendZoDailyReminders(today);

        assertThat(report.noAddress()).isEqualTo(1);
        verify(mail, never()).sendAfterCommit(any());
    }

    @Test
    void withMailSwitchedOffNothingIsSentOrLoggedAndTheDayIsPickedUpLater() {
        LocalDate today = LocalDate.now();
        probationCase("wylaczona@kandydat.example", today.minusDays(62));
        clearInvocations(mail);
        when(mail.sendAfterCommit(any())).thenReturn(MailDispatch.DISABLED);

        RecruitmentNotifier.Report off = notifier.sendZoDailyReminders(today);

        assertThat(off.notSent()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_notifications", Integer.class)).isZero();

        when(mail.sendAfterCommit(any())).thenReturn(MailDispatch.SCHEDULED);

        assertThat(notifier.sendZoDailyReminders(today).scheduled()).isEqualTo(1);
    }

    @Test
    void zkDigestReportsCandidatesAdmittedToTheInitialPeriodOnceAndOnlyNumbers() {
        TRecruitmentCase admitted = probationCase("przyjety@kandydat.example", LocalDate.now().minusDays(1));
        clearInvocations(mail);

        RecruitmentNotifier.Report first = notifier.sendZkProbationDigest(Instant.now().plusSeconds(5));

        assertThat(first.scheduled()).isEqualTo(1);
        MailMessage message = singleMessage("recruitment-zk-probation-digest");
        assertThat(message.recipient()).isEqualTo(ZK_ADDRESS);
        assertThat(message.textBody()).contains("art. 9 ust. 4 pkt 3", "sprawa nr " + admitted.getId())
            .doesNotContain("Kolendra", "Kowalska", "kandydat.example");

        clearInvocations(mail);

        RecruitmentNotifier.Report second = notifier.sendZkProbationDigest(Instant.now().plusSeconds(10));

        assertThat(second.bodiesWithDue()).isZero();
        verify(mail, never()).sendAfterCommit(any());
    }

    @Test
    void zkDigestKeepsItsEventsWhileMailIsOff() {
        probationCase("czeka@kandydat.example", LocalDate.now().minusDays(1));
        clearInvocations(mail);
        when(mail.sendAfterCommit(any())).thenReturn(MailDispatch.DISABLED);

        notifier.sendZkProbationDigest(Instant.now().plusSeconds(5));

        when(mail.sendAfterCommit(any())).thenReturn(MailDispatch.SCHEDULED);

        assertThat(notifier.sendZkProbationDigest(Instant.now().plusSeconds(10)).scheduled()).isEqualTo(1);
    }

    @Test
    void zoRecommendationNotifiesTheZkImmediatelyAndOnce() {
        TRecruitmentCase probation = probationCase("rekomendacja@kandydat.example", LocalDate.now().minusDays(80));
        clearInvocations(mail);

        workflow.recordZoRecommendation(probation.getId(), actor(), board("mazowieckie").getId(),
            probation.getAssessmentNotBeforeOn().plusDays(3), "ZO/MZ/REC/26", null);

        MailMessage message = singleMessage("recruitment-zk-recommendation");
        assertThat(message.recipient()).isEqualTo(ZK_ADDRESS);
        assertThat(message.textBody()).contains("sprawie nr " + probation.getId())
            .doesNotContain("Kolendra", "Kowalska", "kandydat.example");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM recruitment_notifications WHERE kind = 'ZK_RECOMMENDATION'", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void recommendationNoticeNeverBreaksTheWorkflowWhenMailIsOff() {
        TRecruitmentCase probation = probationCase("cicha@kandydat.example", LocalDate.now().minusDays(80));
        when(mail.sendAfterCommit(any())).thenReturn(MailDispatch.DISABLED);

        TRecruitmentCase recommended = workflow.recordZoRecommendation(probation.getId(), actor(),
            board("mazowieckie").getId(), probation.getAssessmentNotBeforeOn().plusDays(3), "ZO/MZ/REC/27", null);

        assertThat(recommended.getStatus()).isEqualTo(RecruitmentCaseStatus.ZO_RECOMMENDED);
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private MailMessage singleMessage(String purpose) {
        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mail, org.mockito.Mockito.atLeastOnce()).sendAfterCommit(captor.capture());
        List<MailMessage> matching = captor.getAllValues().stream()
            .filter(message -> message.purpose().equals(purpose)).toList();
        assertThat(matching).hasSize(1);
        return matching.getFirst();
    }

    private void setEmail(String bodyCode, String email) {
        jdbc.update("UPDATE bodies SET email = ? WHERE code = ?", email, bodyCode);
    }

    private long actor() {
        TMember member = newMember("Antoni", "Okręgowy", "mazowieckie");
        grant(member, board("mazowieckie"), "COORDINATOR");
        return member.getId();
    }

    private TRecruitmentCase probationCase(String email, LocalDate receivedOn) {
        TRecruitmentCase candidate = new TRecruitmentCase();
        candidate.setDistrict(district("mazowieckie"));
        candidate.setCandidateFirstName("Kolendra");
        candidate.setCandidateLastName("Kowalska");
        candidate.setCandidateEmail(email);
        candidate.setCandidateBirthDate(LocalDate.of(2007, 4, 1));
        candidate.setStatus(RecruitmentCaseStatus.AWAITING_DECLARATION);
        candidate = caseRepository.saveAndFlush(candidate);

        long actor = actor();
        TRecruitmentCase declared = workflow.recordDeclaration(candidate.getId(), actor, DeclarationForm.ELECTRONIC,
            RecruitmentAssessmentTrack.STANDARD, receivedOn);

        return workflow.startProbation(declared.getId(), actor);
    }

    private long recommendedCase(String email, LocalDate receivedOn, LocalDate recommendedOn) {
        TRecruitmentCase probation = probationCase(email, receivedOn);
        return workflow.recordZoRecommendation(probation.getId(), actor(), board("mazowieckie").getId(),
            recommendedOn, "ZO/MZ/REC/28", null).getId();
    }
}
