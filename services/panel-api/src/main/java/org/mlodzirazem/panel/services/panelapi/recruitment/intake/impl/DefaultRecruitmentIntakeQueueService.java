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

package org.mlodzirazem.panel.services.panelapi.recruitment.intake.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentIntakeDiscardReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentIntakeStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentSource;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentIntake;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentIntakeRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.NewRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCandidateField;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseService;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.IntakeProperties;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.RecruitmentIntakeQueueService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class DefaultRecruitmentIntakeQueueService implements RecruitmentIntakeQueueService {
    private static final List<String> PERSONAL_FIELDS =
        List.of("firstName", "lastName", "email", "phone", "birthDate", "locality");

    private final IntakeProperties properties;
    private final IntakeJournal journal;
    private final TRecruitmentIntakeRepository intakes;
    private final TBodyRepository bodies;
    private final TMemberRepository members;
    private final RecruitmentCaseService cases;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    @Transactional
    public void assignDistrict(long intakeId, long districtBodyId, long actorMemberId) {
        TRecruitmentIntake intake = pending(intakeId);
        TBody district = bodies.findById(districtBodyId)
            .orElseThrow(() -> new NotFoundException("Body", districtBodyId));

        if (!district.isDistrict()) {
            throw new RuleViolationException("DISTRICT_REQUIRED", "A submission is assigned to a district body");
        }

        intake.setDistrict(district);
        touch(intake, actorMemberId);
        journal.record("DISTRICT_ASSIGNED", intakeId, "intake-assign", List.of("districtBodyId"),
            "PENDING", actorMemberId);
    }

    @Override
    @Transactional
    public long accept(long intakeId, long actorMemberId) {
        TRecruitmentIntake intake = pending(intakeId);
        TBody district = intake.getDistrict();

        if (district == null) {
            throw new RuleViolationException("DISTRICT_REQUIRED", "Assign a district before accepting");
        }

        // checked up front: the unique index would also refuse it, but the database error names the address
        if (hasOpenCase(district.getId(), intake.getEmail())) {
            throw new ConflictException("OPEN_CASE_EXISTS",
                "An open case for this e-mail already exists in this district");
        }

        Map<RecruitmentCandidateField, @Nullable Object> candidate = new EnumMap<>(RecruitmentCandidateField.class);
        candidate.put(RecruitmentCandidateField.FIRST_NAME, intake.getFirstName());
        candidate.put(RecruitmentCandidateField.LAST_NAME, intake.getLastName());
        candidate.put(RecruitmentCandidateField.EMAIL, intake.getEmail());
        candidate.put(RecruitmentCandidateField.PHONE, intake.getPhone());
        candidate.put(RecruitmentCandidateField.BIRTH_DATE, intake.getBirthDate());

        long caseId = cases.create(new NewRecruitmentCase(district.getId(), RecruitmentSource.WEBSITE, candidate),
            actorMemberId).getId();

        intake.setCaseId(caseId);
        close(intake, RecruitmentIntakeStatus.ACCEPTED, actorMemberId);
        // erased before the first flush: the schema never holds an accepted submission with data
        intake.erasePersonalData(now());
        intakes.saveAndFlush(intake);
        journal.record("ACCEPTED", intakeId, "intake-accept", List.of("caseId", "status"),
            "ACCEPTED", actorMemberId);
        recordErasure(intake, actorMemberId);

        return caseId;
    }

    @Override
    @Transactional
    public void discard(long intakeId, RecruitmentIntakeDiscardReason reason, long actorMemberId) {
        TRecruitmentIntake intake = pending(intakeId);

        intake.setDiscardReason(reason);
        close(intake, RecruitmentIntakeStatus.DISCARDED, actorMemberId);
        journal.record("DISCARDED", intakeId, "intake-discard", List.of("discardReason", "status"),
            reason.name(), actorMemberId);

        if (reason == RecruitmentIntakeDiscardReason.SPAM || reason == RecruitmentIntakeDiscardReason.WITHDRAWN) {
            eraseNow(intake, actorMemberId);
        }
    }

    @Override
    @Transactional
    public void erase(long intakeId, long actorMemberId) {
        TRecruitmentIntake intake = locked(intakeId);

        if (intake.getStatus() != RecruitmentIntakeStatus.DISCARDED || intake.getDataErasedAt() != null) {
            throw new ConflictException("INTAKE_NOT_ERASABLE",
                "Only a discarded submission that still holds data can be erased");
        }

        touch(intake, actorMemberId);
        eraseNow(intake, actorMemberId);
    }

    /**
     * One transaction for the whole run: the volumes are bounded by the intake limits. Every row is locked and
     * checked again, so a decision taken meanwhile in the back office wins.
     */
    @Override
    @Transactional
    public void applyRetention() {
        ZonedDateTime now = now();

        for (long id : intakes.pendingCreatedBefore(now.minusDays(properties.getPendingRetentionDays()))) {
            TRecruitmentIntake intake = locked(id);

            if (intake.getStatus() == RecruitmentIntakeStatus.PENDING) {
                close(intake, RecruitmentIntakeStatus.EXPIRED, null);
                intake.erasePersonalData(now());
                intakes.saveAndFlush(intake);
                journal.record("EXPIRED", id, "intake-expire", List.of("status"), "EXPIRED", null);
                recordErasure(intake, null);
            }
        }

        for (long id : intakes.discardedNotErasedClosedBefore(
            now.minusDays(properties.getDiscardedRetentionDays()))) {
            TRecruitmentIntake intake = locked(id);

            if (intake.getDataErasedAt() == null) {
                eraseNow(intake, null);
            }
        }

        // slots older than every limit window carry no further meaning
        int keepDays = Math.max(1, properties.getPendingRetentionDays());
        jdbc.update("DELETE FROM recruitment_intake_slots WHERE created_at < ?",
            Timestamp.from(now.toInstant().minus(Duration.ofDays(keepDays + 1L))));
    }

    private void close(TRecruitmentIntake intake, RecruitmentIntakeStatus status, @Nullable Long actorMemberId) {
        intake.setStatus(status);
        intake.setClosedAt(now());

        touch(intake, actorMemberId);
        jdbc.update("UPDATE recruitment_intake_slots SET released_at = ? WHERE intake_id = ? "
            + "AND released_at IS NULL", Timestamp.from(now().toInstant()), intake.getId());
    }

    private void eraseNow(TRecruitmentIntake intake, @Nullable Long actorMemberId) {
        intake.erasePersonalData(now());
        intakes.saveAndFlush(intake);
        recordErasure(intake, actorMemberId);
    }

    private void recordErasure(TRecruitmentIntake intake, @Nullable Long actorMemberId) {
        journal.record("DATA_ERASED", intake.getId(), "intake-erase", PERSONAL_FIELDS,
            intake.getStatus().name(), actorMemberId);
    }

    private boolean hasOpenCase(long districtId, @Nullable String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_cases WHERE district_body_id = ? "
            + "AND closed_at IS NULL AND LOWER(candidate_email) = LOWER(?)", Integer.class, districtId, email);
        return count != null && count > 0;
    }

    private TRecruitmentIntake locked(long intakeId) {
        return intakes.findForUpdate(intakeId)
            .orElseThrow(() -> new NotFoundException("RecruitmentIntake", intakeId));
    }

    private TRecruitmentIntake pending(long intakeId) {
        TRecruitmentIntake intake = locked(intakeId);

        if (intake.getStatus() != RecruitmentIntakeStatus.PENDING) {
            throw new ConflictException("INTAKE_NOT_PENDING", "The submission has already been handled");
        }

        return intake;
    }

    private void touch(TRecruitmentIntake intake, @Nullable Long actorMemberId) {
        if (actorMemberId != null) {
            members.findById(actorMemberId).ifPresent(intake::setUpdatedBy);
        }
    }

    private ZonedDateTime now() {
        return ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
