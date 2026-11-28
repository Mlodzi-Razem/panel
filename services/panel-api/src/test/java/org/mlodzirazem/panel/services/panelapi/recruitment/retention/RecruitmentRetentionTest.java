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

package org.mlodzirazem.panel.services.panelapi.recruitment.retention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationArtifactKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationSignatureType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentDecisionKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TDeclarationArtifact;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TDeclarationArtifactRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecision;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecisionRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseAnonymized;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseService;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationArtifactStore;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationProperties;
import org.mlodzirazem.panel.services.panelapi.recruitment.retention.RecruitmentRetention.RetentionReport;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@PostgresTest
class RecruitmentRetentionTest extends DomainTestSupport {
    private static final String PII_MARK = "Anonimizowalna";

    @Autowired
    RecruitmentRetention retention;

    @Autowired
    RecruitmentCaseService recruitmentCases;

    @Autowired
    TRecruitmentCaseRepository cases;

    @Autowired
    TRecruitmentCaseEventRepository caseEvents;

    @Autowired
    TRecruitmentDecisionRepository decisions;

    @Autowired
    TDeclarationArtifactRepository artifacts;

    @Autowired
    DeclarationProperties declarationProperties;

    @Autowired
    Clock clock;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    CapturedEvents captured;

    @MockitoSpyBean
    DeclarationArtifactStore store;

    private ZonedDateTime now;

    private int sequence;

    @TestConfiguration
    static class CapturedEventsConfiguration {
        @Bean
        CapturedEvents capturedEvents() {
            return new CapturedEvents();
        }
    }

    static class CapturedEvents {
        final List<RecruitmentCaseAnonymized> afterCommit = new CopyOnWriteArrayList<>();

