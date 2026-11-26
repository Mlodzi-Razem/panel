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

package org.mlodzirazem.panel.services.panelapi.fees.impl;

import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;

import java.util.Set;

/**
 * Nobody decides on their own fees: another FEES_MANAGE holder has to grant the adjustment, record the cash, assign
 * the tier, confirm the IBAN or match the transfer. Bank transfers identified automatically by an import are not
 * decisions and stay allowed.
 */
final class OwnFeesGuard {
    private OwnFeesGuard() {
    }

    static void reject(long actorMemberId, long memberId) {
        if (actorMemberId == memberId) {
            throw new RuleViolationException("SELF_FEES_NOT_ALLOWED",
                "Another fee manager has to make this change to your own fees");
        }
    }

    static void reject(long actorMemberId, Set<Long> memberIds) {
        memberIds.forEach(id -> reject(actorMemberId, (long) id));
    }
}
