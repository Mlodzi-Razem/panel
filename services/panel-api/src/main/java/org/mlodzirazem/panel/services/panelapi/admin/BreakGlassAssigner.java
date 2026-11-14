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

package org.mlodzirazem.panel.services.panelapi.admin;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipChanged;
import org.mlodzirazem.panel.services.panelapi.core.cli.CliMode;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberTypeRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
class BreakGlassAssigner {
    static final String OPERATION_ID = "admin :: break-glass";

    private final TBodyRepository bodies;
    private final TBodyTemplateMemberTypeRepository memberTypes;
    private final TMemberRepository members;
    private final TBodyMembershipRepository memberships;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;
    private final Clock clock;

    @Transactional
    public BreakGlassReport run(
        @Nullable String function,
        @Nullable String memberIdentifier,
        @Nullable String operator,
        @Nullable String reason,
        boolean apply
    ) {
        List<String> lines = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        if (isBlank(operator)) {
            problems.add("--panel.admin.operator is required");
        }

        if (isBlank(reason)) {
            problems.add("--panel.admin.reason is required");
        }

        if (isBlank(memberIdentifier)) {
            problems.add("--panel.admin.member is required");
        }

        String bodyCode = null;
        String typeCode = null;
        int separator = function == null ? -1 : function.indexOf(':');

        if (separator <= 0 || separator == function.length() - 1) {
            problems.add("--panel.admin.assign-function must look like <body-code>:<member-type-code>");
        } else {
            bodyCode = function.substring(0, separator).trim();
            typeCode = function.substring(separator + 1).trim();
        }

        if (!problems.isEmpty()) {
            return failed(problems);
        }

        TBody body = bodies.findByCode(bodyCode).orElse(null);

        if (body == null) {
            problems.add("Unknown body code: " + bodyCode);
        } else if (body.getBodyTemplate() == null) {
            problems.add("Body " + bodyCode + " has no template");
        }

        TBodyTemplateMemberType memberType = null;

        if (body != null && body.getBodyTemplate() != null) {
            memberType = memberTypes.findByBodyTemplateIdAndCode(body.getBodyTemplate().getId(), typeCode).orElse(null);

            if (memberType == null) {
                problems.add("Body " + bodyCode + " has no member type " + typeCode);
            }
        }

        TMember member = null;
        String identifier = memberIdentifier.trim();
        Optional<TMember> resolved = resolve(identifier, problems);

        if (resolved.isPresent()) {
            member = resolved.get();

            if (member.getStatus() == MemberStatus.REMOVED) {
                problems.add("Member " + identifier + " has status REMOVED and cannot hold a function");
            }
        } else if (problems.stream().noneMatch(p -> p.startsWith("Ambiguous"))) {
            problems.add("Unknown member: " + identifier);
        }

        if (!problems.isEmpty()) {
            return failed(problems);
        }

        LocalDate today = LocalDate.now(clock);
        long memberTypeId = memberType.getId();
        TBodyMembership current = memberships.findAllByMemberIdAndBodyId(member.getId(), body.getId())
                                             .stream()
                                             .filter(m -> m.getMemberType().getId() == memberTypeId)
                                             .filter(m -> m.isActiveOn(today))
                                             .findFirst()
                                             .orElse(null);

        lines.add("Mode: " + (apply ? "apply" : "dry run"));
        lines.add("Function: " + bodyCode + ":" + typeCode + " (body " + body.getId() + ", member type "
            + memberType.getId() + ")");
        lines.add("Member: " + identifier);
        lines.add("Valid from: " + today + ", open-ended; Art. 20/21/52 checks and the lockout guard are bypassed");

        if (current != null) {
            lines.add("Result: member already holds this function (membership " + current.getId() + "), nothing to do");
            return new BreakGlassReport(CliMode.EXIT_OK, lines);
        }

        if (!apply) {
            lines.add("Result: would create the membership; run again with --panel.admin.apply=true to write it");
            return new BreakGlassReport(CliMode.EXIT_OK, lines);
        }

        TBodyMembership membership = new TBodyMembership();
        membership.setMember(member);
        membership.setBody(body);
        membership.setMemberType(memberType);
        membership.setValidFrom(today);
        membership.setValidTo(null);
        memberships.saveAndFlush(membership);

        events.publishEvent(new BodyMembershipChanged(
            BodyMembershipChanged.Change.CREATED,
            membership.getId(),
            member.getId(),
            body.getId(),
            memberType.getId(),
            membership.getValidFrom(),
            membership.getValidTo()
        ));

        auditLog.record(OPERATION_ID, AuditPayload.of("membershipId", membership.getId())
                                                  .withId("memberId", member.getId())
                                                  .withId("bodyId", body.getId())
                                                  .withId("memberTypeId", memberType.getId())
                                                  .withFields(List.of("validFrom"))
                                                  .withOutcome("operator: " + operator.trim() + "; reason: "
                                                      + reason.trim()));

        lines.add("Result: created membership " + membership.getId());
        return new BreakGlassReport(CliMode.EXIT_OK, lines);
    }

    private Optional<TMember> resolve(String identifier, List<String> problems) {
        if (identifier.contains("@")) {
            return members.findByDomainEmailIgnoreCase(identifier);
        }

        Optional<TMember> byMemberId = members.findByMemberId(identifier);
        Optional<TMember> byDatabaseId = identifier.chars().allMatch(Character::isDigit) && identifier.length() <= 18
            ? members.findById(Long.parseLong(identifier))
            : Optional.empty();

        if (byMemberId.isPresent() && byDatabaseId.isPresent()
            && !byMemberId.get().getId().equals(byDatabaseId.get().getId())) {
            problems.add("Ambiguous member identifier " + identifier
                + ": it is a member_id of one member and a database id of another");
            return Optional.empty();
        }

        return byMemberId.isPresent() ? byMemberId : byDatabaseId;
    }

    private static BreakGlassReport failed(List<String> problems) {
        List<String> lines = new ArrayList<>();

        problems.forEach(problem -> lines.add("ERROR: " + problem));
        lines.add("Nothing was written");

        return new BreakGlassReport(CliMode.EXIT_PROBLEMS, lines);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
