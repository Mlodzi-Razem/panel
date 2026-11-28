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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Names;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.PageResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.PersonRef;
import org.mlodzirazem.panel.services.panelapi.api.v1.recruitment.RecruitmentResponses.CaseDetailResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.recruitment.RecruitmentResponses.CaseDecisionResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.recruitment.RecruitmentResponses.CaseEventResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.recruitment.RecruitmentResponses.CaseSummaryResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.recruitment.RecruitmentResponses.DistrictRef;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseEventType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentDecisionKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentSource;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecision;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentDecisionRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.recruitment.NewRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentAdmissionService;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCandidateNames;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseService;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.RecruitmentEformService;
import org.mlodzirazem.panel.services.panelapi.recruitment.retention.RecruitmentRetention;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentRules;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Intake of recruitment cases (M2): the district-board back-office. Permissions: RECRUITMENT_READ
 * lists and reads the caller's districts (the national board reads globally), RECRUITMENT_MANAGE
 * creates and edits in the caller's own district. The declaration clock and everything after the
 * declaration is milestone M3 and politely refuses here.
 */
@RestController
@RequestMapping("/recruitment-cases")
@RequiredArgsConstructor
@Transactional
public class RecruitmentCasesController {
    private static final int MAX_PAGE_SIZE = 100;

    private final CallerResolver callers;
    private final RecruitmentCaseService recruitment;
    private final RecruitmentWorkflowService workflow;
    private final RecruitmentAdmissionService admission;
    private final RecruitmentEformService eform;
    private final RecruitmentCaseSearch search;
    private final RecruitmentCandidateParser parser;
    private final TRecruitmentCaseRepository cases;
    private final TRecruitmentDecisionRepository decisions;
    private final TBodyRepository bodies;
    private final JsonMapper json;
    private final RecruitmentRetention retention;

    public record CreateCaseRequest(
        @Nullable Long districtBodyId,
        @Nullable RecruitmentSource source,
        @Nullable Map<String, JsonNode> candidate
    ) {
    }

    public record PatchCaseRequest(@Nullable Map<String, JsonNode> candidate) {
    }

    public record TransitionRequest(
        @Nullable RecruitmentCaseStatus target,
        @Nullable RecruitmentRejectionReason rejectionReason,
        @Nullable Long decidingBodyId,
        @Nullable LocalDate decidedOn,
        @Nullable String signature,
        @Nullable String note
    ) {
    }

    /** The two collegial resolutions the M3b back office records over HTTP. */
    public record DecisionRequest(
        @Nullable RecruitmentDecisionKind kind,
        @Nullable LocalDate decidedOn,
        @Nullable String signature,
        @Nullable String note
    ) {
    }

    public record NoteRequest(@Nullable String note) {
    }

    public record CandidateDataCorrectionRequest(
        @Nullable String formalFirstName,
        @Nullable String formalLastName,
        @Nullable String preferredFirstName,
        @Nullable String preferredLastName,
        @Nullable String pronouns,
        @Nullable String personalId,
        @Nullable String reason
    ) {
    }

