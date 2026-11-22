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

package org.mlodzirazem.panel.services.panelapi.api.v1.bodies;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.bodies.BodyResponses.AssignmentResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.bodies.BodyResponses.BodyDetailResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.bodies.BodyResponses.BodyResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.bodies.BodyResponses.CompositionEntry;
import org.mlodzirazem.panel.services.panelapi.api.v1.bodies.BodyResponses.MemberTypeResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.bodies.BodyResponses.MembershipResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.bodies.BodyResponses.TemplateResponse;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.ApiException;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.CallerResolver;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Names;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignMembership;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignmentCheck;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignmentResult;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipService;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyService;
import org.mlodzirazem.panel.services.panelapi.bodies.NewBody;
import org.mlodzirazem.panel.services.panelapi.bodies.UpdateBody;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplate;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/bodies")
@RequiredArgsConstructor
@Transactional
public class BodiesController {
    private final CallerResolver callers;
    private final BodyService bodies;
    private final BodyMembershipService bodyMemberships;
    private final TBodyMembershipRepository membershipRepository;
    private final Clock clock;

    public record CreateBodyRequest(
        @Nullable Long templateId,
        @Nullable String name,
        @Nullable String email,
        @Nullable Long parentBodyId,
        @Nullable String code
    ) {
    }

    public record UpdateBodyRequest(@Nullable String name, @Nullable String email) {
    }

    public record AssignRequest(
        @Nullable Long memberId,
        @Nullable Long memberTypeId,
        @Nullable LocalDate validFrom,
        @Nullable LocalDate validTo,
        @Nullable Boolean confirmed
    ) {
    }

    @GetMapping
    public List<BodyResponse> list() {
        callers.require();

        return bodies.all().stream().map(BodiesController::body).toList();
    }

    @GetMapping("/templates")
    public List<TemplateResponse> templates() {
        callers.require();

        return bodies.templates().stream().map(this::template).toList();
    }

    @GetMapping("/{id}")
    public BodyDetailResponse get(@PathVariable("id") long id) {
        callers.require();
        TBody body = bodies.get(id);

        List<CompositionEntry> composition = bodyMemberships.activeIn(id)
            .stream()
            .map(m -> new CompositionEntry(m.getId(), m.getMember().getId(), Names.display(m.getMember()),
                m.getMemberType().getId(), m.getMemberType().getName(), m.getValidFrom(), m.getValidTo()))
            .toList();

        List<MemberTypeResponse> memberTypes = body.getBodyTemplate() == null
            ? List.of()
            : bodies.memberTypes(body.getBodyTemplate().getId()).stream().map(BodiesController::memberType).toList();

        return new BodyDetailResponse(body(body), composition, memberTypes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BodyResponse create(@RequestBody CreateBodyRequest request) {
        callers.requireBodiesManager();

        if (request.templateId() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "templateId is required", List.of("templateId"));
        }

        return body(bodies.create(new NewBody(request.templateId(), request.name() == null ? "" : request.name(),
            request.email(), request.parentBodyId(), request.code())));
    }

    @PatchMapping("/{id}")
    public BodyResponse update(@PathVariable("id") long id, @RequestBody UpdateBodyRequest request) {
        callers.requireBodiesManager();

        return body(bodies.update(id, new UpdateBody(request.name(), request.email())));
    }

    @GetMapping("/{id}/memberships")
    public List<MembershipResponse> memberships(@PathVariable("id") long id) {
        callers.requireBodiesManager();
        bodies.get(id);

        return bodyMemberships.activeIn(id).stream().map(BodiesController::membership).toList();
    }

    @PostMapping("/{id}/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse assign(@PathVariable("id") long id, @RequestBody AssignRequest request) {
        callers.requireBodiesManager();

        AssignmentResult result = bodyMemberships.assign(assignment(id, request), Boolean.TRUE.equals(request.confirmed()));

        return new AssignmentResponse(
            membership(result.membership()),
            result.endedMemberships().stream().map(BodiesController::membership).toList(),
            result.check()
        );
    }

    @PostMapping("/{id}/memberships/check")
    public AssignmentCheck check(@PathVariable("id") long id, @RequestBody AssignRequest request) {
        callers.requireBodiesManager();

        return bodyMemberships.check(assignment(id, request));
    }

    @DeleteMapping("/{id}/memberships/{membershipId}")
    public MembershipResponse end(
        @PathVariable("id") long id,
        @PathVariable("membershipId") long membershipId,
        @RequestParam(name = "endDate", required = false) @Nullable LocalDate endDate,
        @RequestParam(name = "decisionReference", required = false) @Nullable String decisionReference
    ) {
        callers.requireBodiesManager();

        TBodyMembership existing = membershipRepository.findById(membershipId)
                                                       .filter(m -> m.getBody().getId() == id)
                                                       .orElseThrow(() -> new NotFoundException("Membership",
                                                           membershipId));

        return membership(bodyMemberships.end(existing.getId(), endDate, decisionReference));
    }

    private AssignMembership assignment(long bodyId, AssignRequest request) {
        if (request.memberId() == null || request.memberTypeId() == null) {
            throw ApiException.badRequest("FIELD_REQUIRED", "memberId and memberTypeId are required",
                List.of("memberId", "memberTypeId"));
        }

        LocalDate from = request.validFrom() != null ? request.validFrom() : LocalDate.now(clock);

        return new AssignMembership(request.memberId(), bodyId, request.memberTypeId(), from, request.validTo());
    }

    private TemplateResponse template(TBodyTemplate t) {
        return new TemplateResponse(
            t.getId(), t.getName(), t.getKind(), t.getScope(),
            t.getParentTemplate() == null ? null : t.getParentTemplate().getId(),
            t.getMaxConsecutiveTerms(), t.getTermLengthMonths(), t.getMaxTermMonths(), t.getMaxExtensions(),
            bodies.memberTypes(t.getId()).stream().map(BodiesController::memberType).toList()
        );
    }

    private static MemberTypeResponse memberType(TBodyTemplateMemberType t) {
        return new MemberTypeResponse(t.getId(), t.getName(), t.getCode(), t.isDefaultType());
    }

    private static BodyResponse body(TBody b) {
        TBodyTemplate template = b.getBodyTemplate();

        return new BodyResponse(
            b.getId(), b.getName(), b.getCode(), b.getEmail(),
            template == null ? null : template.getKind(),
            template == null ? null : template.getScope(),
            template == null ? null : template.getId(),
            template == null ? null : template.getName(),
            b.getParentBody() == null ? null : b.getParentBody().getId()
        );
    }

    private static MembershipResponse membership(TBodyMembership m) {
        return new MembershipResponse(m.getId(), m.getBody().getId(), m.getMember().getId(),
            Names.display(m.getMember()), m.getMemberType().getId(), m.getMemberType().getName(),
            m.getValidFrom(), m.getValidTo());
    }
}
