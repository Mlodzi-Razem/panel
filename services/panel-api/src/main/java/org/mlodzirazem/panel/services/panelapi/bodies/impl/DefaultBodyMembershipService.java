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

package org.mlodzirazem.panel.services.panelapi.bodies.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignMembership;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignmentBlockedException;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignmentCheck;
import org.mlodzirazem.panel.services.panelapi.bodies.AssignmentResult;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipChanged;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipService;
import org.mlodzirazem.panel.services.panelapi.bodies.ConfirmationRequiredException;
import org.mlodzirazem.panel.services.panelapi.bodies.FunctionViolation;
import org.mlodzirazem.panel.services.panelapi.bodies.FunctionWarning;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ConstraintConflicts;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.BodiesManagerGuard;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyMembershipStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplate;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateIncompatibility;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateIncompatibilityRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberTypeRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DefaultBodyMembershipService implements BodyMembershipService {
    /**
     * The partial unique index on open mandates (body_memberships_open_unique_idx) is the database's
     * last line of defence for one-open-mandate-per-(member, body, memberType); races surface here
     * as 23505 and are translated into a friendly 409 instead of a generic conflict.
     */
    private static final Map<String, RuntimeException> OPEN_DUPLICATE_INDEXES = Map.of(
        "body_memberships_open_unique_idx",
        new ConflictException("OPEN_MEMBERSHIP_EXISTS",
            "The member already holds an open mandate for this function in the body",
            List.of("memberId", "memberTypeId"))
    );

    private final TBodyMembershipRepository memberships;
    private final TBodyRepository bodies;
    private final TBodyTemplateMemberTypeRepository memberTypes;
    private final TBodyTemplateIncompatibilityRepository incompatibilities;
    private final TMemberRepository members;
    private final CurrentMemberResolver currentMemberResolver;
    private final BodiesManagerGuard bodiesManagerGuard;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AssignmentCheck check(AssignMembership request) {
        return evaluate(request).check();
    }

    @Override
    @Transactional
    public AssignmentResult assign(AssignMembership request, boolean confirmed) {
        bodiesManagerGuard.lock();

        TMember actor = currentMemberResolver.current().orElse(null);

        if (actor != null && actor.getId().equals(request.memberId())) {
            throw new RuleViolationException(
                "SELF_ASSIGNMENT_NOT_ALLOWED",
                "A function cannot be assigned to oneself; another member with BODIES_MANAGE has to record the decision");
        }

        Evaluation evaluation = evaluate(request);
        AssignmentCheck check = evaluation.check();

        if (check.blocked()) {
            throw new AssignmentBlockedException(check);
        }

        if (check.needsConfirmation() && !confirmed) {
            throw new ConfirmationRequiredException(check);
        }

        TBodyMembership membership = new TBodyMembership();
        membership.setMember(evaluation.member());
        membership.setBody(evaluation.body());
        membership.setMemberType(evaluation.memberType());
        membership.setValidFrom(request.validFrom());
        membership.setValidTo(request.validTo());
        membership.setCreatedBy(actor);

        try {
            memberships.saveAndFlush(membership);
        } catch (DataIntegrityViolationException conflict) {
            throw ConstraintConflicts.translate(conflict, OPEN_DUPLICATE_INDEXES);
        }

        publishAndAudit(BodyMembershipChanged.Change.CREATED, membership, "bodies :: membership-assign",
            request.validTo() == null ? List.of("validFrom") : List.of("validFrom", "validTo"));

        return new AssignmentResult(membership, List.of(), check);
    }

    @Override
    @Transactional
    public TBodyMembership end(long membershipId, @Nullable LocalDate endDate, @Nullable String decisionReference) {
        bodiesManagerGuard.lock();

        TBodyMembership membership = memberships.findById(membershipId)
                                                .orElseThrow(() -> new NotFoundException("Body membership",
                                                    membershipId));

        if (membership.isVoided()) {
            throw new ConflictException("MEMBERSHIP_ALREADY_VOIDED",
                "The mandate is already voided: it never existed and is never touched again", List.of("status"));
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate date = endDate != null ? endDate : today;

        if (!date.isAfter(membership.getValidFrom())) {
            throw new RuleViolationException("INVALID_PERIOD", "The end date must be after the start date");
        }

        if (membership.getValidTo() != null && !membership.getValidTo().isAfter(date)) {
            throw new RuleViolationException("MEMBERSHIP_ALREADY_CLOSED",
                "The membership is already closed on " + membership.getValidTo()
                    + "; a closed mandate can only be shortened, never extended or reopened");
        }

        boolean retroactive = date.isBefore(today);
        String reference = decisionReference == null ? null : decisionReference.trim();

        if (retroactive && (reference == null || reference.isBlank())) {
            throw new RuleViolationException("RETROACTIVE_CORRECTION_REQUIRES_REASON",
                "Changing a membership's history requires a decision reference (e.g. the resolution signature)");
        }

        if (membership.getValidTo() == null && bodiesManagerGuard.wouldLeaveNone(List.of(membership.getId()), null, 0)) {
            throw new RuleViolationException(BodiesManagerGuard.VIOLATION_CODE,
                "Ending this membership would leave nobody able to manage bodies");
        }

        membership.setValidTo(date);
        membership.setDecisionReference(reference != null && reference.isBlank() ? null : reference);
        membership.setUpdatedBy(currentMemberResolver.current().orElse(null));
        memberships.saveAndFlush(membership);

        publishAndAudit(
            BodyMembershipChanged.Change.ENDED,
            membership,
            "bodies :: membership-end",
            membership.getDecisionReference() == null ? List.of("validTo") : List.of("validTo", "decisionReference")
        );

        return membership;
    }

    @Override
    @Transactional
    public TBodyMembership voidMembership(long membershipId, String reason, @Nullable String decisionReference) {
        bodiesManagerGuard.lock();

        TBodyMembership membership = memberships.findById(membershipId)
                                                .orElseThrow(() -> new NotFoundException("Body membership",
                                                    membershipId));

        if (membership.isVoided()) {
            throw new ConflictException("MEMBERSHIP_ALREADY_VOIDED",
                "The mandate is already voided; a voided mandate is never touched again",
                List.of("status"));
        }

        String trimmedReason = reason == null ? "" : reason.strip();

        if (trimmedReason.isEmpty()) {
            throw new RuleViolationException("VOID_REASON_REQUIRED",
                "Voiding a mandate requires a reason (the mandate was recorded by mistake)");
        }

        if (trimmedReason.length() > 500) {
            throw new RuleViolationException("VOID_REASON_TOO_LONG",
                "The void reason exceeds 500 characters");
        }

        if (membership.getValidTo() == null
            && bodiesManagerGuard.wouldLeaveNone(List.of(membership.getId()), null, 0)) {
            throw new RuleViolationException(BodiesManagerGuard.VIOLATION_CODE,
                "Voiding this mandate would leave nobody able to manage bodies");
        }

        String reference = decisionReference == null ? null : decisionReference.strip();

        // The voiding resolution goes to its own column: decisionReference (the appointing
        // resolution) is history and is never overwritten by a void (A3-2).
        boolean hasVoidReference = reference != null && !reference.isBlank();

        membership.setStatus(BodyMembershipStatus.VOIDED);
        membership.setVoidReason(trimmedReason);
        membership.setVoidDecisionReference(hasVoidReference ? reference : null);
        membership.setUpdatedBy(currentMemberResolver.current().orElse(null));
        memberships.saveAndFlush(membership);

        publishAndAudit(
            BodyMembershipChanged.Change.VOIDED,
            membership,
            "bodies :: membership-void",
            hasVoidReference
                ? List.of("status", "voidReason", "voidDecisionReference")
                : List.of("status", "voidReason")
        );

        return membership;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBodyMembership> activeIn(long bodyId) {
        return memberships.findActiveByBodyId(bodyId, LocalDate.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBodyMembership> activeOf(long memberId) {
        return memberships.findActiveByMemberId(memberId, LocalDate.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TBodyMembership> historyOf(long memberId) {
        return memberships.findAllByMemberId(memberId);
    }

    private void publishAndAudit(
        BodyMembershipChanged.Change change,
        TBodyMembership membership,
        String operationId,
        List<String> fields
    ) {
        events.publishEvent(new BodyMembershipChanged(
            change,
            membership.getId(),
            membership.getMember().getId(),
            membership.getBody().getId(),
            membership.getMemberType().getId(),
            membership.getValidFrom(),
            membership.getValidTo()
        ));

        auditLog.record(operationId, AuditPayload.of("membershipId", membership.getId())
                                                 .withId("memberId", membership.getMember().getId())
                                                 .withId("bodyId", membership.getBody().getId())
                                                 .withFields(fields));
    }

    private Evaluation evaluate(AssignMembership request) {
        TMember member = members.findById(request.memberId())
                                .orElseThrow(() -> new NotFoundException("Member", request.memberId()));
        TBody body = bodies.findById(request.bodyId())
                           .orElseThrow(() -> new NotFoundException("Body", request.bodyId()));
        TBodyTemplateMemberType memberType = memberTypes.findById(request.memberTypeId())
                                                        .orElseThrow(() -> new NotFoundException("Member type",
                                                            request.memberTypeId()));

        List<FunctionViolation> violations = new ArrayList<>();
        List<FunctionWarning> warnings = new ArrayList<>();

        List<TBodyMembership> existing = memberships.findAllByMemberId(member.getId());
        TBodyTemplate template = body.getBodyTemplate();
        FunctionRules.Span span = new FunctionRules.Span(request.validFrom(), request.validTo());

        if (member.getStatus() == MemberStatus.REMOVED || member.getStatus() == MemberStatus.FORM_REQUEST) {
            violations.add(violation(FunctionViolation.Code.MEMBER_NOT_ELIGIBLE,
                "Member with status " + member.getStatus() + " cannot hold a function"));
        }

        if (request.validTo() != null && !request.validTo().isAfter(request.validFrom())) {
            violations.add(violation(FunctionViolation.Code.INVALID_PERIOD, "valid_to must be after valid_from"));
        }

        if (template == null || !Objects.equals(memberType.getBodyTemplate().getId(), template.getId())) {
            violations.add(violation(FunctionViolation.Code.MEMBER_TYPE_MISMATCH,
                "The member type does not belong to the body's template"));
        }

        boolean validPeriod = violations.stream().noneMatch(v -> v.code() == FunctionViolation.Code.INVALID_PERIOD);

        if (template != null && validPeriod) {
            checkDuplicates(request, existing, span, violations);
            checkIncompatibilities(request, body, template, existing, span, violations);
            checkTermLimit(body, template, existing, span, warnings);
            checkSinglePersonExemptionLoss(request, body, template, span, warnings);
            checkCommissionerTerm(body, template, existing, span, violations);
        }

        return new Evaluation(member, body, memberType, existing,
            new AssignmentCheck(List.of(), warnings, violations));
    }

    private void checkDuplicates(
        AssignMembership request,
        List<TBodyMembership> existing,
        FunctionRules.Span span,
        List<FunctionViolation> violations
    ) {
        boolean duplicate = existing.stream()
                                    .filter(m -> m.getBody().getId().equals(request.bodyId()))
                                    .filter(m -> m.getMemberType().getId().equals(request.memberTypeId()))
                                    .anyMatch(m -> span.overlaps(spanOf(m)));

        if (duplicate) {
            violations.add(violation(FunctionViolation.Code.DUPLICATE_MEMBERSHIP,
                "The member already holds this function in the body in an overlapping period"));
        }
    }

    private void checkIncompatibilities(
        AssignMembership request,
        TBody body,
        TBodyTemplate template,
        List<TBodyMembership> existing,
        FunctionRules.Span span,
        List<FunctionViolation> violations
    ) {
        List<TBodyTemplateIncompatibility> rules = incompatibilities.findAll();

        for (TBodyMembership earlier : existing) {
            TBodyTemplate earlierTemplate = earlier.getBody().getBodyTemplate();

            if (earlierTemplate == null || earlier.getBody().getId().equals(body.getId())
                || !span.overlaps(spanOf(earlier))) {
                continue;
            }

            TBodyTemplateIncompatibility rule = rules.stream()
                                                     .filter(r -> matches(r, template.getId(), earlierTemplate.getId()))
                                                     .findFirst()
                                                     .orElse(null);

            if (rule == null) {
                continue;
            }

            Long exemptTemplateId = rule.getSingleMemberExemptTemplateId();

            if (exemptTemplateId != null) {
                Long singlePersonBodyId = exemptTemplateId.equals(template.getId())
                    ? body.getId()
                    : exemptTemplateId.equals(earlierTemplate.getId()) ? earlier.getBody().getId() : null;

                if (singlePersonBodyId != null
                    && memberships.countOtherMembersActiveOn(singlePersonBodyId, request.memberId(),
                    request.validFrom()) == 0) {
                    continue;
                }
            }

            violations.add(FunctionViolation.incompatible(
                "The function in %s is incompatible with %s over the requested period (statutory group"
                    .formatted(earlier.getBody().getName(), body.getName())
                    + " of art. 18 ust. 1 pkt 6; incompatible mandates are never auto-closed —"
                    + " end the earlier membership explicitly first)",
                earlier.getId(),
                earlier.getBody().getId(),
                earlier.getBody().getName()
            ));
        }
    }

    private void checkSinglePersonExemptionLoss(
        AssignMembership request,
        TBody body,
        TBodyTemplate template,
        FunctionRules.Span span,
        List<FunctionWarning> warnings
    ) {
        List<TBodyTemplateIncompatibility> exemptRules = incompatibilities.findAll()
                                                                          .stream()
                                                                          .filter(r -> template.getId().equals(
                                                                              r.getSingleMemberExemptTemplateId()))
                                                                          .toList();

        if (exemptRules.isEmpty()) {
            return;
        }

        Set<Long> otherMemberIds = memberships.findActiveByBodyId(body.getId(), request.validFrom())
                                              .stream()
                                              .map(m -> m.getMember().getId())
                                              .filter(id -> !id.equals(request.memberId()))
                                              .collect(Collectors.toSet());

        if (otherMemberIds.size() != 1) {
            return;
        }

        Long soleMemberId = otherMemberIds.iterator().next();

        for (TBodyMembership held : memberships.findAllByMemberId(soleMemberId)) {
            TBodyTemplate heldTemplate = held.getBody().getBodyTemplate();

            if (heldTemplate == null || held.getBody().getId().equals(body.getId())
                || !span.overlaps(spanOf(held))) {
                continue;
            }

            boolean exemptionLost = exemptRules.stream()
                                               .anyMatch(r -> matches(r, template.getId(), heldTemplate.getId()));

            if (exemptionLost) {
                TMember sole = held.getMember();
                warnings.add(FunctionWarning.singlePersonExemptionLost(
                    "%s stops being single-person, so the function in %s becomes incompatible (Art. 21.1.3)"
                        .formatted(body.getName(), held.getBody().getName()),
                    held.getId(),
                    sole.getId(),
                    sole.getPreferredNameFirst() + " " + sole.getPreferredNameLast(),
                    held.getBody().getName()
                ));
            }
        }
    }

    private void checkTermLimit(
        TBody body,
        TBodyTemplate template,
        List<TBodyMembership> existing,
        FunctionRules.Span span,
        List<FunctionWarning> warnings
    ) {
        Integer maxTerms = template.getMaxConsecutiveTerms();
        Integer termMonths = template.getTermLengthMonths();

        if (maxTerms == null || termMonths == null) {
            return;
        }

        List<FunctionRules.Span> sameBody = sameBodySpans(existing, body);
        int terms = FunctionRules.consecutiveTerms(sameBody, span, termMonths, LocalDate.now(clock));

        if (terms > maxTerms) {
            warnings.add(FunctionWarning.termLimitExceeded(
                "More than %d consecutive terms in %s".formatted(maxTerms, body.getName()), terms, maxTerms));
        }
    }

    private void checkCommissionerTerm(
        TBody body,
        TBodyTemplate template,
        List<TBodyMembership> existing,
        FunctionRules.Span span,
        List<FunctionViolation> violations
    ) {
        Integer maxTermMonths = template.getMaxTermMonths();

        if (maxTermMonths == null) {
            return;
        }

        LocalDate validTo = span.to();

        if (validTo == null) {
            violations.add(violation(FunctionViolation.Code.COMMISSIONER_VALID_TO_REQUIRED,
                "valid_to is required for this function"));
            return;
        }

        if (span.from().plusMonths(maxTermMonths).isBefore(validTo)) {
            violations.add(violation(FunctionViolation.Code.COMMISSIONER_TERM_TOO_LONG,
                "A single term is limited to " + maxTermMonths + " months"));
        }

        int maxExtensions = template.getMaxExtensions() == null ? 0 : template.getMaxExtensions();
        FunctionRules.Chain chain = FunctionRules.previousChain(sameBodySpans(existing, body), span);

        if (chain.previousCount() > maxExtensions) {
            violations.add(violation(FunctionViolation.Code.COMMISSIONER_EXTENSIONS_EXCEEDED,
                "The term can be extended at most " + maxExtensions + " times"));
        } else if (chain.start().plusMonths((long) maxTermMonths * (1 + maxExtensions)).isBefore(validTo)) {
            violations.add(violation(FunctionViolation.Code.COMMISSIONER_TOTAL_TOO_LONG,
                "The total term is limited to " + maxTermMonths * (1 + maxExtensions) + " months"));
        }
    }

    private static List<FunctionRules.Span> sameBodySpans(List<TBodyMembership> existing, TBody body) {
        return existing.stream()
                       .filter(m -> m.getBody().getId().equals(body.getId()))
                       .map(DefaultBodyMembershipService::spanOf)
                       .toList();
    }

    private static boolean matches(TBodyTemplateIncompatibility rule, long templateA, long templateB) {
        return (rule.getBodyTemplateId() == templateA && rule.getOtherBodyTemplateId() == templateB)
            || (rule.getBodyTemplateId() == templateB && rule.getOtherBodyTemplateId() == templateA);
    }

    private static FunctionRules.Span spanOf(TBodyMembership membership) {
        return new FunctionRules.Span(membership.getValidFrom(), membership.getValidTo());
    }

    private static FunctionViolation violation(FunctionViolation.Code code, String message) {
        return new FunctionViolation(code, message);
    }

    private record Evaluation(
        TMember member,
        TBody body,
        TBodyTemplateMemberType memberType,
        List<TBodyMembership> existing,
        AssignmentCheck check
    ) {
    }
}
