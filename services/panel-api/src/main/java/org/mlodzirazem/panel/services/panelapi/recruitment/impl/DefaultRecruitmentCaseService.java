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

package org.mlodzirazem.panel.services.panelapi.recruitment.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentSource;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEventRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.NewRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCandidateField;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseService;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseTransitionOption;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseUpdate;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentRules;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DefaultRecruitmentCaseService implements RecruitmentCaseService {
    /**
     * The pre-declaration cluster owned by M2 (intake). Edges and per-state rejection reasons come
     * from {@link RecruitmentRules} — the single state machine shared with the M3 workflow engine;
     * this service only checks that the target stays inside the intake cluster (a post-declaration
     * target such as DECLARATION_RECEIVED, PROBATION or ACCEPTED is milestone M3 and is refused here).
     */
    private static final Set<RecruitmentCaseStatus> INTAKE_STATES =
        EnumSet.of(RecruitmentCaseStatus.NEW, RecruitmentCaseStatus.IN_PROGRESS,
            RecruitmentCaseStatus.NO_CONTACT, RecruitmentCaseStatus.AWAITING_DECLARATION);

    private static final String OPEN_EMAIL_INDEX = "recruitment_cases_open_email_district_idx";
    private static final int NOTE_MAX_LENGTH = 2000;

    private final TRecruitmentCaseRepository cases;
    private final TRecruitmentCaseEventRepository events;
    private final TBodyRepository bodies;
    private final TMemberRepository members;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional
    public TRecruitmentCase create(NewRecruitmentCase newCase, long openedByMemberId) {
        TBody district = bodies.findById(newCase.districtBodyId())
            .orElseThrow(() -> new NotFoundException("Body", newCase.districtBodyId()));

        if (!district.isDistrict()) {
            throw new RuleViolationException("DISTRICT_REQUIRED",
                "A recruitment case belongs to a district body, and " + district.getName() + " is not one");
        }

        TRecruitmentCase candidateCase = new TRecruitmentCase();
        candidateCase.setDistrict(district);
        candidateCase.setSource(newCase.source() == null ? RecruitmentSource.MANUAL : newCase.source());
        candidateCase.setStatus(RecruitmentCaseStatus.NEW);

        validateAndApply(candidateCase, newCase.candidateFields());

        members.findById(openedByMemberId).ifPresent(candidateCase::setCreatedBy);

        try {
            cases.saveAndFlush(candidateCase);
        } catch (DataIntegrityViolationException conflict) {
            throw openCaseConflict(conflict);
        }

        List<String> knownFields = knownFieldNames(newCase.candidateFields());
        record(candidateCase, RecruitmentCaseEventType.CASE_CREATED, knownFields);
        auditLog.record("recruitment :: case-create",
            AuditPayload.of("caseId", candidateCase.getId())
                .withId("districtBodyId", district.getId())
                .withFields(knownFields)
                .withOutcome(RecruitmentCaseStatus.NEW.name()));

        return candidateCase;
    }

    @Override
    @Transactional
    public RecruitmentCaseUpdate update(long caseId, Map<RecruitmentCandidateField, @Nullable Object> changes,
                                        long actorMemberId) {
        TRecruitmentCase candidateCase = open(caseId);
        Set<RecruitmentCandidateField> changed = validateAndApply(candidateCase, changes);

        if (changed.isEmpty()) {
            return new RecruitmentCaseUpdate(candidateCase, changed);
        }

        members.findById(actorMemberId).ifPresent(candidateCase::setUpdatedBy);

        try {
            cases.saveAndFlush(candidateCase);
        } catch (DataIntegrityViolationException conflict) {
            throw openCaseConflict(conflict);
        }

        List<String> names = changed.stream().map(RecruitmentCandidateField::propertyName).toList();
        auditLog.record("recruitment :: case-update",
            AuditPayload.of("caseId", caseId)
                .withId("districtBodyId", candidateCase.getDistrict().getId())
                .withFields(names));

        return new RecruitmentCaseUpdate(candidateCase, changed);
    }

    @Override
    @Transactional
    public TRecruitmentCase transition(long caseId, RecruitmentCaseStatus target,
                                       @Nullable RecruitmentRejectionReason reason, long actorMemberId) {
        TRecruitmentCase candidateCase = open(caseId);
        RecruitmentCaseStatus from = candidateCase.getStatus();

        boolean allowed = INTAKE_STATES.contains(from)
            && RecruitmentRules.canTransition(from, target)
            && (target == RecruitmentCaseStatus.REJECTED || INTAKE_STATES.contains(target));

        if (!allowed) {
            throw new RuleViolationException("TRANSITION_NOT_ALLOWED",
                "The transition " + from.name() + " -> " + target.name()
                    + " is not part of the intake cluster");
        }

        if (target == RecruitmentCaseStatus.REJECTED) {
            if (reason == null) {
                throw new RuleViolationException("REJECTION_REASON_REQUIRED",
                    "Closing a case as REJECTED requires a rejection reason");
            }

            if (!RecruitmentRules.allowedRejectionReasons(from).contains(reason)) {
                throw new RuleViolationException("REJECTION_REASON_NOT_ALLOWED",
                    "The reason " + reason.name() + " does not end a case before the declaration");
            }

            candidateCase.setRejectionReason(reason);
            candidateCase.setClosedAt(LocalDate.now(clock));
        }

        candidateCase.setStatus(target);
        members.findById(actorMemberId).ifPresent(candidateCase::setUpdatedBy);
        cases.saveAndFlush(candidateCase);

        record(candidateCase, RecruitmentCaseEventType.STATUS_CHANGED, List.of("status"));
        auditLog.record("recruitment :: case-transition",
            AuditPayload.of("caseId", caseId)
                .withId("districtBodyId", candidateCase.getDistrict().getId())
                .withFields(List.of("status"))
                .withOutcome(target.name()));

        if (target == RecruitmentCaseStatus.REJECTED) {
            record(candidateCase, RecruitmentCaseEventType.CASE_CLOSED, List.of("rejection_reason", "closed_at"));
        }

        return candidateCase;
    }

    @Override
    @Transactional
    public TRecruitmentCaseEvent addNote(long caseId, String note, long actorMemberId) {
        TRecruitmentCase candidateCase = writable(caseId);

        String trimmed = note == null ? "" : note.strip();

        if (trimmed.isEmpty()) {
            throw new RuleViolationException("FIELD_REQUIRED", "The note text is required");
        }

        if (trimmed.length() > NOTE_MAX_LENGTH) {
            throw new RuleViolationException("NOTE_TOO_LONG",
                "The note is longer than " + NOTE_MAX_LENGTH + " characters");
        }

        TRecruitmentCaseEvent eventCase = events.saveAndFlush(withActor(newEvent(candidateCase,
            RecruitmentCaseEventType.NOTE_ADDED, "{\"fields\": [\"note\"]}"), actorMemberId));

        auditLog.record("recruitment :: case-note",
            AuditPayload.of("caseId", caseId).withFields(List.of("note")));

        return eventCase;
    }

    @Override
    @Transactional
    public TRecruitmentCaseEvent sendReminder(long caseId, long actorMemberId) {
        TRecruitmentCase candidateCase = open(caseId);

        TRecruitmentCaseEvent eventCase = events.saveAndFlush(withActor(newEvent(candidateCase,
            RecruitmentCaseEventType.REMINDER_SENT, "{\"fields\": [\"reminder\"]}"), actorMemberId));

        auditLog.record("recruitment :: case-reminder",
            AuditPayload.of("caseId", caseId).withFields(List.of("reminder")));

        return eventCase;
    }

    @Override
    @Transactional(readOnly = true)
    public TRecruitmentCase get(long caseId) {
        return cases.findById(caseId).orElseThrow(() -> new NotFoundException("RecruitmentCase", caseId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TRecruitmentCaseEvent> timeline(long caseId) {
        get(caseId);
        return events.findAllByRecruitmentCaseIdOrderByCreatedAtAscIdAsc(caseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecruitmentCaseTransitionOption> availableTransitions(long caseId) {
        TRecruitmentCase candidateCase = get(caseId);
        RecruitmentCaseStatus from = candidateCase.getStatus();
        boolean closed = candidateCase.getClosedAt() != null;

        return RecruitmentRules.allowedTargets(from).stream()
            .sorted(Comparator.comparingInt(Enum::ordinal))
            .map(target -> {
                if (closed) {
                    return new RecruitmentCaseTransitionOption(target, false, null, "CASE_NOT_OPEN");
                }

                // The two flows owned by other milestones stay visible but explicitly blocked:
                // the declaration arrival is artifact-gated (M4a), ACCEPTED allocates the member
                // number and the members row in one transaction (M6).
                if (from == RecruitmentCaseStatus.AWAITING_DECLARATION
                    && target == RecruitmentCaseStatus.DECLARATION_RECEIVED) {
                    return new RecruitmentCaseTransitionOption(target, false, null, "DECLARATION_ARTIFACT_REQUIRED");
                }
                if (target == RecruitmentCaseStatus.ACCEPTED) {
                    return new RecruitmentCaseTransitionOption(target, false, null, "MEMBER_ALLOCATION_PENDING");
                }

                if (target == RecruitmentCaseStatus.REJECTED) {
                    List<RecruitmentRejectionReason> reasons
                        = RecruitmentRules.allowedRejectionReasons(from).stream()
                            .sorted(Comparator.comparingInt(Enum::ordinal))
                            .toList();
                    return new RecruitmentCaseTransitionOption(target, true, reasons, null);
                }

                return new RecruitmentCaseTransitionOption(target, true, null, null);
            })
            .toList();
    }

    /**
     * Validates and applies candidate fields. Blank strings clear optional fields, but the names are
     * never clearable: they mirror the DB CHECK pair ((candidate_first_name IS NULL) =
     * (candidate_last_name IS NULL)) plus the "non-anonymised cases keep their names" invariant, and
     * M2 never anonymises. The birth date, when known, must pass the ordinary-membership admission
     * age, because recruitment leads to członkostwo zwyczajne.
     */
    private Set<RecruitmentCandidateField> validateAndApply(
        TRecruitmentCase candidateCase,
        Map<RecruitmentCandidateField, @Nullable Object> values
    ) {
        Set<RecruitmentCandidateField> changed = EnumSet.noneOf(RecruitmentCandidateField.class);

        values.forEach((field, value) -> {
            if (value != null && !field.type().isInstance(value)) {
                throw new RuleViolationException("INVALID_VALUE", field.propertyName() + " has an invalid type");
            }

            Object normalized = value instanceof String text ? normalize(text) : value;

            if (!Objects.equals(field.get(candidateCase), normalized)) {
                field.set(candidateCase, normalized);
                changed.add(field);
            }
        });

        boolean firstNameBlank = isBlank(candidateCase.getCandidateFirstName());
        boolean lastNameBlank = isBlank(candidateCase.getCandidateLastName());

        if (firstNameBlank != lastNameBlank) {
            throw new RuleViolationException("NAMES_ARE_A_PAIR",
                "candidateFirstName and candidateLastName are always set together");
        }

        if (firstNameBlank) {
            throw new RuleViolationException("FIELD_REQUIRED",
                "candidateFirstName and candidateLastName are required");
        }

        if (candidateCase.getCandidateBirthDate() != null) {
            MemberRules.validateAdmissionAge(candidateCase.getCandidateBirthDate(), MembershipType.ORDINARY,
                LocalDate.now(clock));
        }

        return changed;
    }

    private TRecruitmentCase open(long caseId) {
        TRecruitmentCase candidateCase = get(caseId);

        if (candidateCase.getClosedAt() != null) {
            throw new ConflictException("CASE_NOT_OPEN", "The case is closed; closed cases are read-only");
        }

        return candidateCase;
    }

    /**
     * A note stays possible on a closed (but not yet anonymised) case, e.g. to record why data was
     * minimised; an anonymised case accepts no new content.
     */
    private TRecruitmentCase writable(long caseId) {
        TRecruitmentCase candidateCase = get(caseId);

        if (candidateCase.getAnonymizedAt() != null) {
            throw new ConflictException("CASE_ANONYMIZED", "The case is anonymised and accepts no new content");
        }

        return candidateCase;
    }

    private TRecruitmentCaseEvent newEvent(
        TRecruitmentCase candidateCase,
        RecruitmentCaseEventType type,
        @Nullable String payload
    ) {
        TRecruitmentCaseEvent eventCase = new TRecruitmentCaseEvent();
        eventCase.setRecruitmentCase(candidateCase);
        eventCase.setEventType(type);
        eventCase.setPayload(payload);
        return eventCase;
    }

    private void record(TRecruitmentCase candidateCase, RecruitmentCaseEventType type, List<String> fields) {
        events.saveAndFlush(newEvent(candidateCase, type,
            "{\"fields\": [" + fields.stream().map(f -> "\"" + f + "\"").collect(Collectors.joining(", ")) + "]}"));
    }

    private static @Nullable String normalize(String value) {
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static List<String> knownFieldNames(Map<RecruitmentCandidateField, @Nullable Object> values) {
        List<String> names = new ArrayList<>();

        names.add(RecruitmentCandidateField.FIRST_NAME.propertyName());
        names.add(RecruitmentCandidateField.LAST_NAME.propertyName());
        values.forEach((field, value) -> {
            if (value != null && field != RecruitmentCandidateField.FIRST_NAME
                && field != RecruitmentCandidateField.LAST_NAME) {
                names.add(field.propertyName());
            }
        });

        return names;
    }

    private TRecruitmentCaseEvent withActor(TRecruitmentCaseEvent eventCase, long actorMemberId) {
        eventCase.setCreatedBy(members.findById(actorMemberId).orElse(null));
        return eventCase;
    }

    private static ConflictException openCaseConflict(DataIntegrityViolationException cause) {
        String message = String.valueOf(cause.getMostSpecificCause().getMessage());

        if (message.contains(OPEN_EMAIL_INDEX)) {
            return new ConflictException("OPEN_CASE_EXISTS",
                "An open case for this e-mail already exists in this district",
                List.of(RecruitmentCandidateField.EMAIL.propertyName()));
        }

        throw cause;
    }

    static String jsonEscape(String text) {
        StringBuilder escaped = new StringBuilder("\"");

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }

        return escaped.append("\"").toString();
    }
}