        // AFTER_COMMIT semantics without @TransactionalEventListener: Spring Modulith would route that listener
        // through its JDBC event registry (table event_publication, absent in this test schema)
        @EventListener
        void on(RecruitmentCaseAnonymized event) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        afterCommit.add(event);
                    }
                });
            } else {
                afterCommit.add(event);
            }
        }

        // accessor, not the field: the bean may be a proxy whose own fields are not initialised
        List<RecruitmentCaseAnonymized> afterCommit() {
            return afterCommit;
        }
    }

    @DynamicPropertySource
    static void declarationStorage(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("retention-declarations-test");
        registry.add("panel.api.recruitment.declaration.storage-dir", storage::toString);
    }

    @BeforeEach
    void freshState() {
        now = ZonedDateTime.now(clock);
        captured.afterCommit().clear();
    }

    // ---- boundaries of the artifact purge: 89 / 90 / 91 days -------------------------------------------------

    @Test
    void artifactsAreKeptAt89DaysAndPurgedFrom90() {
        TRecruitmentCase at89 = rejectedCase(89);
        TRecruitmentCase at90 = rejectedCase(90);
        TRecruitmentCase at91 = rejectedCase(91);

        RetentionReport report = retention.runAt(now);

        assertThat(report.casesWithArtifactsPurged()).isEqualTo(2);
        assertThat(report.artifactsPurged()).isEqualTo(4);
        assertThat(report.casesAnonymized()).isZero();
        assertThat(report.failures()).isZero();

        assertThat(artifactsPurged(at89)).isFalse();
        assertThat(artifactsPurged(at90)).isTrue();
        assertThat(artifactsPurged(at91)).isTrue();

        // purged: files gone, rows keep the trace but neither the file reference nor the signer's name
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM declaration_artifacts WHERE case_id = ? AND purged_at IS NOT NULL"
                + " AND storage_ref IS NULL AND signer_name IS NULL AND content_sha256 IS NOT NULL",
            Integer.class, at90.getId())).isEqualTo(2);
        assertThat(filesOf(at90)).isEmpty();
        assertThat(filesOf(at89)).hasSize(2);

        // the personal data is untouched until the case itself is due
        TRecruitmentCase reloaded = cases.findById(at90.getId()).orElseThrow();
        assertThat(reloaded.getCandidateFirstName()).isEqualTo(PII_MARK);
        assertThat(reloaded.getCandidateFormalIdentifier()).isNotNull();
        assertThat(reloaded.getAnonymizedAt()).isNull();
        assertThat(reloaded.getArtifactsPurgedAt()).isNotNull();
    }

    // ---- boundaries of the anonymization: 364 / 365 / 366 days -------------------------------------------------

    @Test
    void caseIsKeptAt364DaysAndAnonymizedFrom365() {
        TRecruitmentCase at364 = rejectedCase(364);
        TRecruitmentCase at365 = rejectedCase(365);
        TRecruitmentCase at366 = rejectedCase(366);

        RetentionReport report = retention.runAt(now);

        assertThat(report.casesAnonymized()).isEqualTo(2);
        assertThat(report.failures()).isZero();

        assertThat(cases.findById(at364.getId()).orElseThrow().getAnonymizedAt()).isNull();
        assertThat(cases.findById(at364.getId()).orElseThrow().getCandidateFormalIdentifier()).isNotNull();

        for (TRecruitmentCase anonymized : List.of(at365, at366)) {
            TRecruitmentCase reloaded = cases.findById(anonymized.getId()).orElseThrow();

            assertThat(reloaded.getAnonymizedAt()).isNotNull();
            assertThat(reloaded.getArtifactsPurgedAt()).isNotNull();
            assertThat(reloaded.getCandidateFirstName()).isNull();
            assertThat(reloaded.getCandidateLastName()).isNull();
            assertThat(reloaded.getCandidateEmail()).isNull();
            assertThat(reloaded.getCandidatePhone()).isNull();
            assertThat(reloaded.getCandidateSocialProfileUrl()).isNull();
            assertThat(reloaded.getCandidateBirthDate()).isNull();
            assertThat(reloaded.getCandidateFormalFirstName()).isNull();
            assertThat(reloaded.getCandidateFormalLastName()).isNull();
            assertThat(reloaded.getCandidatePreferredFirstName()).isNull();
            assertThat(reloaded.getCandidatePreferredLastName()).isNull();
            assertThat(reloaded.getCandidatePronouns()).isNull();
            assertThat(reloaded.getCandidateFormalIdentifier()).isNull();

            // the non-personal skeleton of the case stays
            assertThat(reloaded.getStatus()).isEqualTo(RecruitmentCaseStatus.REJECTED);
            assertThat(reloaded.getRejectionReason()).isEqualTo(RecruitmentRejectionReason.NOT_FIT);
            assertThat(reloaded.getClosedAt()).isNotNull();
            assertThat(reloaded.getDistrict().getId()).isEqualTo(district("mazowieckie").getId());
            assertThat(reloaded.getTrialAccountNumber()).isNotNull();
            assertThat(filesOf(anonymized)).isEmpty();
        }
    }

    // ---- open, accepted and unrelated cases stay untouched -----------------------------------------------------

    @Test
    void openAndAcceptedCasesAreNeverTouched() {
        TRecruitmentCase open = openCase(2000);
        long accepted = acceptedCase();

        RetentionReport report = retention.runAt(now.plusYears(10));

        assertThat(report.changedNothing()).isTrue();

        TRecruitmentCase reloaded = cases.findById(open.getId()).orElseThrow();
        assertThat(reloaded.getCandidateFirstName()).isEqualTo(PII_MARK);
        assertThat(reloaded.getAnonymizedAt()).isNull();
        assertThat(reloaded.getArtifactsPurgedAt()).isNull();

        assertThat(jdbc.queryForObject(
            "SELECT candidate_first_name FROM recruitment_cases WHERE id = ?", String.class, accepted))
            .isEqualTo(PII_MARK);
        assertThat(jdbc.queryForObject(
            "SELECT anonymized_at IS NULL AND artifacts_purged_at IS NULL FROM recruitment_cases WHERE id = ?",
            Boolean.class, accepted)).isTrue();
        assertThat(artifactsPurged(open)).isFalse();
        assertThat(captured.afterCommit()).isEmpty();
    }

    // ---- history stays, only possible-PII free text is redacted ------------------------------------------------

    @Test
    void eventsAndDecisionsStayAndFreeTextIsRedacted() {
        TRecruitmentCase target = rejectedCase(400);
        List<String> before = eventTypes(target);
        long decisionCount = decisions.findAllByRecruitmentCaseId(target.getId()).size();

        retention.runAt(now);

        List<String> after = eventTypes(target);
        assertThat(after).startsWith(before.toArray(String[]::new));
        assertThat(after.subList(before.size(), after.size())).containsExactly("CASE_ANONYMIZED");

        assertThat(decisions.findAllByRecruitmentCaseId(target.getId())).hasSize((int) decisionCount)
            .allSatisfy(decision -> {
                assertThat(decision.getNote()).isNull();
                assertThat(decision.getDecisionSignature()).isEqualTo("UCH/1");
            });

        String payloads = String.join("\n", jdbc.queryForList(
            "SELECT payload::text FROM recruitment_case_events WHERE case_id = ? AND event_type ="
                + " 'CANDIDATE_DATA_CORRECTED'", String.class, target.getId()));
        assertThat(payloads).contains("candidate_last_name").contains("reasonRedacted")
            .doesNotContain("reason\":").doesNotContain("Nazwisko Sekretne");
    }

    // ---- publication after commit only -----------------------------------------------------------------------

    @Test
    void eventIsPublishedAfterCommitWithTheTrialAccountNumber() {
        TRecruitmentCase target = rejectedCase(400);

        retention.runAt(now);

        assertThat(captured.afterCommit()).containsExactly(
            new RecruitmentCaseAnonymized(target.getId(), target.getTrialAccountNumber()));
    }

    @Test
    void nothingIsPublishedOrChangedWhenTheTransactionRollsBack() {
        TRecruitmentCase target = rejectedCase(400);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RetentionReport report = retention.runAt(now);

            assertThat(report.casesAnonymized()).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(captured.afterCommit()).isEmpty();

        TRecruitmentCase reloaded = cases.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getAnonymizedAt()).isNull();
        assertThat(reloaded.getCandidateFirstName()).isEqualTo(PII_MARK);
        assertThat(auditCount("recruitment :: anonymize")).isZero();
        assertThat(eventTypes(target)).doesNotContain("CASE_ANONYMIZED");
    }

    // ---- idempotency, audit, batching, isolation ---------------------------------------------------------------

    @Test
    void secondPassChangesNothingAndTheAuditIsNamesOnly() {
        TRecruitmentCase target = rejectedCase(400);

        RetentionReport first = retention.runAt(now);
        long audits = auditCount("recruitment :: anonymize");
        int events = eventTypes(target).size();

        RetentionReport second = retention.runAt(now);

        assertThat(first.casesAnonymized()).isEqualTo(1);
        assertThat(second.changedNothing()).isTrue();
        assertThat(auditCount("recruitment :: anonymize")).isEqualTo(audits).isEqualTo(1);
        assertThat(eventTypes(target)).hasSize(events);
        assertThat(captured.afterCommit()).hasSize(1);

        String payloads = auditPayloads("recruitment :: anonymize");
        assertThat(payloads).contains("candidate_formal_identifier").contains("ANONYMIZED")
            .doesNotContain(PII_MARK).doesNotContain("Nazwisko Sekretne").doesNotContain("@example.org")
            .doesNotContain("ID-");
    }

    @Test
    void artifactPurgeThenAnonymizationAreSeparateAuditedSteps() {
        TRecruitmentCase target = rejectedCase(100);

        retention.runAt(now);
        assertThat(auditPayloads("recruitment :: anonymize")).contains("ARTIFACTS_PURGED");
        assertThat(eventTypes(target)).contains("ARTIFACTS_PURGED");

        RetentionReport later = retention.runAt(now.plusDays(265));

        assertThat(later.casesAnonymized()).isEqualTo(1);
        assertThat(later.casesWithArtifactsPurged()).isZero();
        assertThat(cases.findById(target.getId()).orElseThrow().getAnonymizedAt()).isNotNull();
    }

    @Test
    void manyCasesAreProcessedInBatches() {
        for (int index = 0; index < 5; index++) {
            rejectedCase(500 + index);
        }

        RetentionReport report = retention.runAt(now);

        assertThat(report.casesAnonymized()).isEqualTo(5);
        assertThat(captured.afterCommit()).hasSize(5);
    }

    @Test
    void aFailingCaseRollsBackAloneAndTheOthersGoOn() {
        TRecruitmentCase bad = rejectedCase(400);
        TRecruitmentCase good = rejectedCase(401);
        String badRef = filesOf(bad).getFirst();

        doThrow(new java.io.UncheckedIOException("boom", new IOException("disk")))
            .when(store).delete(eq(badRef));

        RetentionReport report = retention.runAt(now);

        assertThat(report.failures()).isEqualTo(1);
        assertThat(report.casesAnonymized()).isEqualTo(1);

        TRecruitmentCase reloadedBad = cases.findById(bad.getId()).orElseThrow();
        assertThat(reloadedBad.getAnonymizedAt()).isNull();
        assertThat(reloadedBad.getCandidateFirstName()).isEqualTo(PII_MARK);
        assertThat(artifactsPurged(bad)).isFalse();
        assertThat(cases.findById(good.getId()).orElseThrow().getAnonymizedAt()).isNotNull();
        assertThat(captured.afterCommit()).extracting(RecruitmentCaseAnonymized::caseId)
            .containsExactly(good.getId());
    }

    // ---- shared content-addressed files, bans, closed-case rules -----------------------------------------------

    @Test
    void aFileSharedWithAnotherCaseSurvivesUntilTheLastReferenceIsPurged() throws IOException {
        TRecruitmentCase older = rejectedCase(100);
        TRecruitmentCase younger = rejectedCase(50);
        String shared = store.save("identical scan".getBytes(StandardCharsets.UTF_8));

        for (TRecruitmentCase target : List.of(older, younger)) {
            TDeclarationArtifact artifact = artifact(target, DeclarationArtifactKind.SCAN, shared);
            artifacts.saveAndFlush(artifact);
        }

        retention.runAt(now);

        assertThat(artifactsPurged(older)).isTrue();
        assertThat(Files.exists(Path.of(declarationProperties.getStorageDir(), shared))).isTrue();

        retention.runAt(now.plusDays(40));

        assertThat(artifactsPurged(younger)).isTrue();
        assertThat(Files.exists(Path.of(declarationProperties.getStorageDir(), shared))).isFalse();
    }

    @Test
    void anAdmissionBanOutlivesTheAnonymizedCase() {
        TRecruitmentCase target = rejectedCase(400);
        jdbc.update("INSERT INTO admission_bans (case_id, formal_identifier, decision_signature, reason,"
                + " effective_from, effective_until) VALUES (?, ?, 'ZK/9', 'uchwała testowa', ?, ?)",
            target.getId(), "ID-" + target.getId(),
            now.toLocalDate().minusDays(10), now.toLocalDate().plusDays(100));

        retention.runAt(now);

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM admission_bans WHERE case_id = ? AND formal_identifier IS NOT NULL",
            Integer.class, target.getId())).isEqualTo(1);
    }

    @Test
    void anAnonymizedCaseAcceptsNoNotes() {
        TMember operator = newMember("Halina", "Kordynatorka", "mazowieckie");
        TRecruitmentCase target = rejectedCase(400);

        retention.runAt(now);

        assertThatThrownBy(() -> recruitmentCases.addNote(target.getId(), "zapis", operator.getId()))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("CASE_ANONYMIZED");
    }

    @Test
    void dueDatesFollowTheConfiguredPeriods() {
        LocalDate closed = LocalDate.of(2026, 1, 10);

        assertThat(retention.artifactsDueOn(closed)).isEqualTo(closed.plusDays(90));
        assertThat(retention.caseDueOn(closed)).isEqualTo(closed.plusDays(365));
    }

    @Test
    void artifactsMustNotOutliveTheCase() {
        RetentionProperties properties = new RetentionProperties();
        properties.setArtifactDays(400);

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        properties.setArtifactDays(0);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    // ---- fixtures ----------------------------------------------------------------------------------------------

    private TRecruitmentCase rejectedCase(int closedDaysAgo) {
        TRecruitmentCase target = baseCase("ID-R", closedDaysAgo);
        target.setStatus(RecruitmentCaseStatus.REJECTED);
        target.setRejectionReason(RecruitmentRejectionReason.NOT_FIT);
        target.setClosedAt(now.toLocalDate().minusDays(closedDaysAgo));
        target.setTrialAccountNumber(990000L + sequence);
        target = cases.saveAndFlush(target);

        withArtifacts(target);
        withHistory(target);
        return target;
    }

    private TRecruitmentCase openCase(int createdDaysAgo) {
        TRecruitmentCase target = baseCase("ID-O", createdDaysAgo);
        target.setStatus(RecruitmentCaseStatus.IN_PROGRESS);
        target = cases.saveAndFlush(target);

        withArtifacts(target);
        return target;
    }

    private TRecruitmentCase baseCase(String identifierPrefix, int tag) {
        sequence++;

        TRecruitmentCase target = new TRecruitmentCase();
        target.setDistrict(district("mazowieckie"));
        target.setCandidateFirstName(PII_MARK);
        target.setCandidateLastName("Nazwisko Sekretne");
        target.setCandidateEmail("kandydat" + sequence + "@example.org");
        target.setCandidatePhone("+48 600 000 " + String.format("%03d", sequence));
        target.setCandidateSocialProfileUrl("https://example.org/profil/" + sequence);
        target.setCandidateBirthDate(LocalDate.of(2007, 4, 1));
        target.setCandidateFormalFirstName("Formalna");
        target.setCandidateFormalLastName("Formalska");
        target.setCandidatePreferredFirstName("Prefa");
        target.setCandidatePreferredLastName("Prefowska");
        target.setCandidatePronouns("ona/jej");
        target.setCandidateFormalIdentifier(identifierPrefix + sequence + "-" + tag);
        return target;
    }

    private void withArtifacts(TRecruitmentCase target) {
        for (DeclarationArtifactKind kind : List.of(DeclarationArtifactKind.TEMPLATE_PDF,
            DeclarationArtifactKind.SCAN)) {
            String ref = store.save(("artifact " + kind + " of " + target.getId()).getBytes(StandardCharsets.UTF_8));
            artifacts.saveAndFlush(artifact(target, kind, ref));
        }
    }

    private TDeclarationArtifact artifact(TRecruitmentCase target, DeclarationArtifactKind kind, String ref) {
        TDeclarationArtifact artifact = new TDeclarationArtifact();
        artifact.setRecruitmentCase(target);
        artifact.setArtifactKind(kind);
        artifact.setContentSha256("sha-" + ref);
        artifact.setStorageRef(ref);

        if (kind != DeclarationArtifactKind.TEMPLATE_PDF) {
            artifact.setSignatureType(DeclarationSignatureType.SCAN);
            artifact.setSignerName("Jan Podpisany");
            artifact.setValidatedAt(now);
        }

        return artifact;
    }

    private void withHistory(TRecruitmentCase target) {
        TRecruitmentCaseEvent created = event(target, RecruitmentCaseEventType.CASE_CREATED,
            "{\"fields\": [\"candidateFirstName\"]}");
        TRecruitmentCaseEvent corrected = event(target, RecruitmentCaseEventType.CANDIDATE_DATA_CORRECTED,
            "{\"fields\": [\"candidate_last_name\"], \"reason\": \"Nazwisko Sekretne było błędnie zapisane\"}");
        caseEvents.saveAllAndFlush(List.of(created, corrected));

        TRecruitmentDecision decision = new TRecruitmentDecision();
        decision.setRecruitmentCase(target);
        decision.setDecidingBody(board("mazowieckie"));
        decision.setDecisionKind(RecruitmentDecisionKind.REJECTION);
        decision.setDecisionSignature("UCH/1");
        decision.setDecidedOn(target.getClosedAt());
        decision.setNote("uwaga o Nazwisko Sekretne");
        decisions.saveAndFlush(decision);
    }

    private TRecruitmentCaseEvent event(TRecruitmentCase target, RecruitmentCaseEventType type, String payload) {
        TRecruitmentCaseEvent event = new TRecruitmentCaseEvent();
        event.setRecruitmentCase(target);
        event.setEventType(type);
        event.setPayload(payload);
        return event;
    }

    /** ACCEPTED needs both collegial decisions (schema trigger) and a member row; closed two years ago. */
    private long acceptedCase() {
        TMember member = newMember("Przyjeta", "Czlonkini", "mazowieckie");
        LocalDate received = now.toLocalDate().minusDays(900);

        long id = jdbc.queryForObject("""
            INSERT INTO recruitment_cases (district_body_id, candidate_first_name, candidate_last_name, status,
                declaration_form, declaration_received_on, assessment_not_before_on, assessment_not_later_on,
                zo_recommended_on, admission_resolution_not_later_on)
            VALUES (?, ?, 'Przyjeta', 'ZK_VOTING', 'SCAN', ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, district("mazowieckie").getId(), PII_MARK, received, received.plusDays(62),
            received.plusDays(76), received.plusDays(70), received.plusDays(77));

        for (String kind : List.of("ZK_APPROVAL", "ZO_ADMISSION_RESOLUTION")) {
            jdbc.update("INSERT INTO recruitment_decisions (case_id, deciding_body_id, decision_kind,"
                    + " decision_signature, decided_on, note) VALUES (?, ?, CAST(? AS recruitment_decision_kind),"
                    + " 'UCH/A', ?, 'notatka')", id, board("mazowieckie").getId(), kind, received.plusDays(80));
        }

        jdbc.update("UPDATE recruitment_cases SET status = 'ACCEPTED', accepted_on = ?, accepted_member_id = ?,"
                + " closed_at = ? WHERE id = ?", received.plusDays(81), member.getId(), received.plusDays(81), id);

        return id;
    }

    private boolean artifactsPurged(TRecruitmentCase target) {
        Integer unpurged = jdbc.queryForObject(
            "SELECT COUNT(*) FROM declaration_artifacts WHERE case_id = ? AND purged_at IS NULL", Integer.class,
            target.getId());

        return unpurged != null && unpurged == 0
            && cases.findById(target.getId()).orElseThrow().getArtifactsPurgedAt() != null;
    }

    private List<String> filesOf(TRecruitmentCase target) {
        return jdbc.queryForList("SELECT storage_ref FROM declaration_artifacts WHERE case_id = ?"
                + " AND storage_ref IS NOT NULL ORDER BY id", String.class, target.getId()).stream()
            .filter(ref -> Files.exists(Path.of(declarationProperties.getStorageDir(), ref)))
            .toList();
    }

    private List<String> eventTypes(TRecruitmentCase target) {
        return caseEvents.findAllByRecruitmentCaseIdOrderByCreatedAtAscIdAsc(target.getId()).stream()
            .map(event -> event.getEventType().name()).toList();
    }
}
