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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentSource;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class RecruitmentCaseServiceTest extends DomainTestSupport {
    @Autowired
    RecruitmentCaseService recruitmentCases;

    @Autowired
    RecruitmentWorkflowService workflow;

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    private TMember member() {
        return newMember("Halina", "Kordynatorka", "mazowieckie");
    }

    @Test
    void createOpensACaseAtNewWithACaseCreatedEvent() {
        TMember operator = member();

        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("rekrutka@example.org", null), operator.getId());

        assertThat(candidateCase.getId()).isNotNull();
        assertThat(candidateCase.getStatus()).isEqualTo(RecruitmentCaseStatus.NEW);
        assertThat(candidateCase.getSource()).isEqualTo(RecruitmentSource.MANUAL);
        assertThat(candidateCase.getCandidateFirstName()).isEqualTo("Kandydat");
        assertThat(candidateCase.getCandidateLastName()).isEqualTo("Kandydacki");
        assertThat(candidateCase.getCandidateEmail()).isEqualTo("rekrutka@example.org");
        assertThat(jdbc.queryForObject(
            "SELECT b.code FROM recruitment_cases c JOIN bodies b ON b.id = c.district_body_id WHERE c.id = ?",
            String.class, candidateCase.getId())).isEqualTo("district-mazowieckie");
        assertThat(candidateCase.getCreatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM recruitment_cases WHERE id = ?",
            Long.class, candidateCase.getId())).isEqualTo(operator.getId());
        assertThat(candidateCase.getClosedAt()).isNull();

        List<TRecruitmentCaseEvent> timeline = recruitmentCases.timeline(candidateCase.getId());
        assertThat(timeline).hasSize(1);
        assertThat(timeline.getFirst().getEventType()).isEqualTo(RecruitmentCaseEventType.CASE_CREATED);
        assertThat(timeline.getFirst().getPayload())
            .contains("candidateFirstName")
            .contains("candidateLastName")
            .contains("candidateEmail")
            .doesNotContain("rekrutka@example.org");

        assertThat(auditCount("recruitment :: case-create")).isEqualTo(1);
        assertThat(auditPayloads("recruitment :: case-create"))
            .contains("candidateFirstName")
            .doesNotContain("rekrutka@example.org");
    }

    @Test
    void googleFormIsAValidSource() {
        TMember operator = member();

        TRecruitmentCase candidateCase = recruitmentCases.create(
            new NewRecruitmentCase(district("mazowieckie").getId(), RecruitmentSource.GOOGLE_FORM,
                candidateFields("zgorzetka@example.org", null)), operator.getId());

        assertThat(candidateCase.getSource()).isEqualTo(RecruitmentSource.GOOGLE_FORM);
    }

    @Test
    void createValidatesTheDistrictBody() {
        TMember operator = member();

        NewRecruitmentCase intoBoard = new NewRecruitmentCase(board("mazowieckie").getId(),
            RecruitmentSource.MANUAL, candidateFields("dozarzadu@example.org", null));
        assertThatThrownBy(() -> recruitmentCases.create(intoBoard, operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("DISTRICT_REQUIRED");

        NewRecruitmentCase nowhere = new NewRecruitmentCase(-1L, RecruitmentSource.MANUAL,
            candidateFields("donikad@example.org", null));
        assertThatThrownBy(() -> recruitmentCases.create(nowhere, operator.getId()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createRequiresBothNames() {
        TMember operator = member();

        NewRecruitmentCase noLastName = new NewRecruitmentCase(district("mazowieckie").getId(),
            RecruitmentSource.MANUAL,
            Map.of(RecruitmentCandidateField.FIRST_NAME, "Kandydat",
                RecruitmentCandidateField.EMAIL, "beznazwiska@example.org"));

        assertThatThrownBy(() -> recruitmentCases.create(noLastName, operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("NAMES_ARE_A_PAIR");
    }

    @Test
    void createValidatesTheAdmissionAge() {
        TMember operator = member();

        NewRecruitmentCase tooYoung = candidate("mloda@example.org", LocalDate.now().minusYears(15));
        assertThatThrownBy(() -> recruitmentCases.create(tooYoung, operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");

        NewRecruitmentCase tooOld = candidate("mature@example.org", LocalDate.now().minusYears(27));
        assertThatThrownBy(() -> recruitmentCases.create(tooOld, operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");

        TRecruitmentCase justRight = recruitmentCases.create(candidate("okie@example.org",
            LocalDate.now().minusYears(16)), operator.getId());
        assertThat(justRight.getCandidateBirthDate()).isEqualTo(LocalDate.now().minusYears(16));
    }

    @Test
    void oneOpenCasePerEmailPerDistrict() {
        TMember operator = member();

        TRecruitmentCase first = recruitmentCases.create(candidate("duplikat@example.org", null), operator.getId());
        assertThatThrownBy(() -> recruitmentCases.create(candidate("DUPLIKAT@example.org", null), operator.getId()))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("OPEN_CASE_EXISTS");

        recruitmentCases.transition(first.getId(), RecruitmentCaseStatus.REJECTED,
            RecruitmentRejectionReason.FALSE_ENTRY, operator.getId());

        TRecruitmentCase second = recruitmentCases.create(candidate("duplikat@example.org", null), operator.getId());
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void updateChangesOnlyTheSubmittedFields() {
        TMember operator = member();
        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("edytowana@example.org", null), operator.getId());

        RecruitmentCaseUpdate update = recruitmentCases.update(candidateCase.getId(),
            Map.of(RecruitmentCandidateField.PHONE, "+48 600 700 800"), operator.getId());

        assertThat(update.changedFields()).containsExactly(RecruitmentCandidateField.PHONE);
        assertThat(update.recruitmentCase().getCandidateEmail()).isEqualTo("edytowana@example.org");
        assertThat(auditCount("recruitment :: case-update")).isEqualTo(1);

        recruitmentCases.update(candidateCase.getId(),
            Map.of(RecruitmentCandidateField.PHONE, "  "), operator.getId());
        assertThat(recruitmentCases.get(candidateCase.getId()).getCandidatePhone()).isNull();
    }

    @Test
    void updateCannotWipeTheNamesOrBreakTheAgeRule() {
        TMember operator = member();
        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("zmieniona@example.org", null), operator.getId());

        Map<RecruitmentCandidateField, Object> wipe = Map.of(
            RecruitmentCandidateField.FIRST_NAME, "  ",
            RecruitmentCandidateField.LAST_NAME, "  ");

        assertThatThrownBy(() -> recruitmentCases.update(candidateCase.getId(), wipe, operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("FIELD_REQUIRED");

        assertThatThrownBy(() -> recruitmentCases.update(candidateCase.getId(),
            Map.of(RecruitmentCandidateField.BIRTH_DATE, LocalDate.now().minusYears(30)), operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");
    }

    @Test
    void intakeClusterEdges() {
        TMember operator = member();
        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("klaster@example.org", null), operator.getId());

        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.IN_PROGRESS, null, operator.getId());
        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.NO_CONTACT, null, operator.getId());
        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.IN_PROGRESS, null, operator.getId());
        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.AWAITING_DECLARATION, null,
            operator.getId());

        assertThat(recruitmentCases.get(candidateCase.getId()).getStatus())
            .isEqualTo(RecruitmentCaseStatus.AWAITING_DECLARATION);
        assertThat(auditCount("recruitment :: case-transition")).isEqualTo(4);
    }

    @Test
    void edgesOutsideTheIntakeClusterAreRefused() {
        TMember operator = member();
        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("poza@example.org", null), operator.getId());

        assertThatThrownBy(() -> recruitmentCases.transition(candidateCase.getId(),
            RecruitmentCaseStatus.AWAITING_DECLARATION, null, operator.getId()))
            .isInstanceOf(RuleViolationException.class);

        assertThatThrownBy(() -> recruitmentCases.transition(candidateCase.getId(),
            RecruitmentCaseStatus.NEW, null, operator.getId()))
            .isInstanceOf(RuleViolationException.class);

        assertThatThrownBy(() -> recruitmentCases.transition(candidateCase.getId(),
            RecruitmentCaseStatus.DECLARATION_RECEIVED, null, operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("TRANSITION_NOT_ALLOWED");

        assertThatThrownBy(() -> recruitmentCases.transition(candidateCase.getId(),
            RecruitmentCaseStatus.PROBATION, null, operator.getId()))
            .isInstanceOf(RuleViolationException.class);

        assertThatThrownBy(() -> recruitmentCases.transition(candidateCase.getId(),
            RecruitmentCaseStatus.ACCEPTED, null, operator.getId()))
            .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void rejectionClosesTheCaseWithAReason() {
        TMember operator = member();
        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("niespoznana@example.org", null), operator.getId());

        assertThatThrownBy(() -> recruitmentCases.transition(candidateCase.getId(),
            RecruitmentCaseStatus.REJECTED, null, operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("REJECTION_REASON_REQUIRED");

        assertThatThrownBy(() -> recruitmentCases.transition(candidateCase.getId(),
            RecruitmentCaseStatus.REJECTED, RecruitmentRejectionReason.VOTE_FAILED, operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("REJECTION_REASON_NOT_ALLOWED");

        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.REJECTED,
            RecruitmentRejectionReason.FALSE_ENTRY, operator.getId());

        TRecruitmentCase closed = recruitmentCases.get(candidateCase.getId());
        assertThat(closed.getStatus()).isEqualTo(RecruitmentCaseStatus.REJECTED);
        assertThat(closed.getRejectionReason()).isEqualTo(RecruitmentRejectionReason.FALSE_ENTRY);
        assertThat(closed.getClosedAt()).isEqualTo(LocalDate.now());

        List<TRecruitmentCaseEvent> timeline = recruitmentCases.timeline(candidateCase.getId());
        assertThat(timeline).extracting(TRecruitmentCaseEvent::getEventType)
            .containsExactly(RecruitmentCaseEventType.CASE_CREATED, RecruitmentCaseEventType.STATUS_CHANGED,
                RecruitmentCaseEventType.CASE_CLOSED);
    }

    @Test
    void closedCasesAreReadOnly() {
        TMember operator = member();
        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("ostateczna@example.org", null), operator.getId());
        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.REJECTED,
            RecruitmentRejectionReason.FALSE_ENTRY, operator.getId());

        assertThatThrownBy(() -> recruitmentCases.update(candidateCase.getId(),
            Map.of(RecruitmentCandidateField.PHONE, "+48 100"), operator.getId()))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("CASE_NOT_OPEN");

        assertThatThrownBy(() -> recruitmentCases.transition(candidateCase.getId(),
            RecruitmentCaseStatus.IN_PROGRESS, null, operator.getId()))
            .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> recruitmentCases.sendReminder(candidateCase.getId(), operator.getId()))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("CASE_NOT_OPEN");
    }

    @Test
    void notesAndRemindersAreTimelineEvents() {
        TMember operator = member();
        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("notowana@example.org", null), operator.getId());

        TRecruitmentCaseEvent note = recruitmentCases.addNote(candidateCase.getId(), "Dzwonić w czwartek", operator.getId());
        assertThat(note.getEventType()).isEqualTo(RecruitmentCaseEventType.NOTE_ADDED);
        assertThat(note.getPayload()).contains("note").doesNotContain("Dzwonić");
        assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM recruitment_case_events WHERE id = ?",
            Long.class, note.getId())).isEqualTo(operator.getId());

        TRecruitmentCaseEvent reminder = recruitmentCases.sendReminder(candidateCase.getId(), operator.getId());
        assertThat(reminder.getEventType()).isEqualTo(RecruitmentCaseEventType.REMINDER_SENT);

        assertThatThrownBy(() -> recruitmentCases.addNote(candidateCase.getId(), "   ", operator.getId()))
            .isInstanceOf(RuleViolationException.class);

        assertThatThrownBy(() -> recruitmentCases.addNote(candidateCase.getId(), "x".repeat(2001), operator.getId()))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("NOTE_TOO_LONG");

        List<TRecruitmentCaseEvent> timeline = recruitmentCases.timeline(candidateCase.getId());
        assertThat(timeline).extracting(TRecruitmentCaseEvent::getEventType)
            .containsExactly(RecruitmentCaseEventType.CASE_CREATED, RecruitmentCaseEventType.NOTE_ADDED,
                RecruitmentCaseEventType.REMINDER_SENT);
    }

    @Test
    void availableTransitionsReportTheIntakeClusterAndItsBoundary() {
        TMember operator = member();
        TRecruitmentCase candidateCase = recruitmentCases.create(candidate("opcje-s@example.org", null), operator.getId());

        List<RecruitmentCaseTransitionOption> fromNew = recruitmentCases.availableTransitions(candidateCase.getId());
        assertThat(fromNew).extracting(RecruitmentCaseTransitionOption::target)
            .containsExactly(RecruitmentCaseStatus.IN_PROGRESS, RecruitmentCaseStatus.NO_CONTACT,
                RecruitmentCaseStatus.REJECTED);
        fromNew.forEach(option -> {
            assertThat(option.allowed()).as("target %s", option.target()).isTrue();
            assertThat(option.blockedReason()).as("target %s", option.target()).isNull();
            if (option.target() == RecruitmentCaseStatus.REJECTED) {
                assertThat(option.rejectionReasons())
                    .containsExactly(RecruitmentRejectionReason.FALSE_ENTRY);
            } else {
                assertThat(option.rejectionReasons()).isNull();
            }
        });

        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.IN_PROGRESS,
            null, operator.getId());
        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.AWAITING_DECLARATION,
            null, operator.getId());

        RecruitmentCaseTransitionOption m3Boundary = recruitmentCases.availableTransitions(candidateCase.getId())
            .stream()
            .filter(option -> option.target() == RecruitmentCaseStatus.DECLARATION_RECEIVED)
            .findFirst()
            .orElseThrow();
        assertThat(m3Boundary.allowed()).isFalse();
        assertThat(m3Boundary.blockedReason()).isEqualTo("DECLARATION_ARTIFACT_REQUIRED");

        recruitmentCases.transition(candidateCase.getId(), RecruitmentCaseStatus.REJECTED,
            RecruitmentRejectionReason.NO_DECLARATION, operator.getId());

        assertThat(recruitmentCases.availableTransitions(candidateCase.getId())).isEmpty();
        assertThatThrownBy(() -> recruitmentCases.availableTransitions(candidateCase.getId() + 999))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void availableTransitionsExposeTheFullPostDeclarationClusterAndItsReservations() {
        long actor = districtActor();
        TRecruitmentCase opened = recruitmentCases.create(candidate("mapa-dekl@example.org", null), actor);
        opened.setStatus(RecruitmentCaseStatus.AWAITING_DECLARATION);
        caseRepository.saveAndFlush(opened);

        // AWAITING_DECLARATION: the declaration arrival stays artifact-gated (M4a).
        RecruitmentCaseTransitionOption artifactGate = recruitmentCases.availableTransitions(opened.getId())
            .stream()
            .filter(option -> option.target() == RecruitmentCaseStatus.DECLARATION_RECEIVED)
            .findFirst()
            .orElseThrow();
        assertThat(artifactGate.allowed()).isFalse();
        assertThat(artifactGate.blockedReason()).isEqualTo("DECLARATION_ARTIFACT_REQUIRED");

        TRecruitmentCase declared = workflow.recordDeclaration(opened.getId(), actor, DeclarationForm.SCAN,
            RecruitmentAssessmentTrack.STANDARD, LocalDate.now().minusDays(80));
        assertThat(recruitmentCases.availableTransitions(declared.getId()))
            .extracting(RecruitmentCaseTransitionOption::target)
            .containsExactly(RecruitmentCaseStatus.PROBATION);

        TRecruitmentCase probation = workflow.startProbation(declared.getId(), actor);
        List<RecruitmentCaseTransitionOption> fromProbation = recruitmentCases.availableTransitions(probation.getId());
        assertThat(fromProbation).extracting(RecruitmentCaseTransitionOption::target)
            .containsExactly(RecruitmentCaseStatus.PROBATION_EXTENDED, RecruitmentCaseStatus.ZO_RECOMMENDED,
                RecruitmentCaseStatus.REJECTED);
        RecruitmentCaseTransitionOption probationRejection = fromProbation.stream()
            .filter(option -> option.target() == RecruitmentCaseStatus.REJECTED)
            .findFirst()
            .orElseThrow();
        assertThat(probationRejection.rejectionReasons())
            .containsExactly(RecruitmentRejectionReason.NOT_FIT, RecruitmentRejectionReason.ZK_BAN);

        TRecruitmentCase recommended = workflow.recordZoRecommendation(probation.getId(), actor,
            board("mazowieckie").getId(), probation.getAssessmentNotBeforeOn().plusDays(3),
            "ZO/MZ/44/2026", null);
        assertThat(recruitmentCases.availableTransitions(recommended.getId()))
            .extracting(RecruitmentCaseTransitionOption::target)
            .containsExactly(RecruitmentCaseStatus.ZK_VOTING);

        TRecruitmentCase voting = workflow.startZkVoting(recommended.getId(), actor);
        List<RecruitmentCaseTransitionOption> fromVoting = recruitmentCases.availableTransitions(voting.getId());
        assertThat(fromVoting).extracting(RecruitmentCaseTransitionOption::target)
            .containsExactly(RecruitmentCaseStatus.ACCEPTED, RecruitmentCaseStatus.REJECTED);
        assertThat(fromVoting.getFirst().allowed()).isFalse();
        assertThat(fromVoting.getFirst().blockedReason()).isEqualTo("BOTH_DECISIONS_REQUIRED");
        assertThat(fromVoting.getFirst().rejectionReasons()).isNull();
        assertThat(fromVoting.get(1).allowed()).isTrue();
        assertThat(fromVoting.get(1).rejectionReasons())
            .containsExactly(RecruitmentRejectionReason.VOTE_FAILED, RecruitmentRejectionReason.ZK_BAN);

        // One decision is not enough; with both recorded the admission (M6a) opens.
        TMember zk = newMember("Bogna", "Krajowa", "mazowieckie");
        grant(zk, body("national-board"), "CO_CHAIR");
        workflow.recordZkApproval(voting.getId(), zk.getId(), body("national-board").getId(),
            recommended.getZoRecommendedOn().plusDays(1), "ZK/45/2026", null);
        assertThat(recruitmentCases.availableTransitions(voting.getId()).getFirst().allowed()).isFalse();

        workflow.recordAdmissionResolution(voting.getId(), zk.getId(), board("mazowieckie").getId(),
            recommended.getZoRecommendedOn().plusDays(2), "ZO/MZ/46/2026", null);
        RecruitmentCaseTransitionOption accepted = recruitmentCases.availableTransitions(voting.getId()).getFirst();
        assertThat(accepted.target()).isEqualTo(RecruitmentCaseStatus.ACCEPTED);
        assertThat(accepted.allowed()).isTrue();
        assertThat(accepted.blockedReason()).isNull();
    }

    private long districtActor() {
        TMember actor = newMember("Aniela", "Okręgowa", "mazowieckie");
        grant(actor, board("mazowieckie"), "COORDINATOR");
        return actor.getId();
    }

    private NewRecruitmentCase candidate(String email, LocalDate birthDate) {
        return new NewRecruitmentCase(district("mazowieckie").getId(), RecruitmentSource.MANUAL,
            candidateFields(email, birthDate));
    }

    private static Map<RecruitmentCandidateField, Object> candidateFields(String email, LocalDate birthDate) {
        Map<RecruitmentCandidateField, Object> values = new EnumMap<>(RecruitmentCandidateField.class);
        values.put(RecruitmentCandidateField.FIRST_NAME, "Kandydat");
        values.put(RecruitmentCandidateField.LAST_NAME, "Kandydacki");
        values.put(RecruitmentCandidateField.EMAIL, email);

        if (birthDate != null) {
            values.put(RecruitmentCandidateField.BIRTH_DATE, birthDate);
        }

        return values;
    }
}
