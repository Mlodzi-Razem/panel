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

package org.mlodzirazem.panel.services.panelapi.fees;

import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;

import java.time.YearMonth;
import java.util.List;

/**
 * Dues are generated daily by a scheduled job (cron {@code panel.fees.dues-cron}, default {@code 0 0 2 * * *} UTC)
 * for the current month, and for the affected members right after rate, tier, adjustment and payment changes.
 * Everything is idempotent and serialised by a transaction-scoped advisory lock.
 * Other modules should call {@link #refresh(long)} after changing a member status, type or {@code member_since}.
 */
public interface DuesService {
    /**
     * Generates missing dues up to and including the month for all members and recalculates open dues. Paid dues are
     * never touched.
     */
    DuesRun generateDues(YearMonth upTo);

    DuesRun generateDues(long memberId, YearMonth upTo);

    /**
     * {@link #generateDues(long, YearMonth)} up to the current month.
     */
    DuesRun refresh(long memberId);

    /**
     * All dues of the member (including paid and cancelled), oldest month first.
     */
    List<Due> duesOf(long memberId);

    /**
     * Dues in the given status, oldest month first.
     */
    List<Due> duesOf(long memberId, DueStatus status);

    /**
     * The due of the next month the member will be charged, after adjustments; empty when the member does not pay
     * fees, is exempt for it or no rate applies yet.
     */
    java.util.Optional<NextDue> nextDue(long memberId);
}
