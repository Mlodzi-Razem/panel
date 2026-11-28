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

package org.mlodzirazem.panel.services.panelapi.api.v1.members;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.PageResponse;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.fees.ArrearsService;
import org.mlodzirazem.panel.services.panelapi.fees.DistrictScope;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.ElectoralRightsService;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.SuspensionRegistry;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.suspensions.ArrearsCandidate;
import org.mlodzirazem.panel.services.panelapi.suspensions.EndSuspendedMandates;
import org.mlodzirazem.panel.services.panelapi.suspensions.LiftSuspension;
import org.mlodzirazem.panel.services.panelapi.suspensions.PositionLoss;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspendMember;
import org.mlodzirazem.panel.services.panelapi.suspensions.Suspension;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionCheck;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionFilter;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionKind;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionOptions;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionResult;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionRow;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionRowsPage;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionScope;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionService;
import org.mlodzirazem.panel.services.panelapi.suspensions.SuspensionState;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Suspensions of membership rights (art. 10 statutu). Reading needs SUSPENSIONS_READ, recording and lifting need
 * SUSPENSIONS_MANAGE, both per the member's primary district (global for the ZK). Ending the mandates also needs
 * BODIES_MANAGE, because it closes functions.
 */
@RestController
@RequiredArgsConstructor
@Transactional
public class SuspensionsController {
    private final CallerResolver callers;
    private final SuspensionService suspensions;
    private final SuspensionRegistry registry;
    private final ElectoralRightsService electoralRights;
    private final MemberService members;
    private final AffiliationService affiliations;
    private final MemberViews views;
    private final TBodyRepository bodies;
    private final ArrearsService arrears;
    private final Clock clock;

    public record CreateSuspensionRequest(
        @Nullable String kind,
        @Nullable String scope,
        @Nullable String scopeDescription,
        @Nullable Long suspendingBodyId,
        @Nullable LocalDate start,
        @Nullable LocalDate end,
        @Nullable LocalDate decisionDate,
        @Nullable String decisionSignature,
        @Nullable List<String> confirmedWarnings
    ) {
    }

    public record LiftRequest(
        @Nullable LocalDate liftedOn,
        @Nullable String reason,
        @Nullable Long liftingBodyId,
        @Nullable String decisionSignature,
        @Nullable List<String> confirmedWarnings
    ) {
    }

    public record AppealEndedRequest(@Nullable LocalDate endedOn, @Nullable String decisionReference) {
    }

    public record VoidRequest(@Nullable String reason, @Nullable String decisionReference) {
    }

    public record EndMandatesRequest(@Nullable List<Long> membershipIds, @Nullable String decisionReference) {
    }

    public record Effects(boolean votingSuspended, boolean passiveRightSuspended, boolean competencesSuspended) {
    }

    public record Overview(List<SuspensionResponse> items, Effects effects) {
    }

    public record LiftedView(LocalDate on, String reason, long bodyId, @Nullable String bodyName,
                             @Nullable String decisionSignature) {
    }

    public record VoidedView(Instant at, String reason, @Nullable String decisionReference) {
    }

    public record AppealView(LocalDate on, String reference) {
    }

    public record ArrearsHint(int months, boolean settled) {
    }

    public record SuspensionResponse(
        long id,
        long memberId,
        SuspensionKind kind,
        SuspensionScope scope,
        @Nullable String scopeDescription,
        long suspendingBodyId,
        @Nullable String suspendingBodyName,
        LocalDate start,
        @Nullable LocalDate end,
        @Nullable LocalDate decisionDate,
        SuspensionState state,
        @Nullable String decisionSignature,
        @Nullable LiftedView lifted,
        @Nullable VoidedView voided,
        @Nullable AppealView appealEnded,
        PositionLoss positionLoss,
        @Nullable ArrearsHint arrearsHint,
        Instant createdAt
    ) {
    }

    public record SuspensionRowResponse(
        long id,
        long memberId,
        @Nullable String memberName,
        @Nullable Long districtId,
        SuspensionKind kind,
        SuspensionScope scope,
        SuspensionState state,
        LocalDate start,
        @Nullable LocalDate end,
        @Nullable String suspendingBodyName,
        boolean needsAction
    ) {
    }