    @GetMapping
    public PageResponse<CaseSummaryResponse> list(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size,
        @RequestParam(name = "district", required = false) @Nullable Long district,
        @RequestParam(name = "status", required = false) @Nullable RecruitmentCaseStatus status,
        @RequestParam(name = "open", required = false) @Nullable Boolean open,
        @RequestParam(name = "view", required = false) @Nullable RecruitmentCaseView view
    ) {
        Caller caller = callers.require();
        RecruitmentCaseSearch.Scope scope = new RecruitmentCaseSearch.Scope(
            caller.permissions().canGlobally(PermissionType.RECRUITMENT_READ),
            caller.permissions().districtsWith(PermissionType.RECRUITMENT_READ));

        if (scope.empty()) {
            throw new ForbiddenException("No permission to read recruitment cases");
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        RecruitmentCaseSearch.Result result = search.search(scope,
            new RecruitmentCaseSearch.Filter(district, status, open, view), safePage, safeSize);

        return PageResponse.of(summaries(result.ids()), safePage, safeSize, result.total());
    }

    /** Badge counters of the predefined list views, within the caller's read scope. */
    @GetMapping("/views")
    public Map<RecruitmentCaseView, Long> viewCounts(
        @RequestParam(name = "district", required = false) @Nullable Long district
    ) {
        Caller caller = callers.require();
        RecruitmentCaseSearch.Scope scope = new RecruitmentCaseSearch.Scope(
            caller.permissions().canGlobally(PermissionType.RECRUITMENT_READ),
            caller.permissions().districtsWith(PermissionType.RECRUITMENT_READ));

        if (scope.empty()) {
            throw new ForbiddenException("No permission to read recruitment cases");
        }

        return search.viewCounts(scope, district);
    }

    @GetMapping("/{id}")
    public CaseDetailResponse get(@PathVariable("id") long id) {
        Caller caller = callers.require();
        TRecruitmentCase candidateCase = recruitment.get(id);

        requireRead(caller, candidateCase);

        return detail(candidateCase, recruitment.timeline(id));
    }

    @GetMapping("/{id}/transitions")
    public RecruitmentResponses.CaseTransitionsResponse transitions(@PathVariable("id") long id) {
        Caller caller = callers.require();
        TRecruitmentCase candidateCase = recruitment.get(id);

        requireRead(caller, candidateCase);

        return new RecruitmentResponses.CaseTransitionsResponse(
            recruitment.availableTransitions(id).stream()
                .map(option -> new RecruitmentResponses.CaseTransitionResponse(
                    option.target(), RecruitmentCaseEventType.STATUS_CHANGED, option.allowed(),
                    option.rejectionReasons(), option.blockedReason()))
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseDetailResponse create(@RequestBody CreateCaseRequest request) {
        Caller caller = callers.require();

        if (request.districtBodyId() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "districtBodyId is required",
                List.of("districtBodyId"));
        }

        if (!caller.permissions().canInDistrict(PermissionType.RECRUITMENT_MANAGE, request.districtBodyId())) {
            throw new ForbiddenException("RECRUITMENT_MANAGE is required in the target district");
        }

        TRecruitmentCase candidateCase = recruitment.create(
            new NewRecruitmentCase(request.districtBodyId(), request.source(), parser.parse(request.candidate())),
            caller.id());

        return detail(candidateCase, recruitment.timeline(candidateCase.getId()));
    }

    @PatchMapping("/{id}")
    public CaseDetailResponse update(@PathVariable("id") long id, @RequestBody PatchCaseRequest request) {
        Caller caller = callers.require();
        TRecruitmentCase candidateCase = recruitment.get(id);

        requireManage(caller, candidateCase);

        recruitment.update(id, parser.parse(request.candidate()), caller.id());

        return detail(recruitment.get(id), recruitment.timeline(id));
    }

    @PostMapping("/{id}/transitions")
    public CaseDetailResponse transition(@PathVariable("id") long id, @RequestBody TransitionRequest request) {
        Caller caller = callers.require();

        if (request.target() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "target is required", List.of("target"));
        }

        TRecruitmentCase candidateCase = recruitment.get(id);

        boolean postDeclaration = RecruitmentRules.POST_DECLARATION_STATES.contains(candidateCase.getStatus());
        if (postDeclaration) {
            requireManageOrApprove(caller, candidateCase);
        } else {
            requireManage(caller, candidateCase);
        }
        requireOpen(candidateCase);
        requireReachable(candidateCase, request.target());
        requireNotReserved(request.target());

        if (request.target() == RecruitmentCaseStatus.ACCEPTED) {
            // M6a: the admission (number allocation + members row + status flip) in one transaction.
            admission.admit(id, caller.id());
        } else if (postDeclaration) {
            transitionPostDeclaration(candidateCase, caller.id(), request);
        } else {
            recruitment.transition(id, request.target(), request.rejectionReason(), caller.id());
        }

        return detail(recruitment.get(id), recruitment.timeline(id));
    }

    /**
     * Records one of the two collegial resolutions of the admission flow over the M3 engine:
     * {@code ZK_APPROVAL} (global RECRUITMENT_APPROVE, decided by the Zarząd Krajowy) or
     * {@code ZO_ADMISSION_RESOLUTION} (RECRUITMENT_MANAGE of the case's district, decided by its
     * Zarząd Okręgu). The deciding body is resolved server-side; a second decision of the same kind
     * answers 409 DECISION_ALREADY_RECORDED. The status flip to ACCEPTED is the admission transition (M6a).
     */
    @PostMapping("/{id}/decisions")
    public CaseDetailResponse recordDecision(@PathVariable("id") long id, @RequestBody DecisionRequest request) {
        Caller caller = callers.require();
        TRecruitmentCase candidateCase = recruitment.get(id);

        if (request.kind() != RecruitmentDecisionKind.ZK_APPROVAL
            && request.kind() != RecruitmentDecisionKind.ZO_ADMISSION_RESOLUTION) {
            throw ApiException.badRequest("INVALID_VALUE",
                "kind must be ZK_APPROVAL or ZO_ADMISSION_RESOLUTION", List.of("kind"));
        }

        if (request.kind() == RecruitmentDecisionKind.ZK_APPROVAL) {
            if (!caller.permissions().canGlobally(PermissionType.RECRUITMENT_APPROVE)) {
                throw new ForbiddenException("RECRUITMENT_APPROVE_REQUIRED",
                    "Recording a ZK opinion requires the global RECRUITMENT_APPROVE permission");
            }
            workflow.recordZkApproval(id, caller.id(), nationalBoard().getId(), request.decidedOn(),
                requireDecisionInput(request.signature(), request.decidedOn()), request.note());
        } else {
            requireManage(caller, candidateCase);
            workflow.recordAdmissionResolution(id, caller.id(), districtBoardOf(candidateCase).getId(),
                request.decidedOn(), requireDecisionInput(request.signature(), request.decidedOn()), request.note());
        }

        return detail(recruitment.get(id), recruitment.timeline(id));
    }

    /**
     * The declaration arrival (M4a, artifact-gated e-declaration) refuses with the exact code the
     * transitions helper shows, whatever the caller's permissions are.
     */
    private static void requireNotReserved(RecruitmentCaseStatus target) {
        if (target == RecruitmentCaseStatus.DECLARATION_RECEIVED) {
            throw new ConflictException("DECLARATION_ARTIFACT_REQUIRED",
                "The declaration arrives with its artifact in the M4a e-declaration flow");
        }
    }

    /**
     * The post-declaration edges the M3b back office drives over the M3 engine. The reserved flows
     * never reach this method ({@link #requireNotReserved} answers first).
     */
    private void transitionPostDeclaration(TRecruitmentCase candidateCase, long actorId,
                                          TransitionRequest request) {
        long id = candidateCase.getId();
        RecruitmentCaseStatus target = request.target();

        switch (target) {
            case PROBATION -> workflow.startProbation(id, actorId);
            case PROBATION_EXTENDED, ZO_RECOMMENDED -> {
                requireDecisionInput(request.signature(), request.decidedOn());
                requireBodyId(request.decidingBodyId());
                if (target == RecruitmentCaseStatus.PROBATION_EXTENDED) {
                    workflow.extendAssessment(id, actorId, request.decidingBodyId(), request.decidedOn(),
                        request.signature(), request.note());
                } else {
                    workflow.recordZoRecommendation(id, actorId, request.decidingBodyId(), request.decidedOn(),
                        request.signature(), request.note());
                }
            }
            case ZK_VOTING -> workflow.startZkVoting(id, actorId);
            case REJECTED -> {
                if (request.rejectionReason() == null) {
                    throw new RuleViolationException("REJECTION_REASON_REQUIRED",
                        "Closing a post-declaration case as REJECTED requires a rejection reason");
                }
                requireDecisionInput(request.signature(), request.decidedOn());
                requireBodyId(request.decidingBodyId());
                workflow.reject(id, actorId, request.decidingBodyId(), request.decidedOn(),
                    request.rejectionReason(), request.signature(), request.note());
            }
            default -> throw new RuleViolationException("TRANSITION_NOT_ALLOWED",
                "The transition " + candidateCase.getStatus().name() + " -> " + target.name()
                    + " is not served by the back office");
        }
    }

    /** Closed cases answer 409 before anything else, exactly like the M2 intake does. */
    private static void requireOpen(TRecruitmentCase candidateCase) {
        if (candidateCase.getClosedAt() != null) {
            throw new ConflictException("CASE_NOT_OPEN", "The case is closed; closed cases are read-only");
        }
    }

    /** Only the edges of {@link RecruitmentRules} are reachable; everything else answers 422. */
    private static void requireReachable(TRecruitmentCase candidateCase, RecruitmentCaseStatus target) {
        if (!RecruitmentRules.canTransition(candidateCase.getStatus(), target)) {
            throw new RuleViolationException("TRANSITION_NOT_ALLOWED",
                "The transition " + candidateCase.getStatus().name() + " -> " + target.name()
                    + " is not part of the state machine");
        }
    }

    private static String requireDecisionInput(@Nullable String signature, @Nullable LocalDate decidedOn) {
        if (decidedOn == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "decidedOn is required", List.of("decidedOn"));
        }
        if (signature == null || signature.isBlank()) {
            throw ApiException.badRequest("FIELD_REQUIRED", "signature is required", List.of("signature"));
        }
        return signature.strip();
    }

