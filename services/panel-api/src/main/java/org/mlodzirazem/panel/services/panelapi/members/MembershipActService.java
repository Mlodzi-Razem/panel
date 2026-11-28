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
package org.mlodzirazem.panel.services.panelapi.members;

import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipRemovalGround;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberMembershipAct;

import java.time.LocalDate;
import java.util.List;

/**
 * The only way to change membership status, membership type or removal date (statute art. 9 ust. 1 pkt 1, art. 11
 * ust. 2, art. 12 ust. 3). Every act is a new row in the acts register with the resolution that justifies it; nothing
 * is overwritten and no open mandate is closed silently.
 */
public interface MembershipActService {

    /** Supporting membership of a natural person, granted by resolution of a ZO or ZK (art. 9 ust. 1 pkt 1). */
    record GrantSupporting(NewMember member, LocalDate grantedOn, String decisionReference) {
    }

    /** Honorary membership of a new person, granted by a WZOC resolution (art. 12 ust. 3). */
    record GrantHonorary(NewMember member, LocalDate grantedOn, String decisionReference) {
    }

    /** Honorary membership of an existing member, granted by a WZOC resolution (art. 12 ust. 3). */
    record GrantHonoraryToMember(long memberId, LocalDate grantedOn, String decisionReference) {
    }

    /** End of membership with a statutory ground (art. 11 ust. 1 pkt 1-5, art. 11 ust. 3, art. 12). */
    record Remove(long memberId, MembershipRemovalGround ground, LocalDate removedOn, String decisionReference) {
    }

    /**
     * Reinstatement of a removed member, recorded as a new act with its own reference and reason. After a removal on
     * the ground HONORARY_REVOKED the membership type from before the honorary grant is restored (art. 12 ust. 3).
     */
    record Reinstate(long memberId, LocalDate effectiveOn, String decisionReference, String reason) {
    }

    /**
     * Change between ordinary and supporting membership by a resolution (decision D-B, closed in G-12). Honorary
     * membership is never changed this way; it has its own acts.
     */
    record ChangeMembershipType(
        long memberId,
        org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType target,
        LocalDate effectiveOn,
        String decisionReference,
        String reason
    ) {
    }

    /**
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException when a statutory rule is broken
     * @throws org.mlodzirazem.panel.services.panelapi.core.error.ConflictException when the member is in the wrong state
     */
    TMember grantSupporting(GrantSupporting grant);

    TMember grantHonorary(GrantHonorary grant);

    TMember grantHonoraryToMember(GrantHonoraryToMember grant);

    TMemberMembershipAct remove(Remove removal);

    TMemberMembershipAct reinstate(Reinstate reinstatement);

    TMemberMembershipAct changeMembershipType(ChangeMembershipType change);

    /** All acts of the member, newest first. Rows written before the register existed are not listed. */
    List<TMemberMembershipAct> history(long memberId);
}