    public record ArrearsCandidateResponse(
        long memberId,
        @Nullable String memberName,
        @Nullable Long districtId,
        int months,
        @Nullable LocalDate since,
        int overdueDues
    ) {
    }

    @GetMapping("/members/{id}/suspensions")
    public Overview history(@PathVariable("id") long memberId) {
        Caller caller = callers.require();
        requireRead(caller, memberId);

        LocalDate today = today();
        List<SuspensionResponse> items = suspensions.historyOf(memberId)
                                                    .stream()
                                                    .map(s -> response(caller, s, today))
                                                    .toList();

        boolean competences = registry.competencesSuspended(memberId, today);
        Effects effects = new Effects(electoralRights.rightsOf(memberId).suspended(), competences, competences);

        return new Overview(items, effects);
    }

    @GetMapping("/members/{id}/suspension-options")
    public SuspensionOptions options(@PathVariable("id") long memberId) {
        Caller caller = callers.require();
        requireManage(caller, memberId);

        return suspensions.optionsFor(memberId);
    }

    @PostMapping("/members/{id}/suspensions/check")
    public SuspensionCheck check(@PathVariable("id") long memberId, @RequestBody CreateSuspensionRequest request) {
        Caller caller = callers.require();
        requireManage(caller, memberId);

        return suspensions.check(toCommand(memberId, request));
    }

    @PostMapping("/members/{id}/suspensions")
    @ResponseStatus(HttpStatus.CREATED)
    public SuspensionResponse create(@PathVariable("id") long memberId, @RequestBody CreateSuspensionRequest request) {
        Caller caller = callers.require();
        requireManage(caller, memberId);

        SuspensionResult result = suspensions.suspend(toCommand(memberId, request));

        return response(caller, result.suspension(), today(), result.positionLoss());
    }

    @PostMapping("/members/{id}/suspensions/{sid}/lift")
    public SuspensionResponse lift(
        @PathVariable("id") long memberId,
        @PathVariable("sid") long suspensionId,
        @RequestBody LiftRequest request
    ) {
        Caller caller = callers.require();
        requireManage(caller, memberId);
        owned(memberId, suspensionId);

        Suspension lifted = suspensions.lift(suspensionId, new LiftSuspension(
            request.liftedOn(),
            request.reason(),
            required(request.liftingBodyId(), "liftingBodyId"),
            request.decisionSignature(),
            confirmed(request.confirmedWarnings())
        ));

        return response(caller, lifted, today());
    }

    @PostMapping("/members/{id}/suspensions/{sid}/appeal-ended")
    public SuspensionResponse appealEnded(
        @PathVariable("id") long memberId,
        @PathVariable("sid") long suspensionId,
        @RequestBody AppealEndedRequest request
    ) {
        Caller caller = callers.require();
        requireManage(caller, memberId);
        owned(memberId, suspensionId);

        Suspension updated = suspensions.recordAppealEnded(suspensionId,
            required(request.endedOn(), "endedOn"),
            request.decisionReference() == null ? "" : request.decisionReference());

        return response(caller, updated, today());
    }

    @PostMapping("/members/{id}/suspensions/{sid}/void")
    public SuspensionResponse voidSuspension(
        @PathVariable("id") long memberId,
        @PathVariable("sid") long suspensionId,
        @RequestBody VoidRequest request
    ) {
        Caller caller = callers.require();
        requireManage(caller, memberId);
        owned(memberId, suspensionId);

        Suspension voided = suspensions.voidSuspension(suspensionId,
            request.reason() == null ? "" : request.reason(),
            request.decisionReference() == null ? "" : request.decisionReference());

        return response(caller, voided, today());
    }

    @GetMapping("/members/{id}/suspensions/{sid}/position-loss")
    public PositionLoss positionLoss(@PathVariable("id") long memberId, @PathVariable("sid") long suspensionId) {
        Caller caller = callers.require();
        requireRead(caller, memberId);
        owned(memberId, suspensionId);

        return suspensions.positionLoss(suspensionId);
    }