    private static void requireBodyId(@Nullable Long decidingBodyId) {
        if (decidingBodyId == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "decidingBodyId is required", List.of("decidingBodyId"));
        }
    }

    private static void requireManageOrApprove(Caller caller, TRecruitmentCase candidateCase) {
        boolean allowed = caller.permissions().canInDistrict(PermissionType.RECRUITMENT_MANAGE,
                candidateCase.getDistrict().getId())
            || caller.permissions().canGlobally(PermissionType.RECRUITMENT_APPROVE);

        if (!allowed) {
            throw new ForbiddenException("RECRUITMENT_MANAGE_REQUIRED",
                "Post-declaration transitions require RECRUITMENT_MANAGE of the case's district"
                    + " or the global RECRUITMENT_APPROVE");
        }
    }

    private TBody nationalBoard() {
        return bodies.findAllByKind(BodyKind.NATIONAL_BOARD).stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Body kind NATIONAL_BOARD", 0));
    }

    private TBody districtBoardOf(TRecruitmentCase candidateCase) {
        return bodies.findAllByParentBodyIdAndKind(candidateCase.getDistrict().getId(), BodyKind.DISTRICT_BOARD)
            .stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException("District board of " + candidateCase.getDistrict().getName(),
                candidateCase.getDistrict().getId()));
    }

    /** Reasoned correction of the candidate's formal data; the permission is checked by the e-form service. */
    @PostMapping("/{id}/candidate-data-corrections")
    public CaseDetailResponse correctCandidateData(
        @PathVariable("id") long id,
        @RequestBody CandidateDataCorrectionRequest request
    ) {
        Caller caller = callers.require();

        recruitment.get(id);
        eform.correctCandidateData(id, caller.id(), new RecruitmentEformService.CandidateDataCorrection(
            request.formalFirstName(), request.formalLastName(), request.preferredFirstName(),
            request.preferredLastName(), request.pronouns(), request.personalId(), request.reason()));

        return detail(recruitment.get(id), recruitment.timeline(id));
    }

    @PostMapping("/{id}/notes")
    public CaseDetailResponse addNote(@PathVariable("id") long id, @RequestBody NoteRequest request) {
        Caller caller = callers.require();
        TRecruitmentCase candidateCase = recruitment.get(id);

        requireManage(caller, candidateCase);

        recruitment.addNote(id, request.note() == null ? "" : request.note(), caller.id());

        return detail(recruitment.get(id), recruitment.timeline(id));
    }

    @PostMapping("/{id}/reminders")
    public CaseDetailResponse sendReminder(@PathVariable("id") long id) {
        Caller caller = callers.require();
        TRecruitmentCase candidateCase = recruitment.get(id);

        requireManage(caller, candidateCase);

        recruitment.sendReminder(id, caller.id());

        return detail(recruitment.get(id), recruitment.timeline(id));
    }

    private static void requireRead(Caller caller, TRecruitmentCase candidateCase) {
        boolean allowed = caller.permissions().canGlobally(PermissionType.RECRUITMENT_READ)
            || caller.permissions().canInDistrict(PermissionType.RECRUITMENT_READ,
            candidateCase.getDistrict().getId());

        if (!allowed) {
            throw new ForbiddenException("No permission to read this recruitment case");
        }
    }

    private static void requireManage(Caller caller, TRecruitmentCase candidateCase) {
        if (!caller.permissions().canInDistrict(PermissionType.RECRUITMENT_MANAGE,
            candidateCase.getDistrict().getId())) {
            throw new ForbiddenException("RECRUITMENT_MANAGE is required in the case's district");
        }
    }

    private List<CaseSummaryResponse> summaries(List<Long> ids) {
        Map<Long, TRecruitmentCase> byId = new HashMap<>();

        cases.findAllById(ids).forEach(candidateCase -> byId.put(candidateCase.getId(), candidateCase));

        return ids.stream()
                   .map(byId::get)
                   .filter(java.util.Objects::nonNull)
                   .map(this::summary)
                   .toList();
    }

    private CaseDetailResponse detail(TRecruitmentCase candidateCase, List<TRecruitmentCaseEvent> timeline) {
        RecruitmentCandidateNames.Display displayName = RecruitmentCandidateNames.display(candidateCase);

        return new CaseDetailResponse(
            candidateCase.getId(),
            district(candidateCase.getDistrict()),
            candidateCase.getCandidateFirstName(),
            candidateCase.getCandidateLastName(),
            candidateCase.getCandidateEmail(),
            candidateCase.getCandidatePhone(),
            candidateCase.getCandidateSocialProfileUrl(),
            candidateCase.getCandidateBirthDate(),
            candidateCase.getCandidateFormalFirstName(),
            candidateCase.getCandidateFormalLastName(),
            candidateCase.getCandidatePreferredFirstName(),
            candidateCase.getCandidatePreferredLastName(),
            candidateCase.getCandidatePronouns(),
            candidateCase.getCandidateFormalIdentifier() != null
                && !candidateCase.getCandidateFormalIdentifier().isBlank(),
            candidateCase.getStatus(),
            candidateCase.getSource(),
            candidateCase.getRejectionReason(),
            candidateCase.getClosedAt(),
            candidateCase.getCreatedAt(),
            candidateCase.getUpdatedAt(),
            candidateCase.getDeclarationForm(),
            candidateCase.getDeclarationReference(),
            candidateCase.getDeclarationReceivedOn(),
            candidateCase.getAssessmentTrack(),
            candidateCase.getAssessmentNotBeforeOn(),
            candidateCase.getAssessmentNotLaterOn(),
            candidateCase.getExtensionCount(),
            candidateCase.getExtensionDecidedOn(),
            candidateCase.getSecondAssessmentNotLaterOn(),
            candidateCase.getZoRecommendedOn(),
            candidateCase.getAdmissionResolutionNotLaterOn(),
            candidateCase.getAcceptedOn(),
            candidateCase.getAcceptedMember() == null ? null : candidateCase.getAcceptedMember().getId(),
            candidateCase.getAcceptedMember() == null ? null : candidateCase.getAcceptedMember().getMemberId(),
            decisions.findAllByRecruitmentCaseId(candidateCase.getId()).stream()
                .sorted(Comparator.comparing(TRecruitmentDecision::getDecidedOn)
                    .thenComparing(TRecruitmentDecision::getId))
                .map(this::decision)
                .toList(),
            timeline.stream().map(this::event).toList(),
            candidateCase.getAnonymizedAt(),
            candidateCase.getArtifactsPurgedAt(),
            artifactsDueOn(candidateCase),
            anonymizationDueOn(candidateCase),
            displayName.first(),
            displayName.last(),
            displayName.source(),
            RecruitmentCandidateNames.compare(candidateCase)
        );
    }

    private CaseDecisionResponse decision(TRecruitmentDecision decision) {
        return new CaseDecisionResponse(
            decision.getId(),
            decision.getDecisionKind(),
            decision.getDecidedOn(),
            decision.getDecisionSignature(),
            decision.getDecidingBody().getId(),
            decision.getDecidingBody().getName()
        );
    }

    private CaseSummaryResponse summary(TRecruitmentCase candidateCase) {
        RecruitmentCandidateNames.Display displayName = RecruitmentCandidateNames.display(candidateCase);

        return new CaseSummaryResponse(
            candidateCase.getId(),
            district(candidateCase.getDistrict()),
            candidateCase.getCandidateFirstName(),
            candidateCase.getCandidateLastName(),
            candidateCase.getCandidateEmail(),
            candidateCase.getCandidatePhone(),
            candidateCase.getStatus(),
            candidateCase.getSource(),
            candidateCase.getCreatedAt(),
            candidateCase.getUpdatedAt(),
            candidateCase.getClosedAt(),
            candidateCase.getRejectionReason(),
            candidateCase.getAnonymizedAt(),
            anonymizationDueOn(candidateCase),
            displayName.first(),
            displayName.last(),
            displayName.source()
        );
    }

    /** Retention dates exist for rejected cases only; accepted and open cases have no retention clock. */
    private @Nullable LocalDate anonymizationDueOn(TRecruitmentCase candidateCase) {
        return candidateCase.getStatus() == RecruitmentCaseStatus.REJECTED && candidateCase.getClosedAt() != null
            ? retention.caseDueOn(candidateCase.getClosedAt()) : null;
    }

    private @Nullable LocalDate artifactsDueOn(TRecruitmentCase candidateCase) {
        return candidateCase.getStatus() == RecruitmentCaseStatus.REJECTED && candidateCase.getClosedAt() != null
            ? retention.artifactsDueOn(candidateCase.getClosedAt()) : null;
    }

    private static DistrictRef district(TBody body) {
        return new DistrictRef(body.getId(), body.getName(), body.getCode());
    }

    private CaseEventResponse event(TRecruitmentCaseEvent eventCase) {
        String payload = eventCase.getPayload();

        return new CaseEventResponse(
            eventCase.getId(),
            eventCase.getEventType(),
            payload == null || payload.isBlank() ? null : json.readTree(payload),
            eventCase.getCreatedAt(),
            eventCase.getCreatedBy() == null ? null
                : new PersonRef(eventCase.getCreatedBy().getId(), Names.display(eventCase.getCreatedBy()))
        );
    }
}
