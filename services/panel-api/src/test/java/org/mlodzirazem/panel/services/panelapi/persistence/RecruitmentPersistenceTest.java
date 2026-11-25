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

package org.mlodzirazem.panel.services.panelapi.persistence;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentDecisionKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBan;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TAdmissionBanRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecision;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecisionRepository;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class RecruitmentPersistenceTest extends DomainTestSupport {
    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String SEQUENCE_LIMIT = "2200H";

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    @Autowired
    TRecruitmentCaseEventRepository caseEventRepository;

    @Autowired
    TRecruitmentDecisionRepository decisionRepository;

    @Autowired
    TAdmissionBanRepository admissionBanRepository;

    @Test
    void recruitmentCaseRoundTrip() {
        TRecruitmentCase saved = newCase("kolowalska@example.org");

        TRecruitmentCase loaded = caseRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getCandidateFirstName()).isEqualTo("Kolendra");
        assertThat(loaded.getCandidateLastName()).isEqualTo("Kowalska");
        assertThat(loaded.getCandidateEmail()).isEqualTo("kolowalska@example.org");
        assertThat(loaded.getStatus()).isEqualTo(RecruitmentCaseStatus.NEW);
        assertThat(jdbc.queryForObject(
            "SELECT b.code FROM recruitment_cases c JOIN bodies b ON b.id = c.district_body_id WHERE c.id = ?",
            String.class, loaded.getId())).isEqualTo("district-mazowieckie");
        assertThat(loaded.getSource().name()).isEqualTo("MANUAL");
        assertThat(loaded.getAssessmentTrack().name()).isEqualTo("STANDARD");
        assertThat(loaded.getExtensionCount()).isZero();
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void caseLifecycleReachesAcceptedOnlyWithBothDecisions() {
        TRecruitmentCase candidate = caseRepository.saveAndFlush(dressedCase(newCase("przyjeta@example.org")));
        saveDecision(candidate, RecruitmentDecisionKind.ZK_APPROVAL, board("mazowieckie").getId(), "ZK/APR/101/26");
        saveDecision(candidate, RecruitmentDecisionKind.ZO_ADMISSION_RESOLUTION, board("mazowieckie").getId(),
            "ZO/MZ/102/26");

        TMember acceptedMember = newMember("Ola", "Przyjeta", "mazowieckie");
        candidate.setStatus(RecruitmentCaseStatus.ACCEPTED);
        candidate.setAcceptedOn(candidate.getAdmissionResolutionNotLaterOn());
        candidate.setAcceptedMember(acceptedMember);
        candidate.setClosedAt(candidate.getAcceptedOn());

        caseRepository.saveAndFlush(candidate);

        assertThat(caseRepository.findById(candidate.getId()).orElseThrow().getStatus())
            .isEqualTo(RecruitmentCaseStatus.ACCEPTED);
    }

    @Test
    void acceptanceWithoutBothDecisionsIsRejected() {
        TRecruitmentCase candidate = caseRepository.saveAndFlush(dressedCase(newCase("wstrzymana@example.org")));
        saveDecision(candidate, RecruitmentDecisionKind.ZK_APPROVAL, board("mazowieckie").getId(), "ZK/APR/103/26");

        assertSqlState(CHECK_VIOLATION, () -> jdbc.update(
            "UPDATE recruitment_cases SET status = 'ACCEPTED', accepted_on = CURRENT_DATE, accepted_member_id = ?, "
                + "closed_at = CURRENT_DATE WHERE id = ?", acceptedMemberId(), candidate.getId()));
    }

    @Test
    void probationRequiresTheDeclarationClock() {
        TRecruitmentCase candidate = newCase("bezdeklaracji@example.org");

        assertSqlState(CHECK_VIOLATION, () -> jdbc.update(
            "UPDATE recruitment_cases SET status = 'PROBATION' WHERE id = ?", candidate.getId()));
    }

    @Test
    void rejectionOfADeclaredCaseKeepsTheDeclarationData() {
        TRecruitmentCase candidate = caseRepository.saveAndFlush(dressedCase(newCase("odrzucona@example.org")));

        jdbc.update("UPDATE recruitment_cases SET status = 'REJECTED', rejection_reason = 'NOT_FIT', "
            + "closed_at = CURRENT_DATE WHERE id = ?", candidate.getId());

        var row = jdbc.queryForMap(
            "SELECT declaration_received_on, declaration_form::text, assessment_not_before_on, status::text "
                + "FROM recruitment_cases WHERE id = ?", candidate.getId());
        assertThat(row.get("declaration_received_on")).isNotNull();
        assertThat(row.get("declaration_form")).isEqualTo("ELECTRONIC");
        assertThat(row.get("assessment_not_before_on")).isNotNull();
        assertThat(row.get("status")).isEqualTo("REJECTED");
    }

    @Test
    void closingRequiresAReasonForRejections() {
        TRecruitmentCase candidate = newCase("bezpowodu@example.org");

        assertSqlState(CHECK_VIOLATION, () -> jdbc.update(
            "UPDATE recruitment_cases SET status = 'REJECTED', closed_at = CURRENT_DATE WHERE id = ?",
            candidate.getId()));
    }

    @Test
    void extensionOfTheInitialPeriodIsRecordedTogetherWithItsDeadline() {
        TRecruitmentCase candidate = newCase("wydluzona@example.org");

        assertSqlState(CHECK_VIOLATION, () -> jdbc.update(
            "UPDATE recruitment_cases SET extension_decided_on = CURRENT_DATE WHERE id = ?", candidate.getId()));

        assertSqlState(CHECK_VIOLATION, () -> jdbc.update(
            "UPDATE recruitment_cases SET extension_count = 2, extension_decided_on = CURRENT_DATE, "
                + "second_assessment_not_later_on = CURRENT_DATE + 31 WHERE id = ?", candidate.getId()));

        jdbc.update("UPDATE recruitment_cases SET extension_count = 1, extension_decided_on = CURRENT_DATE, "
            + "second_assessment_not_later_on = CURRENT_DATE + 31 WHERE id = ?", candidate.getId());
        assertThat(jdbc.queryForObject(
            "SELECT second_assessment_not_later_on - extension_decided_on FROM recruitment_cases WHERE id = ?",
            Integer.class, candidate.getId())).isEqualTo(31);
    }

    @Test
    void anonymizedRejectedCasesArePurgeableMechanically() {
        TRecruitmentCase candidate = caseRepository.saveAndFlush(dressedCase(newCase("zapomniana@example.org")));
        jdbc.update("UPDATE recruitment_cases SET status = 'REJECTED', rejection_reason = 'NO_CONTACT', "
            + "closed_at = CURRENT_DATE - 45 WHERE id = ?", candidate.getId());

        jdbc.update(
            "UPDATE recruitment_cases SET candidate_first_name = NULL, candidate_last_name = NULL, "
                + "candidate_email = NULL, candidate_phone = NULL, candidate_social_profile_url = NULL, "
                + "candidate_birth_date = NULL, anonymized_at = CURRENT_TIMESTAMP WHERE id = ?",
            candidate.getId());

        Integer purgeable = jdbc.queryForObject(
            "SELECT COUNT(*) FROM recruitment_cases WHERE status = 'REJECTED' AND closed_at <= CURRENT_DATE - 30 "
                + "AND anonymized_at IS NOT NULL", Integer.class);
        assertThat(purgeable).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT candidate_first_name IS NULL AND candidate_email IS NULL FROM recruitment_cases WHERE id = ?",
            Boolean.class, candidate.getId())).isTrue();
    }

    @Test
    void oneOpenCasePerEmailPerDistrictOnly() {
        TRecruitmentCase first = newCase("duplikat@example.org");
        assertThat(first.getId()).isNotNull();

        assertSqlState(UNIQUE_VIOLATION, () -> insertCase("duplikat@example.org"));

        jdbc.update("UPDATE recruitment_cases SET status = 'REJECTED', rejection_reason = 'FALSE_ENTRY', "
            + "closed_at = CURRENT_DATE WHERE id = ?", first.getId());
        insertCase("duplikat@example.org");
    }

    @Test
    void decisionsAreUniquePerCaseAndKind() {
        TRecruitmentCase candidate = newCase("unikalna@example.org");

        saveDecision(candidate, RecruitmentDecisionKind.ZK_APPROVAL, board("mazowieckie").getId(), "ZK/APR/104/26");

        assertThatThrownBy(() -> saveDecision(candidate, RecruitmentDecisionKind.ZK_APPROVAL,
            board("mazowieckie").getId(), "ZK/APR/105/26"))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void caseEventsRecordTheTimeline() {
        TRecruitmentCase candidate = newCase("zdarzeniowa@example.org");

        TRecruitmentCaseEvent event = new TRecruitmentCaseEvent();
        event.setRecruitmentCase(candidate);
        event.setEventType(RecruitmentCaseEventType.DECLARATION_RECORDED);
        event.setPayload("{\"fields\": [\"declaration_form\", \"declaration_received_on\"]}");
        caseEventRepository.saveAndFlush(event);

        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM recruitment_case_events e JOIN recruitment_cases c ON c.id = e.case_id "
                + "WHERE c.candidate_email = ?", Integer.class, "zdarzeniowa@example.org")).isEqualTo(1);
    }

    @Test
    void admissionBansWorkAcrossCases() {
        TAdmissionBan ban = new TAdmissionBan();
        ban.setFormalIdentifier("29999912345");
        ban.setDecisionSignature("ZK/BAN/77/26");
        ban.setReason("uchwała ZK o zakazie przyjmowania (art. 9 ust. 5)");
        ban.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        ban.setEffectiveUntil(LocalDate.of(2026, 6, 1));
        admissionBanRepository.saveAndFlush(ban);

        assertThatThrownBy(() -> {
            TAdmissionBan broken = new TAdmissionBan();
            broken.setDecisionSignature("ZK/BAN/78/26");
            broken.setReason("uchwała ZK o zakazie przyjmowania (art. 9 ust. 5)");
            broken.setEffectiveFrom(LocalDate.of(2026, 7, 1));
            broken.setEffectiveUntil(LocalDate.of(2026, 6, 1));
            admissionBanRepository.saveAndFlush(broken);
        }).isInstanceOf(DataAccessException.class);
    }

    @Test
    void admissionBanPeriodIsBoundedTo365DaysAndReasonIsMandatory() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        // art. 9 ust. 5: "na okres maksymalnie 365 dni" — the 365th day still fits, the 366th does not
        TAdmissionBan maximal = new TAdmissionBan();
        maximal.setFormalIdentifier("29999954321");
        maximal.setDecisionSignature("ZK/BAN/81/26");
        maximal.setReason("uchwała ZK o zakazie przyjmowania (art. 9 ust. 5)");
        maximal.setEffectiveFrom(from);
        maximal.setEffectiveUntil(from.plusDays(365));
        admissionBanRepository.saveAndFlush(maximal);

        assertSqlState(CHECK_VIOLATION, () -> {
            TAdmissionBan tooLong = new TAdmissionBan();
            tooLong.setFormalIdentifier("29999954322");
            tooLong.setDecisionSignature("ZK/BAN/82/26");
            tooLong.setReason("uchwała ZK o zakazie przyjmowania (art. 9 ust. 5)");
            tooLong.setEffectiveFrom(from);
            tooLong.setEffectiveUntil(from.plusDays(366));
            admissionBanRepository.saveAndFlush(tooLong);
        });

        assertSqlState(CHECK_VIOLATION, () -> {
            TAdmissionBan withoutReason = new TAdmissionBan();
            withoutReason.setFormalIdentifier("29999954323");
            withoutReason.setDecisionSignature("ZK/BAN/83/26");
            withoutReason.setReason("   ");
            withoutReason.setEffectiveFrom(from);
            withoutReason.setEffectiveUntil(from.plusDays(30));
            admissionBanRepository.saveAndFlush(withoutReason);
        });
    }

    @Test
    void memberNumberAllocationNeverEntersTheReservedTestBand() {
        assertThat(jdbc.queryForObject(
            "SELECT seqmin FROM pg_sequence WHERE seqrelid = 'seq_member_number_allocation'::regclass",
            Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT seqmax FROM pg_sequence WHERE seqrelid = 'seq_member_number_allocation'::regclass",
            Long.class)).isEqualTo(949999);

        jdbc.queryForObject("SELECT setval('seq_member_number_allocation', 949998)", Long.class);
        assertThat(jdbc.queryForObject("SELECT nextval('seq_member_number_allocation')", Long.class))
            .isEqualTo(949999);

        assertSqlState(SEQUENCE_LIMIT, () -> jdbc.queryForObject(
            "SELECT nextval('seq_member_number_allocation')", Long.class));

        jdbc.queryForObject("SELECT setval('seq_member_number_allocation', 1, false)", Long.class);
    }

    private TRecruitmentCase newCase(String email) {
        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setDistrict(district("mazowieckie"));
        candidateCase.setCandidateFirstName("Kolendra");
        candidateCase.setCandidateLastName("Kowalska");
        candidateCase.setCandidateEmail(email);
        return caseRepository.saveAndFlush(candidateCase);
    }

    /**
     * A case that reached the assessment stage: declaration recorded, statutory clock set,
     * ZO recommendation entered with its resolution deadline.
     */
    private TRecruitmentCase dressedCase(TRecruitmentCase candidateCase) {
        LocalDate received = LocalDate.now().minusDays(63);
        candidateCase.setStatus(RecruitmentCaseStatus.DECLARATION_RECEIVED);
        candidateCase.setDeclarationForm(DeclarationForm.ELECTRONIC);
        candidateCase.setDeclarationReference("DEKL-9501/26");
        candidateCase.setDeclarationReceivedOn(received);
        candidateCase.setAssessmentNotBeforeOn(received.plusDays(62));
        candidateCase.setAssessmentNotLaterOn(received.plusDays(76));
        candidateCase.setZoRecommendedOn(LocalDate.now());
        candidateCase.setAdmissionResolutionNotLaterOn(LocalDate.now().plusDays(7));
        candidateCase.setCandidateBirthDate(LocalDate.of(2007, 4, 1));
        return candidateCase;
    }

    private void saveDecision(
        TRecruitmentCase candidateCase,
        RecruitmentDecisionKind kind,
        long decidingBodyId,
        String signature
    ) {
        TRecruitmentDecision decision = new TRecruitmentDecision();
        decision.setRecruitmentCase(candidateCase);
        decision.setDecidingBody(bodyRepository.findById(decidingBodyId).orElseThrow());
        decision.setDecisionKind(kind);
        decision.setDecisionSignature(signature);
        decision.setDecidedOn(LocalDate.now());
        decisionRepository.saveAndFlush(decision);
    }

    private long acceptedMemberId() {
        return newMember("Ola", "Przyjeta", "mazowieckie").getId();
    }

    private void insertCase(String email) {
        jdbc.update("INSERT INTO recruitment_cases (district_body_id, candidate_first_name, candidate_last_name, "
            + "candidate_email) VALUES (?, 'Kolendra', 'Kowalska', ?)", district("mazowieckie").getId(), email);
    }

    private static void assertSqlState(String expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(DataAccessException.class)
            .satisfies(e -> {
                Throwable cause = ((DataAccessException) e).getMostSpecificCause();
                assertThat(cause).isInstanceOf(SQLException.class);
                assertThat(((SQLException) cause).getSQLState()).isEqualTo(expected);
            });
    }
}