    @PostMapping("/members/{id}/suspensions/{sid}/end-mandates")
    public PositionLoss endMandates(
        @PathVariable("id") long memberId,
        @PathVariable("sid") long suspensionId,
        @RequestBody EndMandatesRequest request
    ) {
        Caller caller = callers.require();
        requireManage(caller, memberId);
        callers.requireBodiesManager();
        owned(memberId, suspensionId);

        return suspensions.endMandates(suspensionId, new EndSuspendedMandates(
            request.membershipIds() == null ? Set.of() : Set.copyOf(request.membershipIds()),
            request.decisionReference() == null ? "" : request.decisionReference()
        ));
    }

    @GetMapping("/suspensions")
    public PageResponse<SuspensionRowResponse> list(
        @RequestParam(name = "state", defaultValue = "OPEN") String state,
        @RequestParam(name = "kind", required = false) @Nullable String kind,
        @RequestParam(name = "districtId", required = false) @Nullable Long districtId,
        @RequestParam(name = "needsAction", defaultValue = "false") boolean needsAction,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size
    ) {
        Caller caller = callers.require();
        DistrictScope scope = readScope(caller, districtId);

        SuspensionFilter filter = new SuspensionFilter(
            parse(SuspensionFilter.StateFilter.class, state, "state"),
            kind == null ? null : parse(SuspensionKind.class, kind, "kind"),
            districtId,
            needsAction
        );

        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        SuspensionRowsPage result = suspensions.list(filter, scope, safePage, safeSize);

        LocalDate today = today();
        List<SuspensionRowResponse> items = result.items()
                                                  .stream()
                                                  .map(row -> row(caller, row, today))
                                                  .toList();

        return PageResponse.of(items, safePage, safeSize, result.totalItems());
    }

    @GetMapping("/suspensions/arrears-candidates")
    public List<ArrearsCandidateResponse> arrearsCandidates(
        @RequestParam(name = "districtId", required = false) @Nullable Long districtId
    ) {
        Caller caller = callers.require();

        if (!caller.permissions().can(PermissionType.FEES_READ)) {
            throw new ForbiddenException("FEES_READ is required");
        }

        DistrictScope scope = readScope(caller, districtId);

        return suspensions.arrearsCandidates(intersectWithFees(caller, scope))
                          .stream()
                          .map(c -> candidate(caller, c))
                          .toList();
    }

    // ---- access ----

    private void requireRead(Caller caller, long memberId) {
        if (!caller.permissions().canForMember(PermissionType.SUSPENSIONS_READ, affiliations.districtsOf(memberId))) {
            throw new ForbiddenException("SUSPENSIONS_READ does not cover this member");
        }
    }

    private void requireManage(Caller caller, long memberId) {
        if (!caller.permissions().canForMember(PermissionType.SUSPENSIONS_MANAGE, affiliations.districtsOf(memberId))) {
            throw new ForbiddenException("SUSPENSIONS_MANAGE does not cover this member");
        }
    }

    private DistrictScope readScope(Caller caller, @Nullable Long districtId) {
        var p = caller.permissions();

        if (p.canGlobally(PermissionType.SUSPENSIONS_READ)) {
            return districtId == null ? DistrictScope.all() : DistrictScope.of(List.of(districtId));
        }

        Set<Long> own = p.districtsWith(PermissionType.SUSPENSIONS_READ);

        if (own.isEmpty() || (districtId != null && !own.contains(districtId))) {
            throw new ForbiddenException("SUSPENSIONS_READ does not cover this district");
        }

        return DistrictScope.of(districtId == null ? own : Set.of(districtId));
    }

    private DistrictScope intersectWithFees(Caller caller, DistrictScope scope) {
        var p = caller.permissions();

        if (p.canGlobally(PermissionType.FEES_READ)) {
            return scope;
        }

        Set<Long> fees = p.districtsWith(PermissionType.FEES_READ);

        if (scope.districtIds() == null) {
            return DistrictScope.of(fees);
        }

        return DistrictScope.of(scope.districtIds().stream().filter(fees::contains).collect(Collectors.toSet()));
    }

