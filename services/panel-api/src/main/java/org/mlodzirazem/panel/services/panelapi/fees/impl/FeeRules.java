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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.time.LocalDate;
import java.time.YearMonth;

final class FeeRules {
    private FeeRules() {
    }

    static @Nullable YearMonth firstDueMonth(TMember member) {
        LocalDate since = member.getMemberSince();

        if (since == null) {
            return null;
        }

        YearMonth month = YearMonth.from(since);
        return since.getDayOfMonth() == 1 ? month : month.plusMonths(1);
    }

    static boolean owesDueFor(TMember member, YearMonth month) {
        YearMonth first = firstDueMonth(member);

        if (member.getMembershipType() != MembershipType.ORDINARY || first == null || month.isBefore(first)) {
            return false;
        }

        LocalDate removedAt = member.getRemovedAt();

        return switch (member.getStatus()) {
            case ACTIVE, SUSPENDED -> true;
            case REMOVED -> removedAt != null && month.atDay(1).isBefore(removedAt);
            default -> false;
        };
    }
}
