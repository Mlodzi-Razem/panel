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
import org.mlodzirazem.panel.services.panelapi.api.v1.recruitment.RecruitmentResponses.CaseEventResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.recruitment.RecruitmentResponses.CaseSummaryResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.recruitment.RecruitmentResponses.DistrictRef;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentCaseStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentRejectionReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentSource;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseEvent;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.recruitment.NewRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.recruitment.RecruitmentCaseService;
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
    private final RecruitmentCaseSearch search;
    private final RecruitmentCandidateParser parser;
    private final TRecruitmentCaseRepository cases;
    private final JsonMapper json;

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
        @Nullable RecruitmentRejectionReason rejectionReason
    ) {
    }

    public record NoteRequest(@Nullable String note) {
    }

    @GetMapping
    public PageResponse<CaseSummaryResponse> list(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size,
        @RequestParam(name = "district", required = false) @Nullable Long district,
        @RequestParam(name = "status", required = false) @Nullable RecruitmentCaseStatus status,
        @RequestParam(name = "open", required = false) @Nullable Boolean open
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
            new RecruitmentCaseSearch.Filter(district, status, open), safePage, safeSize);

        return PageResponse.of(summaries(result.ids()), safePage, safeSize, result.total());
    }

    @GetMapping("/{id}")
    public CaseDetailResponse get(@PathVariable("id") long id) {
        Caller caller = callers.require();
        TRecruitmentCase candidateCase = recruitment.get(id);

        requireRead(caller, candidateCase);

        return detail(candidateCase, recruitment.timeline(id));
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

        requireManage(caller, candidateCase);

        recruitment.transition(id, request.target(), request.rejectionReason(), caller.id());

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
        return new CaseDetailResponse(
            candidateCase.getId(),
            district(candidateCase.getDistrict()),
            candidateCase.getCandidateFirstName(),
            candidateCase.getCandidateLastName(),
            candidateCase.getCandidateEmail(),
            candidateCase.getCandidatePhone(),
            candidateCase.getCandidateSocialProfileUrl(),
            candidateCase.getCandidateBirthDate(),
            candidateCase.getStatus(),
            candidateCase.getSource(),
            candidateCase.getRejectionReason(),
            candidateCase.getClosedAt(),
            candidateCase.getCreatedAt(),
            candidateCase.getUpdatedAt(),
            timeline.stream().map(this::event).toList()
        );
    }

    private CaseSummaryResponse summary(TRecruitmentCase candidateCase) {
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
            candidateCase.getRejectionReason()
        );
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