    /** The suspension must belong to the member named in the path; anything else is not found. */
    private void owned(long memberId, long suspensionId) {
        Suspension suspension = suspensions.get(suspensionId);

        if (suspension.memberId() != memberId) {
            throw new NotFoundException("Suspension", suspensionId);
        }
    }

    private boolean canSeeName(Caller caller, long memberId) {
        return views.visibility(caller, affiliations.districtsOf(memberId), false).canRead();
    }

    private @Nullable String memberName(Caller caller, long memberId) {
        if (!canSeeName(caller, memberId)) {
            return null;
        }

        return members.get(memberId).getPreferredName();
    }

    // ---- views ----

    private SuspensionResponse response(Caller caller, Suspension s, LocalDate today) {
        return response(caller, s, today, suspensions.positionLoss(s.id()));
    }

    private SuspensionResponse response(Caller caller, Suspension s, LocalDate today, PositionLoss loss) {
        LiftedView lifted = s.liftedOn() == null ? null : new LiftedView(
            s.liftedOn(), s.liftedReason(), s.liftedBodyId() == null ? 0 : s.liftedBodyId(),
            s.liftedBodyId() == null ? null : bodyName(s.liftedBodyId()), s.liftDecisionSignature());
        VoidedView voided = !s.voided() ? null : new VoidedView(s.voidedAt(), s.voidReason(), s.voidDecisionReference());
        AppealView appeal = s.appealEndedOn() == null ? null : new AppealView(s.appealEndedOn(), s.appealEndedReference());

        return new SuspensionResponse(
            s.id(), s.memberId(), s.kind(), s.scope(), s.scopeDescription(), s.suspendingBodyId(),
            s.suspendingBodyName(), s.start(), s.end(), s.decisionDate(), s.state(today), s.decisionSignature(),
            lifted, voided, appeal, loss, arrearsHint(caller, s, today), s.createdAt());
    }

    private @Nullable ArrearsHint arrearsHint(Caller caller, Suspension s, LocalDate today) {
        SuspensionState state = s.state(today);

        if (s.kind() != SuspensionKind.ARREARS
            || (state != SuspensionState.ACTIVE && state != SuspensionState.SCHEDULED)
            || !caller.permissions().canForMember(PermissionType.FEES_READ, affiliations.districtsOf(s.memberId()))) {
            return null;
        }

        var current = arrears.of(s.memberId());
        return new ArrearsHint(current.months(), current.overdueDues() == 0);
    }

    private SuspensionRowResponse row(Caller caller, SuspensionRow row, LocalDate today) {
        Suspension s = row.suspension();

        return new SuspensionRowResponse(
            s.id(), s.memberId(), memberName(caller, s.memberId()), row.districtId(), s.kind(), s.scope(),
            s.state(today), s.start(), s.end(), s.suspendingBodyName(), row.needsAction()
        );
    }

    private ArrearsCandidateResponse candidate(Caller caller, ArrearsCandidate c) {
        return new ArrearsCandidateResponse(c.memberId(), memberName(caller, c.memberId()), c.districtId(), c.months(),
            c.since(), c.overdueDues());
    }

    private @Nullable String bodyName(long bodyId) {
        return bodies.findById(bodyId).map(b -> b.getName()).orElse(null);
    }

    // ---- parsing ----

    private SuspendMember toCommand(long memberId, CreateSuspensionRequest request) {
        return new SuspendMember(
            memberId,
            parse(SuspensionKind.class, required(request.kind(), "kind"), "kind"),
            parse(SuspensionScope.class, required(request.scope(), "scope"), "scope"),
            request.scopeDescription(),
            required(request.suspendingBodyId(), "suspendingBodyId"),
            request.start(),
            request.end(),
            request.decisionDate(),
            request.decisionSignature(),
            confirmed(request.confirmedWarnings())
        );
    }

    private static Set<String> confirmed(@Nullable List<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", field + " is required", List.of(field));
        }

        return value;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException unknown) {
            throw ApiException.badRequest("INVALID_VALUE", "Unknown value of " + field, List.of(field));
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
