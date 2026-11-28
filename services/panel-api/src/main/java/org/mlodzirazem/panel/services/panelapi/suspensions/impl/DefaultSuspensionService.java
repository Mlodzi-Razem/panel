/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

package org.mlodzirazem.panel.services.panelapi.suspensions.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipService;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.fees.Arrears;
import org.mlodzirazem.panel.services.panelapi.fees.ArrearsService;
import org.mlodzirazem.panel.services.panelapi.fees.DistrictScope;
import org.mlodzirazem.panel.services.panelapi.fees.MemberArrears;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.BodiesManagerGuard;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyMembershipStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipEnding;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipEndingRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.suspensions.Suspension;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspendMember;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionCheck;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionConfirmationRequiredException;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionFilter;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionKind;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionOptions;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionResult;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionRow;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionRowsPage;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionRules;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionScope;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionService;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionState;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionWarning;
import org.mlodzirazem.panel.services.panelapi.suspensions.MandateRef;
import org.mlodzirazem.panel.services.panelapi.suspensions.LiftSuspension;
import org.mlodzirazem.panel.services.panelapi.suspensions.EndSuspendedMandates;
import org.mlodzirazem.panel.services.panelapi.suspensions.PositionLoss;
import org.mlodzirazem.panel.services.panelapi.suspensions.ArrearsCandidate;
import org.mlodzirazem.panel.services.panelapi.suspensions.MemberSuspensionChanged;
import org.mlodzirazem.panel.services.panelapi.members.SuspensionRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Persistence through JdbcClient: the register has no JPA entity, and its sequence does not match one. Mandates are
 * touched only through {@link BodyMembershipService#end}, never directly.
 */
@Service
@RequiredArgsConstructor
class DefaultSuspensionService implements SuspensionService {
    static final String OP_SUSPEND = "suspensions :: suspend";
    static final String OP_LIFT = "suspensions :: lift";
    static final String OP_APPEAL_ENDED = "suspensions :: appeal-ended";
    static final String OP_VOID = "suspensions :: void";
    static final String OP_MANDATES_ENDED = "suspensions :: mandates-ended";

    private static final Set<MemberStatus> ELIGIBLE = EnumSet.of(MemberStatus.ACTIVE, MemberStatus.PROBATION,
        MemberStatus.SUSPENDED);

    private static final String SELECT = """
        SELECT s.id, s.suspended_user_id, s.kind::text AS kind, s.scope::text AS scope, s.scope_description,
               s.suspending_body_id, b.name AS body_name, s.suspension_start, s.suspension_end, s.decision_date,
               s.decision_signature, s.lifted_at, s.lifted_reason, s.lifted_body, s.lift_decision_signature,
               s.appeal_ended_on, s.appeal_ended_reference, (s.status = 'VOIDED') AS voided, s.voided_at,
               s.void_reason, s.void_decision_reference, s.created_at
        FROM suspensions s
        JOIN bodies b ON b.id = s.suspending_body_id
        """;

    private static final String OVERLAPPING = """
        SELECT COUNT(*) FROM suspensions
        WHERE suspended_user_id = :memberId
          AND kind = CAST(:kind AS suspension_kind)
          AND scope = CAST(:scope AS suspension_scope)
          AND status = 'ACTIVE'
          AND lifted_at IS NULL
          AND (CAST(:end AS DATE) IS NULL OR suspension_start < CAST(:end AS DATE))
          AND (suspension_end IS NULL OR suspension_end > CAST(:start AS DATE))
        """;

    private static final String ENDINGS_OF = """
        SELECT COUNT(*) FROM suspension_mandate_endings WHERE suspension_id = :suspensionId
        """;

    private final TMemberRepository members;
    private final TBodyRepository bodies;
    private final TBodyMembershipRepository memberships;
    private final TBodyMembershipEndingRepository endings;
    private final BodyMembershipService bodyMemberships;
    private final BodiesManagerGuard bodiesManagerGuard;
    private final SuspensionRegistry registry;
    private final AffiliationService affiliations;
    private final ArrearsService arrears;
    private final CurrentMemberResolver currentMemberResolver;
    private final JdbcClient jdbc;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Value("${panel.suspensions.appeal-hint-days:14}")
    private int appealHintDays;

    @Override
    @Transactional(readOnly = true)
    public SuspensionCheck check(SuspendMember request) {
        return evaluate(request);
    }

    @Override
    @Transactional
    public SuspensionResult suspend(SuspendMember request) {
        bodiesManagerGuard.lock();
        lockMember(request.memberId());

        SuspensionCheck check = evaluate(request);

        if (check.blocked()) {
            throw toException(check.violations().getFirst());
        }

        if (check.needsConfirmation()) {
            throw new SuspensionConfirmationRequiredException(check);
        }

        LocalDate today = today();
        LocalDate start = start(request, today);
        LocalDate end = request.end();
        Long actor = actorId();
        boolean voluntary = request.kind() == SuspensionKind.VOLUNTARY;
        String signature = strip(request.decisionSignature());
        String scopeDescription = blankToNull(request.scopeDescription());

        Long id = jdbc.sql("""
                INSERT INTO suspensions (suspended_user_id, suspending_body_id, suspension_start, suspension_end, decision_date,
                                         decision_signature, kind, scope, scope_description,
                                         appeal_ended_on, appeal_ended_reference,
                                         created_by_user_id, updated_by_user_id)
                VALUES (:memberId, :bodyId, :start, :end, :decisionDate, :signature, CAST(:kind AS suspension_kind),
                        CAST(:scope AS suspension_scope), :scopeDescription,
                        :appealEndedOn, :appealEndedReference, :actor, :actor)
                RETURNING id
                """)
            .param("memberId", request.memberId())
            .param("bodyId", request.suspendingBodyId())
            .param("start", start)
            .param("end", end, Types.DATE)
            .param("decisionDate", request.decisionDate(), Types.DATE)
            .param("signature", signature, Types.VARCHAR)
            .param("kind", request.kind().name())
            .param("scope", request.scope().name())
            .param("scopeDescription", scopeDescription, Types.VARCHAR)
            .param("appealEndedOn", voluntary ? start : null, Types.DATE)
            .param("appealEndedReference", voluntary ? signature : null, Types.VARCHAR)
            .param("actor", actor, Types.BIGINT)
            .query(Long.class)
            .single();

        Suspension created = get(id);

        List<String> fields = new ArrayList<>(List.of("kind", "scope", "suspensionStart"));

        if (end != null) {
            fields.add("suspensionEnd");
        }

        if (request.decisionDate() != null) {
            fields.add("decisionDate");
        }

        fields.add("decisionSignature");

        if (scopeDescription != null) {
            fields.add("scopeDescription");
        }

        if (start.isBefore(today)) {
            fields.add("retroactive");
        }

        fields.addAll(confirmedFields(check.warnings()));

        audit(OP_SUSPEND, id, request.memberId(), request.suspendingBodyId(), fields);
        publish(MemberSuspensionChanged.Change.CREATED, created);

        return new SuspensionResult(created, positionLossOf(created, today));
    }

    @Override
    @Transactional
    public Suspension lift(long suspensionId, LiftSuspension request) {
        Suspension current = get(suspensionId);
        LocalDate today = today();

        if (current.voided()) {
            throw new ConflictException("SUSPENSION_VOIDED", "A voided entry is never touched again");
        }

        if (current.liftedOn() != null) {
            throw new ConflictException("SUSPENSION_ALREADY_LIFTED", "The suspension has already been lifted",
                List.of("liftedOn"));
        }

        requireNotSelf(current.memberId());

        LocalDate liftedOn = request.liftedOn() != null ? request.liftedOn() : today;
        LocalDate end = current.end();

        if (liftedOn.isBefore(current.start())) {
            throw new RuleViolationException("INVALID_PERIOD", "The lifting date is before the start of the suspension");
        }

        if (end != null && !liftedOn.isBefore(end) && !end.isAfter(today)) {
            throw new RuleViolationException("SUSPENSION_ALREADY_EXPIRED", "The suspension has already expired");
        }

        if (end != null && liftedOn.isAfter(end)) {
            throw new RuleViolationException("INVALID_PERIOD", "The lifting date is after the end of the suspension");
        }

        String reason = strip(request.reason());

        if (reason == null || reason.length() > SuspensionRules.MAX_TEXT_LENGTH) {
            throw new RuleViolationException("LIFT_REASON_REQUIRED",
                "Lifting requires a reason of at most " + SuspensionRules.MAX_TEXT_LENGTH + " characters");
        }

        String signature = requireSignature(request.decisionSignature());
        TBody body = body(request.liftingBodyId());

        if (!competentFor(current.kind(), body, current.memberId())) {
            throw new RuleViolationException("SUSPENDING_BODY_NOT_COMPETENT",
                "The body is not competent to lift this kind of suspension");
        }

        List<SuspensionWarning> warnings = new ArrayList<>();

        if (current.kind() == SuspensionKind.ARREARS
            && arrears.of(current.memberId(), liftedOn).months() >= SuspensionRules.ARREARS_MIN_MONTHS) {
            warnings.add(warning("ARREARS_STILL_OUTSTANDING", request.confirmedWarnings()));
        }

        SuspensionCheck check = new SuspensionCheck(List.of(), warnings, List.of());

        if (check.needsConfirmation()) {
            throw new SuspensionConfirmationRequiredException(check);
        }

        jdbc.sql("""
                UPDATE suspensions
                SET lifted_at = :liftedOn, lifted_reason = :reason, lifted_body = :bodyId,
                    lift_decision_signature = :signature, updated_by_user_id = :actor, updated_at = now()
                WHERE id = :id AND lifted_at IS NULL AND status = 'ACTIVE'
                """)
            .param("liftedOn", liftedOn)
            .param("reason", reason)
            .param("bodyId", body.getId())
            .param("signature", signature)
            .param("actor", actorId(), Types.BIGINT)
            .param("id", suspensionId)
            .update();

        List<String> fields = new ArrayList<>(List.of("liftedAt", "liftedReason", "liftDecisionSignature"));

        if (liftedOn.isBefore(today)) {
            fields.add("retroactive");
        }

        fields.addAll(confirmedFields(warnings));

        audit(OP_LIFT, suspensionId, current.memberId(), body.getId(), fields);

        Suspension lifted = get(suspensionId);
        publish(MemberSuspensionChanged.Change.LIFTED, lifted);

        return lifted;
    }

    @Override
    @Transactional
    public Suspension recordAppealEnded(long suspensionId, LocalDate endedOn, String decisionReference) {
        Suspension current = get(suspensionId);
        LocalDate today = today();

        if (current.scope() != SuspensionScope.FULL || current.kind() == SuspensionKind.VOLUNTARY) {
            throw new RuleViolationException("APPEAL_NOT_APPLICABLE",
                "Only a full suspension of a resolution has an appeal period");
        }

        if (current.voided()) {
            throw new ConflictException("SUSPENSION_VOIDED", "A voided entry is never touched again");
        }

        if (current.appealEndedOn() != null) {
            throw new ConflictException("APPEAL_ALREADY_RECORDED", "The end of the appeal period is recorded once",
                List.of("appealEndedOn"));
        }

        requireNotSelf(current.memberId());

        if (endedOn.isBefore(current.start())) {
            throw new RuleViolationException("INVALID_PERIOD", "The end of the appeal period is before the suspension");
        }

        if (endedOn.isAfter(today)) {
            throw new RuleViolationException("DATE_IN_FUTURE_NOT_ALLOWED", "The end of the appeal period is in the future");
        }

        String reference = requireReference(decisionReference, "DECISION_REFERENCE_REQUIRED");

        jdbc.sql("""
                UPDATE suspensions
                SET appeal_ended_on = :endedOn, appeal_ended_reference = :reference,
                    updated_by_user_id = :actor, updated_at = now()
                WHERE id = :id AND appeal_ended_on IS NULL
                """)
            .param("endedOn", endedOn)
            .param("reference", reference)
            .param("actor", actorId(), Types.BIGINT)
            .param("id", suspensionId)
            .update();

        audit(OP_APPEAL_ENDED, suspensionId, current.memberId(), current.suspendingBodyId(),
            List.of("appealEndedOn", "appealEndedReference"));

        Suspension updated = get(suspensionId);
        publish(MemberSuspensionChanged.Change.APPEAL_ENDED, updated);

        return updated;
    }

    @Override
    @Transactional
    public Suspension voidSuspension(long suspensionId, String reason, String decisionReference) {
        Suspension current = get(suspensionId);

        if (current.voided()) {
            throw new ConflictException("SUSPENSION_ALREADY_VOIDED", "The entry is already voided");
        }

        if (endedCount(suspensionId) > 0) {
            throw new ConflictException("SUSPENSION_POSITION_LOSS_RECORDED",
                "Mandates were ended on the basis of this suspension; a voiding would hide that");
        }

        String trimmedReason = strip(reason);

        if (trimmedReason == null || trimmedReason.length() > SuspensionRules.MAX_TEXT_LENGTH) {
            throw new RuleViolationException("VOID_REASON_REQUIRED",
                "Voiding requires a reason of at most " + SuspensionRules.MAX_TEXT_LENGTH + " characters");
        }

        String reference = strip(decisionReference);

        if (reference != null && reference.length() > SuspensionRules.MAX_REFERENCE_LENGTH) {
            throw new RuleViolationException("DECISION_REFERENCE_TOO_LONG", "The decision reference is too long");
        }

        requireNotSelf(current.memberId());

        Long actor = actorId();

        jdbc.sql("""
                UPDATE suspensions
                SET status = 'VOIDED', void_reason = :reason, void_decision_reference = :reference,
                    voided_at = now(), voided_by_user_id = :actor, updated_by_user_id = :actor, updated_at = now()
                WHERE id = :id AND status = 'ACTIVE'
                """)
            .param("reason", trimmedReason)
            .param("reference", reference, Types.VARCHAR)
            .param("actor", actor, Types.BIGINT)
            .param("id", suspensionId)
            .update();

        List<String> fields = new ArrayList<>(List.of("status", "voidReason"));

        if (reference != null) {
            fields.add("voidDecisionReference");
        }

        audit(OP_VOID, suspensionId, current.memberId(), current.suspendingBodyId(), fields);

        Suspension voided = get(suspensionId);
        publish(MemberSuspensionChanged.Change.VOIDED, voided);

        return voided;
    }

    @Override
    @Transactional(readOnly = true)
    public PositionLoss positionLoss(long suspensionId) {
        return positionLossOf(get(suspensionId), today());
    }

    @Override
    @Transactional
    public PositionLoss endMandates(long suspensionId, EndSuspendedMandates request) {
        bodiesManagerGuard.lock();

        Suspension current = get(suspensionId);
        LocalDate today = today();

        if (current.voided()) {
            throw new ConflictException("SUSPENSION_VOIDED", "A voided entry is never touched again");
        }

        requireNotSelf(current.memberId());

        PositionLoss loss = positionLossOf(current, today);

        if (!loss.applicable() || !loss.due() || loss.dueOn() == null) {
            throw new RuleViolationException("POSITION_LOSS_NOT_DUE",
                "The positions are not lost yet, or the date of the loss is not recorded");
        }

        String reference = requireReference(request.decisionReference(), "DECISION_REFERENCE_REQUIRED");

        if (request.membershipIds().isEmpty()) {
            throw new RuleViolationException("MEMBERSHIP_IDS_REQUIRED", "Choose at least one position to end");
        }

        Set<Long> proposed = loss.outstanding().stream().map(MandateRef::membershipId).collect(Collectors.toSet());

        for (long membershipId : request.membershipIds()) {
            if (!proposed.contains(membershipId)) {
                throw new RuleViolationException("MANDATE_NOT_IN_PROPOSAL",
                    "A chosen position is not one of the positions proposed for ending");
            }
        }

        Long actor = actorId();

        for (long membershipId : new TreeSet<>(request.membershipIds())) {
            bodyMemberships.end(membershipId, loss.dueOn(), reference);

            TBodyMembershipEnding ending = endings.findFirstByMembershipIdOrderByIdDesc(membershipId)
                                                  .orElseThrow(() -> new NotFoundException("Body membership ending",
                                                      membershipId));

            jdbc.sql("""
                    INSERT INTO suspension_mandate_endings (suspension_id, body_membership_ending_id, created_by_user_id)
                    VALUES (:suspensionId, :endingId, :actor)
                    """)
                .param("suspensionId", suspensionId)
                .param("endingId", ending.getId())
                .param("actor", actor, Types.BIGINT)
                .update();
        }

        audit(OP_MANDATES_ENDED, suspensionId, current.memberId(), current.suspendingBodyId(),
            List.of("endedMandates", "decisionReference"));
        publish(MemberSuspensionChanged.Change.POSITIONS_ENDED, current);

        return positionLossOf(get(suspensionId), today);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Suspension> historyOf(long memberId) {
        return jdbc.sql(SELECT + " WHERE s.suspended_user_id = :memberId ORDER BY s.suspension_start DESC, s.id DESC")
                   .param("memberId", memberId)
                   .query(DefaultSuspensionService::map)
                   .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Suspension get(long suspensionId) {
        return jdbc.sql(SELECT + " WHERE s.id = :id")
                   .param("id", suspensionId)
                   .query(DefaultSuspensionService::map)
                   .optional()
                   .orElseThrow(() -> new NotFoundException("Suspension", suspensionId));
    }

    @Override
    @Transactional(readOnly = true)
    public SuspensionOptions optionsFor(long memberId) {
        members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));

        Long primary = primaryDistrictOf(memberId);
        List<SuspensionOptions.Body> options = new ArrayList<>();

        Stream.of(BodyKind.NATIONAL_BOARD, BodyKind.DISTRICT_BOARD, BodyKind.PEER_COURT)
              .flatMap(kind -> bodies.findAllByKind(kind).stream())
              .forEach(body -> {
                  List<SuspensionKind> allowed = competentKinds(body, primary);

                  if (!allowed.isEmpty()) {
                      options.add(new SuspensionOptions.Body(body.getId(), body.getName(), body.kind(), allowed));
                  }
              });

        return new SuspensionOptions(appealHintDays, options);
    }

    @Override
    @Transactional(readOnly = true)
    public SuspensionRowsPage list(SuspensionFilter filter, DistrictScope scope, int page, int size) {
        LocalDate today = today();
        Map<Long, Long> districtByMember = new HashMap<>();
        List<SuspensionRow> rows = new ArrayList<>();

        for (Suspension s : all()) {
            if (!districtByMember.containsKey(s.memberId())) {
                districtByMember.put(s.memberId(), primaryDistrictOf(s.memberId()));
            }

            Long districtId = districtByMember.get(s.memberId());

            if (!matches(filter.state(), s.state(today))
                || (filter.kind() != null && s.kind() != filter.kind())
                || (filter.districtId() != null && !filter.districtId().equals(districtId))
                || !scope.includes(districtId)) {
                continue;
            }

            PositionLoss loss = positionLossOf(s, today);
            boolean needsAction = loss.due() && !loss.outstanding().isEmpty();

            if (filter.needsAction() && !needsAction) {
                continue;
            }

            rows.add(new SuspensionRow(s, districtId, needsAction));
        }

        rows.sort(Comparator.comparing((SuspensionRow r) -> r.suspension().start()).reversed()
                            .thenComparing(Comparator.comparingLong((SuspensionRow r) -> r.suspension().id()).reversed()));

        int from = Math.min(Math.max(0, page) * size, rows.size());
        int to = Math.min(from + size, rows.size());

        return new SuspensionRowsPage(rows.subList(from, to), rows.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArrearsCandidate> arrearsCandidates(DistrictScope scope) {
        LocalDate today = today();

        Set<Long> openArrears = all().stream()
                                     .filter(s -> s.kind() == SuspensionKind.ARREARS)
                                     .filter(s -> {
                                         SuspensionState state = s.state(today);
                                         return state == SuspensionState.SCHEDULED || state == SuspensionState.ACTIVE;
                                     })
                                     .map(Suspension::memberId)
                                     .collect(Collectors.toSet());

        return arrears.longerThan(2, scope)
                      .stream()
                      .filter(m -> m.arrears().months() >= SuspensionRules.ARREARS_MIN_MONTHS)
                      .filter(m -> !openArrears.contains(m.memberId()))
                      .map(DefaultSuspensionService::candidate)
                      .toList();
    }

    // ---- rules shared by check, suspend and the views ----

    private SuspensionCheck evaluate(SuspendMember request) {
        TMember member = members.findById(request.memberId())
                                .orElseThrow(() -> new NotFoundException("Member", request.memberId()));
        LocalDate today = today();
        LocalDate start = start(request, today);
        LocalDate end = request.end();
        String signature = strip(request.decisionSignature());
        String scopeDescription = blankToNull(request.scopeDescription());
        SuspensionKind kind = request.kind();
        SuspensionScope scope = request.scope();
        Long actor = actorId();

        List<SuspensionCheck.Violation> violations = new ArrayList<>();

        if (!ELIGIBLE.contains(member.getStatus())) {
            violation(violations, "MEMBER_NOT_ELIGIBLE_FOR_SUSPENSION",
                "A member with status " + member.getStatus() + " cannot be suspended");
        }

        if (actor != null && actor.equals(member.getId())) {
            violation(violations, "SELF_SUSPENSION_NOT_ALLOWED",
                "A suspension cannot be recorded by the person it concerns");
        }

        if (signature == null) {
            violation(violations, "DECISION_SIGNATURE_REQUIRED", "The resolution or statement reference is required");
        } else if (signature.length() > SuspensionRules.MAX_REFERENCE_LENGTH) {
            violation(violations, "DECISION_REFERENCE_TOO_LONG", "The decision reference is too long");
        }

        if (end != null && !end.isAfter(start)) {
            violation(violations, "INVALID_PERIOD", "The end date must be after the start date");
        }

        if (kind == SuspensionKind.VOLUNTARY) {
            if (start.isAfter(today)) {
                violation(violations, "START_IN_FUTURE_NOT_ALLOWED", "A voluntary suspension starts on the day the statement was received");
            }
        } else if (start.isAfter(today.plusDays(SuspensionRules.MAX_START_AHEAD_DAYS))) {
            violation(violations, "START_TOO_FAR_IN_FUTURE", "The suspension starts too far in the future");
        }

        // Data uchwały (W4): a resolution exists for ARREARS and BY_ORGAN only; it cannot be later than the start
        LocalDate decisionDate = request.decisionDate();

        if (kind == SuspensionKind.VOLUNTARY) {
            if (decisionDate != null) {
                violation(violations, "DECISION_DATE_NOT_ALLOWED", "A voluntary suspension has no resolution date");
            }
        } else if (decisionDate == null) {
            violation(violations, "DECISION_DATE_REQUIRED", "The date of the resolution is required");
        } else if (decisionDate.isAfter(today)) {
            violation(violations, "DECISION_DATE_IN_FUTURE", "The date of the resolution is in the future");
        } else if (decisionDate.isAfter(start)) {
            violation(violations, "DECISION_DATE_AFTER_START", "The suspension cannot start before its resolution");
        }

        if (kind == SuspensionKind.VOLUNTARY && scope != SuspensionScope.FULL) {
            violation(violations, "VOLUNTARY_REQUIRES_FULL_SCOPE", "A voluntary suspension covers all rights");
        }

        if (scope == SuspensionScope.PARTIAL_OTHER) {
            if (scopeDescription == null || scopeDescription.length() > SuspensionRules.MAX_TEXT_LENGTH) {
                violation(violations, "SCOPE_DESCRIPTION_REQUIRED", "A partial scope needs a description");
            }
        } else if (scopeDescription != null) {
            violation(violations, "SCOPE_DESCRIPTION_NOT_ALLOWED", "Only a partial scope has a description");
        }

        TBody body = body(request.suspendingBodyId());

        if (!competentFor(kind, body, request.memberId())) {
            violation(violations, "SUSPENDING_BODY_NOT_COMPETENT",
                "The body is not competent to suspend for this kind of suspension");
        }

        if (scope.suspendsCompetences() && bodiesManagerGuard.wouldLeaveNone(List.of(), request.memberId(), 0)) {
            violation(violations, BodiesManagerGuard.VIOLATION_CODE,
                "Suspending this member would leave nobody able to manage bodies");
        }

        if (overlaps(request, start, end)) {
            violation(violations, "SUSPENSION_ALREADY_ACTIVE",
                "The person already has an open suspension of this kind and scope in this period");
        }

        List<SuspensionWarning> warnings = new ArrayList<>();

        if (kind == SuspensionKind.ARREARS
            && arrears.of(request.memberId(), start).months() < SuspensionRules.ARREARS_MIN_MONTHS) {
            warnings.add(warning("ARREARS_BELOW_THRESHOLD", request.confirmedWarnings()));
        }

        List<MandateRef> atStake = scope.suspendsCompetences()
            ? mandatesOverlapping(request.memberId(), start, end)
            : List.of();

        if (!atStake.isEmpty()) {
            warnings.add(warning("MANDATES_WILL_BE_SUSPENDED", request.confirmedWarnings()));
        }

        return new SuspensionCheck(List.copyOf(violations), List.copyOf(warnings), atStake);
    }

    private static void violation(List<SuspensionCheck.Violation> violations, String code, String message) {
        violations.add(new SuspensionCheck.Violation(code, message));
    }

    private static SuspensionWarning warning(String code, Set<String> confirmed) {
        return new SuspensionWarning(code, MESSAGES.getOrDefault(code, code), confirmed.contains(code));
    }

    private static final Map<String, String> MESSAGES = Map.of(
        "ARREARS_BELOW_THRESHOLD", "Recorded arrears are shorter than 3 months (art. 10 ust. 1 pkt 1)",
        "MANDATES_WILL_BE_SUSPENDED", "Competences in all organs are suspended from the start date (art. 10 ust. 3); positions may be lost",
        "ARREARS_STILL_OUTSTANDING", "Fee arrears are still recorded"
    );

    private static List<String> confirmedFields(List<SuspensionWarning> warnings) {
        return warnings.stream().filter(SuspensionWarning::confirmed).map(w -> "confirmed." + w.code()).toList();
    }

    private static RuntimeException toException(SuspensionCheck.Violation violation) {
        return "SUSPENSION_ALREADY_ACTIVE".equals(violation.code())
            ? new ConflictException(violation.code(), violation.message())
            : new RuleViolationException(violation.code(), violation.message());
    }

    private boolean overlaps(SuspendMember request, LocalDate start, @Nullable LocalDate end) {
        Long count = jdbc.sql(OVERLAPPING)
                         .param("memberId", request.memberId())
                         .param("kind", request.kind().name())
                         .param("scope", request.scope().name())
                         .param("start", start)
                         .param("end", end, Types.DATE)
                         .query(Long.class)
                         .single();
        return count > 0;
    }

    private List<MandateRef> mandatesOverlapping(long memberId, LocalDate start, @Nullable LocalDate end) {
        LocalDate until = end != null ? end : LocalDate.MAX;

        return memberships.findAllByMemberId(memberId)
                          .stream()
                          .filter(m -> m.getStatus() == BodyMembershipStatus.ACTIVE)
                          .filter(m -> m.getValidFrom().isBefore(until)
                              && (m.getValidTo() == null || m.getValidTo().isAfter(start)))
                          .map(DefaultSuspensionService::mandateRef)
                          .toList();
    }

    private PositionLoss positionLossOf(Suspension s, LocalDate today) {
        SuspensionRules.LossDate rule = SuspensionRules.positionLossDueOn(s);
        LocalDate dueOn = rule.dueOn();
        boolean due = dueOn != null && !dueOn.isAfter(today);

        List<MandateRef> outstanding = new ArrayList<>();
        List<MandateRef> conflicting = new ArrayList<>();

        if (rule.applicable() && dueOn != null) {
            for (TBodyMembership m : memberships.findAllByMemberId(s.memberId())) {
                if (m.getStatus() != BodyMembershipStatus.ACTIVE
                    || (m.getValidTo() != null && !m.getValidTo().isAfter(dueOn))) {
                    continue;
                }

                if (m.getValidFrom().isBefore(dueOn)) {
                    outstanding.add(mandateRef(m));
                } else {
                    conflicting.add(mandateRef(m));
                }
            }
        }

        return new PositionLoss(rule.applicable(), dueOn, due, List.copyOf(outstanding), List.copyOf(conflicting),
            (int) endedCount(s.id()));
    }

    private long endedCount(long suspensionId) {
        Long count = jdbc.sql(ENDINGS_OF).param("suspensionId", suspensionId).query(Long.class).single();
        return count == null ? 0 : count;
    }

    private boolean competentFor(SuspensionKind kind, TBody body, long memberId) {
        return SuspensionRules.competent(kind, body.kind(), ownDistrictBoard(body, primaryDistrictOf(memberId)));
    }

    private List<SuspensionKind> competentKinds(TBody body, @Nullable Long primary) {
        boolean own = ownDistrictBoard(body, primary);
        List<SuspensionKind> kinds = new ArrayList<>();

        for (SuspensionKind kind : SuspensionKind.values()) {
            if (SuspensionRules.competent(kind, body.kind(), own)) {
                kinds.add(kind);
            }
        }

        return kinds;
    }

    private static boolean ownDistrictBoard(TBody body, @Nullable Long primaryDistrict) {
        return body.kind() == BodyKind.DISTRICT_BOARD
            && primaryDistrict != null
            && primaryDistrict.equals(districtOf(body));
    }

    private static @Nullable Long districtOf(TBody body) {
        if (body.isDistrict()) {
            return body.getId();
        }

        TBody parent = body.getParentBody();
        return parent == null ? null : parent.getId();
    }

    private @Nullable Long primaryDistrictOf(long memberId) {
        return affiliations.districtsOf(memberId).primaryDistrictId();
    }

    private TBody body(long bodyId) {
        return bodies.findById(bodyId).orElseThrow(() -> new NotFoundException("Body", bodyId));
    }

    private void lockMember(long memberId) {
        jdbc.sql("SELECT id FROM members WHERE id = :id FOR UPDATE").param("id", memberId).query(Long.class).list();
    }

    private void requireNotSelf(long memberId) {
        Long actor = actorId();

        if (actor != null && actor == memberId) {
            throw new RuleViolationException("SELF_SUSPENSION_NOT_ALLOWED",
                "A suspension cannot be changed by the person it concerns");
        }
    }

    private String requireSignature(@Nullable String value) {
        String signature = strip(value);

        if (signature == null) {
            throw new RuleViolationException("DECISION_SIGNATURE_REQUIRED", "The resolution or statement reference is required");
        }

        if (signature.length() > SuspensionRules.MAX_REFERENCE_LENGTH) {
            throw new RuleViolationException("DECISION_REFERENCE_TOO_LONG", "The decision reference is too long");
        }

        return signature;
    }

    private String requireReference(@Nullable String value, String code) {
        String reference = strip(value);

        if (reference == null) {
            throw new RuleViolationException(code, "A decision reference is required");
        }

        if (reference.length() > SuspensionRules.MAX_REFERENCE_LENGTH) {
            throw new RuleViolationException("DECISION_REFERENCE_TOO_LONG", "The decision reference is too long");
        }

        return reference;
    }

    private void audit(String operation, long suspensionId, long memberId, long bodyId, List<String> fields) {
        auditLog.record(operation, AuditPayload.of("suspensionId", suspensionId)
                                               .withId("memberId", memberId)
                                               .withId("bodyId", bodyId)
                                               .withFields(fields));
    }

    private void publish(MemberSuspensionChanged.Change change, Suspension s) {
        events.publishEvent(new MemberSuspensionChanged(change, s.id(), s.memberId(), s.kind(), s.scope(), s.start(),
            s.end()));
    }

    private List<Suspension> all() {
        return jdbc.sql(SELECT + " ORDER BY s.suspension_start DESC, s.id DESC").query(DefaultSuspensionService::map).list();
    }

    private static boolean matches(SuspensionFilter.StateFilter filter, SuspensionState state) {
        return switch (filter) {
            case OPEN -> state == SuspensionState.SCHEDULED || state == SuspensionState.ACTIVE;
            case ENDED -> state == SuspensionState.EXPIRED || state == SuspensionState.LIFTED;
            case VOIDED -> state == SuspensionState.VOIDED;
            case ALL -> true;
        };
    }

    private static LocalDate start(SuspendMember request, LocalDate today) {
        return request.start() != null ? request.start() : today;
    }

    private Long actorId() {
        return currentMemberResolver.current().map(TMember::getId).orElse(null);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static @Nullable String strip(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return strip(value);
    }

    private static MandateRef mandateRef(TBodyMembership m) {
        TBody body = m.getBody();

        return new MandateRef(m.getId(), body.getId(), body.getName(), body.kind(),
            m.getMemberType().getCode(), m.getMemberType().getName(), m.getValidFrom(), m.getValidTo());
    }

    private static ArrearsCandidate candidate(MemberArrears m) {
        Arrears a = m.arrears();
        return new ArrearsCandidate(m.memberId(), m.districtId(), a.months(), a.since(), a.overdueDues());
    }

    private static Suspension map(ResultSet rs, int rowNum) throws SQLException {
        return new Suspension(
            rs.getLong("id"),
            rs.getLong("suspended_user_id"),
            SuspensionKind.valueOf(rs.getString("kind")),
            SuspensionScope.valueOf(rs.getString("scope")),
            rs.getString("scope_description"),
            rs.getLong("suspending_body_id"),
            rs.getString("body_name"),
            rs.getObject("suspension_start", LocalDate.class),
            rs.getObject("suspension_end", LocalDate.class),
            rs.getObject("decision_date", LocalDate.class),
            rs.getString("decision_signature"),
            rs.getObject("lifted_at", LocalDate.class),
            rs.getString("lifted_reason"),
            nullableLong(rs, "lifted_body"),
            rs.getString("lift_decision_signature"),
            rs.getObject("appeal_ended_on", LocalDate.class),
            rs.getString("appeal_ended_reference"),
            rs.getBoolean("voided"),
            instant(rs, "voided_at"),
            rs.getString("void_reason"),
            rs.getString("void_decision_reference"),
            instant(rs, "created_at")
        );
    }

    private static @Nullable Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
