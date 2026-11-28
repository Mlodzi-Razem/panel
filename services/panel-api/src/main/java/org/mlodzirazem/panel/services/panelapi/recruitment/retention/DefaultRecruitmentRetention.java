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

import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TDeclarationArtifact;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TDeclarationArtifactRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecisionRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseAnonymized;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.DeclarationArtifactStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The retention job. A case is one transaction: the lock, a re-check of the due date, the purge or wipe, the
 * timeline event, the audit row and (for an anonymization) the domain event commit or roll back together.
 * A failing case is counted and skipped; the next case and the next day's pass go on (idempotent: a purged or
 * anonymized case is never selected again). Nothing here logs a value — ids and counters only.
 */
@Service
class DefaultRecruitmentRetention implements RecruitmentRetention {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecruitmentRetention.class);

    static final String AUDIT_OPERATION = "recruitment :: anonymize";

    /** Case columns that carry the candidate's personal data (wiped at anonymization). */
    static final List<String> CASE_PII_COLUMNS = List.of(
        "candidate_first_name", "candidate_last_name", "candidate_email", "candidate_phone",
        "candidate_social_profile_url", "candidate_birth_date", "candidate_formal_first_name",
        "candidate_formal_last_name", "candidate_preferred_first_name", "candidate_preferred_last_name",
        "candidate_pronouns", "candidate_formal_identifier");

    private final TRecruitmentCaseRepository cases;
    private final TDeclarationArtifactRepository artifacts;
    private final TRecruitmentCaseEventRepository caseEvents;
    private final TRecruitmentDecisionRepository decisions;
    private final DeclarationArtifactStore artifactStore;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final RetentionProperties properties;
    private final TransactionTemplate transaction;

    DefaultRecruitmentRetention(
        TRecruitmentCaseRepository cases,
        TDeclarationArtifactRepository artifacts,
        TRecruitmentCaseEventRepository caseEvents,
        TRecruitmentDecisionRepository decisions,
        DeclarationArtifactStore artifactStore,
        AuditLog auditLog,
        ApplicationEventPublisher publisher,
        JsonMapper jsonMapper,
        Clock clock,
        RetentionProperties properties,
        PlatformTransactionManager transactionManager
    ) {
        this.cases = cases;
        this.artifacts = artifacts;
        this.caseEvents = caseEvents;
        this.decisions = decisions;
        this.artifactStore = artifactStore;
        this.auditLog = auditLog;
        this.publisher = publisher;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public RetentionReport run() {
        return runAt(ZonedDateTime.now(clock));
    }

    @Override
    public RetentionReport runAt(ZonedDateTime now) {
        LocalDate today = now.withZoneSameInstant(clock.getZone()).toLocalDate();

        int anonymized = 0;
        int casesPurged = 0;
        int artifactsPurged = 0;
        int failures = 0;
        Set<Long> failed = new HashSet<>();

        // anonymization first: a case due for both is wiped (and its artifacts purged) in one step
        long after = 0;
        List<Long> batch;

        do {
            batch = cases.findIdsDueForAnonymization(today.minusDays(properties.getRejectedCaseDays()), after,
                Pageable.ofSize(properties.getBatchSize()));

            for (long caseId : batch) {
                after = caseId;

                try {
                    if (Boolean.TRUE.equals(transaction.execute(_ -> anonymize(caseId, now, today)))) {
                        anonymized++;
                    }
                } catch (RuntimeException failure) {
                    failures++;
                    failed.add(caseId);
                    logFailure("anonymize", caseId, failure);
                }
            }
        } while (!batch.isEmpty());

        after = 0;

        do {
            batch = cases.findIdsDueForArtifactPurge(today.minusDays(properties.getArtifactDays()), after,
                Pageable.ofSize(properties.getBatchSize()));

            for (long caseId : batch) {
                after = caseId;

                if (failed.contains(caseId)) {
                    continue; // already failed in this pass: counted once, retried by the next pass
                }

                try {
                    Integer purged = transaction.execute(_ -> purgeArtifactsOfCase(caseId, now, today));

                    if (purged != null && purged >= 0) {
                        casesPurged++;
                        artifactsPurged += purged;
                    }
                } catch (RuntimeException failure) {
                    failures++;
                    logFailure("purge", caseId, failure);
                }
            }
        } while (!batch.isEmpty());

        return new RetentionReport(anonymized, casesPurged, artifactsPurged, failures);
    }

    @Override
    public LocalDate artifactsDueOn(LocalDate closedAt) {
        return closedAt.plusDays(properties.getArtifactDays());
    }

    @Override
    public LocalDate caseDueOn(LocalDate closedAt) {
        return closedAt.plusDays(properties.getRejectedCaseDays());
    }

    /** Returns true when the case was anonymized, false when it turned out not to be due (re-check under lock). */
    private boolean anonymize(long caseId, ZonedDateTime now, LocalDate today) {
        TRecruitmentCase candidateCase = cases.findByIdForUpdate(caseId).orElse(null);

        if (candidateCase == null || !isRejectedAndClosedFor(candidateCase, properties.getRejectedCaseDays(), today)
            || candidateCase.getAnonymizedAt() != null) {
            return false;
        }

        int artifactCount = 0;

        if (candidateCase.getArtifactsPurgedAt() == null) {
            artifactCount = purgeArtifacts(candidateCase, now);
            candidateCase.setArtifactsPurgedAt(now);
        }

        // free text typed by case managers can quote the candidate: redact it, the rows themselves stay
        int redactedCorrections = caseEvents.redactCorrectionReasons(caseId);
        int clearedDecisionNotes = decisions.clearNotes(caseId);

        candidateCase.setCandidateFirstName(null);
        candidateCase.setCandidateLastName(null);
        candidateCase.setCandidateEmail(null);
        candidateCase.setCandidatePhone(null);
        candidateCase.setCandidateSocialProfileUrl(null);
        candidateCase.setCandidateBirthDate(null);
        candidateCase.setCandidateFormalFirstName(null);
        candidateCase.setCandidateFormalLastName(null);
        candidateCase.setCandidatePreferredFirstName(null);
        candidateCase.setCandidatePreferredLastName(null);
        candidateCase.setCandidatePronouns(null);
        candidateCase.setCandidateFormalIdentifier(null);
        candidateCase.setAnonymizedAt(now);
        cases.saveAndFlush(candidateCase);

        appendEvent(candidateCase, RecruitmentCaseEventType.CASE_ANONYMIZED, CASE_PII_COLUMNS);

        auditLog.record(AUDIT_OPERATION, AuditPayload.of("caseId", caseId)
            .withId("artifacts", artifactCount)
            .withId("redactedCorrections", redactedCorrections)
            .withId("clearedDecisionNotes", clearedDecisionNotes)
            .withFields(CASE_PII_COLUMNS)
            .withOutcome("ANONYMIZED"));

        // inside the transaction: listeners run after commit, a rollback publishes nothing
        publisher.publishEvent(new RecruitmentCaseAnonymized(caseId, candidateCase.getTrialAccountNumber()));

        return true;
    }

    /** Returns the number of purged artifacts, or -1 when the case turned out not to be due. */
    private int purgeArtifactsOfCase(long caseId, ZonedDateTime now, LocalDate today) {
        TRecruitmentCase candidateCase = cases.findByIdForUpdate(caseId).orElse(null);

        if (candidateCase == null || !isRejectedAndClosedFor(candidateCase, properties.getArtifactDays(), today)
            || candidateCase.getAnonymizedAt() != null || candidateCase.getArtifactsPurgedAt() != null) {
            return -1;
        }

        int artifactCount = purgeArtifacts(candidateCase, now);
        candidateCase.setArtifactsPurgedAt(now);
        cases.saveAndFlush(candidateCase);

        appendEvent(candidateCase, RecruitmentCaseEventType.ARTIFACTS_PURGED, List.of("declaration_artifacts"));

        auditLog.record(AUDIT_OPERATION, AuditPayload.of("caseId", caseId)
            .withId("artifacts", artifactCount)
            .withFields(List.of("declaration_artifacts"))
            .withOutcome("ARTIFACTS_PURGED"));

        return artifactCount;
    }

    /**
     * Detaches the artifact rows from their files and the signer's name, then removes the files that no other
     * artifact row still uses (files are content-addressed). The files go last, inside the transaction: a failed
     * deletion rolls the case back and the next pass retries; a missing file counts as deleted.
     */
    private int purgeArtifacts(TRecruitmentCase candidateCase, ZonedDateTime now) {
        List<TDeclarationArtifact> stored = artifacts.findAllByRecruitmentCaseId(candidateCase.getId()).stream()
            .filter(artifact -> artifact.getPurgedAt() == null)
            .toList();

        List<String> files = new ArrayList<>();
        List<TDeclarationArtifact> purged = new ArrayList<>();

        for (TDeclarationArtifact artifact : stored) {
            String ref = artifact.getStorageRef();

            if (ref != null) {
                files.add(ref);
            }

            artifact.setStorageRef(null);
            artifact.setSignerName(null);
            artifact.setPurgedAt(now);
            purged.add(artifact);
        }

        artifacts.saveAllAndFlush(purged);

        for (String ref : files) {
            if (!artifacts.existsByStorageRefAndPurgedAtIsNull(ref)) {
                artifactStore.delete(ref);
            }
        }

        return purged.size();
    }

    private static boolean isRejectedAndClosedFor(TRecruitmentCase candidateCase, int days, LocalDate today) {
        LocalDate closedAt = candidateCase.getClosedAt();

        return candidateCase.getStatus() == RecruitmentCaseStatus.REJECTED
            && closedAt != null
            && !closedAt.plusDays(days).isAfter(today);
    }

    private void appendEvent(TRecruitmentCase candidateCase, RecruitmentCaseEventType type, List<String> fields) {
        ObjectNode payload = jsonMapper.createObjectNode();
        ArrayNode names = payload.putArray("fields");
        fields.forEach(names::add);

        TRecruitmentCaseEvent event = new TRecruitmentCaseEvent();
        event.setRecruitmentCase(candidateCase);
        event.setEventType(type);
        event.setPayload(jsonMapper.writeValueAsString(payload));
        caseEvents.saveAndFlush(event);
    }

    private static void logFailure(String step, long caseId, RuntimeException failure) {
        // the exception text may quote a database row (constraint details): class name only
        LOG.error("Recruitment retention could not {} case {}: {}", step, caseId, failure.getClass().getName());
    }
}
