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

package org.mlodzirazem.panel.services.panelapi.suspensions;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;

import java.time.LocalDate;

/**
 * Pure rules of art. 10 (no database, no clock: the caller passes the day). Every rule of the register is written
 * down once, here.
 */
public final class SuspensionRules {
    /** Art. 10 ust. 1 pkt 1 and art. 42 ust. 2 pkt 8: arrears of at least this many full calendar months. */
    public static final int ARREARS_MIN_MONTHS = 3;

    /** Art. 10 ust. 3 zd. 2 and art. 18 ust. 1 pkt 2: a passive right suspended for more than this many days. */
    public static final int PASSIVE_LIMIT_DAYS = 62;

    /** Latest start of an organ or fees suspension, counted from today. */
    public static final int MAX_START_AHEAD_DAYS = 365;

    public static final int MAX_REFERENCE_LENGTH = 200;
    public static final int MAX_TEXT_LENGTH = 500;

    private SuspensionRules() {
    }

    /** Result of the art. 10 ust. 3 analysis: whether positions can be lost, and from which day ({@code null}: not yet known). */
    public record LossDate(boolean applicable, @Nullable LocalDate dueOn) {
        static final LossDate NOT_APPLICABLE = new LossDate(false, null);
    }

    public static SuspensionState state(Suspension s, LocalDate today) {
        if (s.voided()) {
            return SuspensionState.VOIDED;
        }

        if (s.liftedOn() != null && !s.liftedOn().isAfter(today)) {
            return SuspensionState.LIFTED;
        }

        LocalDate effectiveEnd = s.effectiveEnd();

        if (effectiveEnd != null && !effectiveEnd.isAfter(today)) {
            return SuspensionState.EXPIRED;
        }

        return s.start().isAfter(today) ? SuspensionState.SCHEDULED : SuspensionState.ACTIVE;
    }

    public static @Nullable LocalDate effectiveEnd(@Nullable LocalDate end, @Nullable LocalDate liftedOn) {
        if (end == null) {
            return liftedOn;
        }

        return liftedOn == null || end.isBefore(liftedOn) ? end : liftedOn;
    }

    /**
     * {@code dueOn} is the first day on which the mandates are lost: for a passive right the day after the 62nd
     * (counted with the start day as day 1, end exclusive); for a full suspension the end of the appeal period,
     * which is recorded by hand. Until it is recorded the date stays unknown.
     */
    public static LossDate positionLossDueOn(Suspension s) {
        if (s.voided() || s.scope() == SuspensionScope.PARTIAL_OTHER) {
            return LossDate.NOT_APPLICABLE;
        }

        LocalDate effectiveEnd = s.effectiveEnd();

        if (s.scope() == SuspensionScope.PASSIVE_ELECTORAL) {
            LocalDate limit = s.start().plusDays(PASSIVE_LIMIT_DAYS);
            return effectiveEnd == null || effectiveEnd.isAfter(limit)
                ? new LossDate(true, limit)
                : LossDate.NOT_APPLICABLE;
        }

        LocalDate appealEnded = s.appealEndedOn();

        if (appealEnded == null) {
            return new LossDate(true, null);
        }

        return effectiveEnd == null || appealEnded.isBefore(effectiveEnd)
            ? new LossDate(true, appealEnded)
            : LossDate.NOT_APPLICABLE;
    }

    /**
     * Art. 10 ust. 1 pkt 1 and art. 42 ust. 2 pkt 8: the ZO of the person's district. Art. 10 ust. 2: ZK or the
     * same ZO for a voluntary suspension. Art. 25 ust. 2 pkt 4: the peer court for an organ suspension. The
     * {@code ownDistrictBoard} flag says whether a DISTRICT_BOARD body is the board of the person's primary district.
     */
    public static boolean competent(SuspensionKind kind, @Nullable BodyKind bodyKind, boolean ownDistrictBoard) {
        if (bodyKind == null) {
            return false;
        }

        return switch (bodyKind) {
            case NATIONAL_BOARD -> kind != SuspensionKind.ARREARS;
            case DISTRICT_BOARD -> ownDistrictBoard;
            case PEER_COURT -> kind == SuspensionKind.BY_ORGAN;
            default -> false;
        };
    }
}
