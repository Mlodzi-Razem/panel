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
import org.mlodzirazem.panel.services.panelapi.api.v1.common.PageResponse;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentIntakeDiscardReason;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentIntakeStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentIntake;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentIntakeRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.IntakeSubmission;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.RecruitmentIntakeFormService;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.RecruitmentIntakeQueueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * The public "I want to join" form and the queue it feeds.
 *
 * <p>Who sees a waiting submission: holders of RECRUITMENT_MANAGE its district, and holders of the global
 * RECRUITMENT_APPROVE (the national board), who see all of them, including those without a district. Only the
 * former may accept one as a case: the initial assessment belongs to the district board (statute art. 9 ust. 3).
 * Read-only recruitment access does not open the queue. A submission outside the caller's scope answers like a
 * missing one. The list carries no personal data.
 */
@RestController
@RequiredArgsConstructor
public class RecruitmentIntakeController {
    /** Set by panel-web only, and only behind a trusted proxy; the API is not reachable from outside. */
    static final String CLIENT_ADDRESS_HEADER = "X-Panel-Client-Address";
    private static final int MAX_PAGE_SIZE = 100;

    private final CallerResolver callers;
    private final RecruitmentIntakeFormService form;
    private final RecruitmentIntakeQueueService queue;
    private final TRecruitmentIntakeRepository intakes;
    private final TRecruitmentCaseRepository cases;

    public record IntakeRequest(
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String email,
        @Nullable String phone,
        @Nullable String birthDate,
        @Nullable Long districtBodyId,
        @Nullable String locality,
        @Nullable Boolean consent,
        @Nullable String consentVersion,
        @Nullable String formToken,
        @Nullable String website
    ) {
        @Override
        public String toString() {
            return "IntakeRequest[redacted]";
        }
    }

    public record ReceivedResponse(String status) {
    }

    public record DistrictRef(long id, String name) {
    }

    public record IntakeSummary(long id, ZonedDateTime receivedAt, RecruitmentIntakeStatus status,
                                @Nullable DistrictRef district,
                                @Nullable RecruitmentIntakeDiscardReason discardReason,
                                @Nullable ZonedDateTime closedAt, @Nullable Long caseId, boolean dataErased) {
    }

    public record IntakeActions(boolean accept, boolean assignDistrict, boolean discard, boolean erase) {
    }

    public record IntakeDetail(IntakeSummary summary, @Nullable String firstName, @Nullable String lastName,
                               @Nullable String email, @Nullable String phone, @Nullable String birthDate,
                               @Nullable String locality, String consentVersion, ZonedDateTime consentedAt,
                               boolean openCaseWithSameEmail, IntakeActions actions) {
    }

    public record DistrictRequest(@Nullable Long districtBodyId) {
    }

    public record DiscardRequest(@Nullable RecruitmentIntakeDiscardReason reason) {
    }

    public record AcceptedResponse(long caseId) {
    }

    @GetMapping(IntakeBodyLimitFilter.PATH)
    public RecruitmentIntakeFormService.Form openForm() {
        return form.open();
    }

    @PostMapping(IntakeBodyLimitFilter.PATH)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReceivedResponse submit(
        @RequestBody IntakeRequest request,
        @RequestHeader(name = CLIENT_ADDRESS_HEADER, required = false) @Nullable String clientAddress
    ) {
        form.submit(new IntakeSubmission(request.firstName(), request.lastName(), request.email(), request.phone(),
            request.birthDate(), request.districtBodyId(), request.locality(),
            Boolean.TRUE.equals(request.consent()), request.consentVersion(), request.formToken(),
            request.website(), clientAddress));

        return new ReceivedResponse("RECEIVED");
    }

