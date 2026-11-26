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

package org.mlodzirazem.panel.services.panelapi.bodies.impl;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.bodies.FunctionViolation;
import org.mlodzirazem.panel.services.panelapi.members.ElectoralRightsService;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Who may hold a function in an organ at all (statute of 3.10.2026): hard violations, never confirmable.
 * Status and membership type are the register's current ones — it keeps no history of them, so a backdated
 * entry is judged by what the member is today. Fee arrears alone block nothing (art. 10 ust. 1 pkt 1 needs
 * a resolution, which is a suspension row). Existing mandates are never touched.
 *
 * <p>Honorary membership (art. 12) is a title that grants no electoral rights; blocking it is the panel's
 * reading of art. 12, not an explicit rule of the statute.
 */
@Component
@RequiredArgsConstructor
class FunctionEligibilityRules {
    /** The National Council is left out by the owner's decision: it is a label without rules. */
    private static final Set<BodyKind> ORGANS = EnumSet.of(
        BodyKind.NATIONAL_BOARD,
        BodyKind.PEER_COURT,
        BodyKind.AUDIT_COMMISSION,
        BodyKind.ELECTORAL_COMMISSION,
        BodyKind.DISTRICT_BOARD,
        BodyKind.DISTRICT_COMMISSIONER
    );

    private final ElectoralRightsService electoralRights;

    void evaluate(TMember member, BodyKind kind, FunctionRules.Span span, List<FunctionViolation> violations) {
        if (!ORGANS.contains(kind)) {
            return;
        }

        if (member.getMembershipType() != MembershipType.ORDINARY) {
            violations.add(new FunctionViolation(FunctionViolation.Code.FUNCTION_REQUIRES_ORDINARY_MEMBERSHIP,
                "A function in this organ requires ordinary membership (art. 7 ust. 2 pkt 1, art. 8 ust. 2)"));
        } else if (span.from().isAfter(MemberRules.membershipEndDate(member.getBirthDate()))) {
            violations.add(new FunctionViolation(FunctionViolation.Code.MEMBERSHIP_ENDED_BEFORE_MANDATE,
                "The ordinary membership ends before the mandate starts (art. 11 ust. 1 pkt 5)"));
        }

        // removed members and form requests are refused for every body (MEMBER_NOT_ELIGIBLE)
        if (member.getStatus() == MemberStatus.PROBATION || member.getStatus() == MemberStatus.SUSPENDED) {
            violations.add(new FunctionViolation(FunctionViolation.Code.FUNCTION_REQUIRES_ACTIVE_MEMBER,
                "Member with status " + member.getStatus() + " cannot hold a function in this organ"));
        }

        if (electoralRights.suspendedDuring(member.getId(), span.from(), span.to())) {
            violations.add(new FunctionViolation(FunctionViolation.Code.MEMBER_SUSPENDED_IN_PERIOD,
                "The member is suspended during part of the mandate's period (art. 10 ust. 3)"));
        }
    }
}