    @GetMapping("/recruitment-intakes")
    @Transactional(readOnly = true)
    public PageResponse<IntakeSummary> list(
        @RequestParam(name = "status", defaultValue = "PENDING") RecruitmentIntakeStatus status,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "25") int size
    ) {
        Caller caller = requireQueueAccess();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        // oldest first: they are the closest to expiring
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt", "id"));

        Page<TRecruitmentIntake> result = isTriage(caller)
            ? intakes.findByStatus(status, pageRequest)
            : intakes.findByStatusAndDistrictIdIn(status,
                caller.permissions().districtsWith(PermissionType.RECRUITMENT_MANAGE), pageRequest);

        List<IntakeSummary> items = result.getContent().stream().map(RecruitmentIntakeController::summary).toList();

        return PageResponse.of(items, safePage, safeSize, result.getTotalElements());
    }

    @GetMapping("/recruitment-intakes/{id}")
    @Transactional(readOnly = true)
    public IntakeDetail get(@PathVariable("id") long id) {
        Caller caller = requireQueueAccess();
        TRecruitmentIntake intake = visible(caller, id);

        return detail(caller, intake);
    }

    @PostMapping("/recruitment-intakes/{id}/district")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignDistrict(@PathVariable("id") long id, @RequestBody DistrictRequest request) {
        Caller caller = requireQueueAccess();
        visible(caller, id);

        if (request.districtBodyId() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "districtBodyId is required",
                List.of("districtBodyId"));
        }

        queue.assignDistrict(id, request.districtBodyId(), caller.id());
    }

    @PostMapping("/recruitment-intakes/{id}/accept")
    public AcceptedResponse accept(@PathVariable("id") long id) {
        Caller caller = requireQueueAccess();
        TRecruitmentIntake intake = visible(caller, id);
        Long districtId = districtId(intake);

        // without a district nobody can accept; the service answers DISTRICT_REQUIRED
        if (districtId != null && !caller.permissions().canInDistrict(PermissionType.RECRUITMENT_MANAGE,
            districtId)) {
            throw new ForbiddenException("RECRUITMENT_MANAGE is required in the submission's district");
        }

        return new AcceptedResponse(queue.accept(id, caller.id()));
    }

    @PostMapping("/recruitment-intakes/{id}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable("id") long id, @RequestBody DiscardRequest request) {
        Caller caller = requireQueueAccess();
        visible(caller, id);

        if (request.reason() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "reason is required", List.of("reason"));
        }

        queue.discard(id, request.reason(), caller.id());
    }

    @PostMapping("/recruitment-intakes/{id}/erase")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable("id") long id) {
        Caller caller = requireQueueAccess();
        visible(caller, id);

        queue.erase(id, caller.id());
    }

    private Caller requireQueueAccess() {
        Caller caller = callers.require();

        if (!isTriage(caller) && caller.permissions().districtsWith(PermissionType.RECRUITMENT_MANAGE).isEmpty()) {
            throw new ForbiddenException("No permission to handle recruitment submissions");
        }

        return caller;
    }

    private static boolean isTriage(Caller caller) {
        return caller.permissions().canGlobally(PermissionType.RECRUITMENT_APPROVE);
    }

    private TRecruitmentIntake visible(Caller caller, long id) {
        return intakes.findById(id)
            .filter(intake -> {
                Long districtId = districtId(intake);
                return isTriage(caller) || districtId != null
                    && caller.permissions().canInDistrict(PermissionType.RECRUITMENT_MANAGE, districtId);
            })
            .orElseThrow(() -> new NotFoundException("RecruitmentIntake", id));
    }

    private static @Nullable Long districtId(TRecruitmentIntake intake) {
        TBody district = intake.getDistrict();
        // the identifier is available on an uninitialised proxy, outside a session too
        return district == null ? null : district.getId();
    }

    private IntakeDetail detail(Caller caller, TRecruitmentIntake intake) {
        boolean pending = intake.getStatus() == RecruitmentIntakeStatus.PENDING;
        Long districtId = districtId(intake);
        boolean accept = pending && districtId != null
            && caller.permissions().canInDistrict(PermissionType.RECRUITMENT_MANAGE, districtId);
        boolean erase = intake.getStatus() == RecruitmentIntakeStatus.DISCARDED && intake.getDataErasedAt() == null;

        return new IntakeDetail(summary(intake), intake.getFirstName(), intake.getLastName(), intake.getEmail(),
            intake.getPhone(), intake.getBirthDate() == null ? null : intake.getBirthDate().toString(),
            intake.getLocality(), intake.getConsentVersion(), intake.getConsentedAt(),
            hasReadableOpenCase(caller, intake.getEmail()),
            new IntakeActions(accept, pending, pending, erase));
    }

    /** A duplicate hint limited to the cases the caller may read anyway; members and bans are never consulted. */
    private boolean hasReadableOpenCase(Caller caller, @Nullable String email) {
        if (email == null) {
            return false;
        }

        if (caller.permissions().canGlobally(PermissionType.RECRUITMENT_READ)) {
            return cases.existsOpenByEmail(email);
        }

        Set<Long> readable = caller.permissions().districtsWith(PermissionType.RECRUITMENT_READ);

        return !readable.isEmpty() && cases.existsOpenByEmailInDistricts(email, readable);
    }

    private static IntakeSummary summary(TRecruitmentIntake intake) {
        TBody district = intake.getDistrict();

        return new IntakeSummary(intake.getId(), intake.getCreatedAt(), intake.getStatus(),
            district == null ? null : new DistrictRef(district.getId(), district.getName()),
            intake.getDiscardReason(), intake.getClosedAt(), intake.getCaseId(), intake.getDataErasedAt() != null);
    }
}
