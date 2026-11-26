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

import org.mlodzirazem.panel.services.panelapi.core.error.DomainException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

abstract class FeesTestSupport extends DomainTestSupport {
    protected static final YearMonth NOW = YearMonth.now(Clock.systemUTC());

    @Autowired
    protected FeeScheduleService schedule;

    @Autowired
    protected DuesService dues;

    @Autowired
    protected AdjustmentService adjustments;

    @Autowired
    protected PaymentService payments;

    @Autowired
    protected ArrearsService arrears;

    protected TMember actor;
    protected long tierId;

    protected static BigDecimal money(String amount) {
        return new BigDecimal(amount).setScale(2);
    }

    protected void setUpTierWithRate() {
        actor = newMember("Tre", "Asurer", "mazowieckie");
        tierId = schedule.createTier(actor.getId(), new NewFeeTier("standard", "Standardowa", true)).id();
        pastRate(tierId, NOW.minusMonths(12), "30");
    }

    /**
     * A rate that has been in force since a past month. The service refuses to create one (rates start in the current
     * month at the earliest), so the history is written directly, the way it looks after months of running.
     */
    protected void pastRate(long tier, YearMonth validFrom, String amount) {
        jdbc.update("INSERT INTO fee_tier_rates (tier_id, monthly_amount, valid_from, resolution_reference, "
            + "created_by_user_id) VALUES (?, ?, ?, 'U/1', ?)", tier, money(amount), validFrom.atDay(1), actor.getId());
        dues.generateDues(NOW);
    }

    protected void pastAssignment(TMember member, long tier, YearMonth validFrom) {
        jdbc.update("INSERT INTO member_fee_tiers (member_id, tier_id, valid_from, created_by_user_id) "
            + "VALUES (?, ?, ?, ?)", member.getId(), tier, validFrom.atDay(1), actor.getId());
        dues.refresh(member.getId());
    }

    protected TMember ordinaryMember(String first, String last, YearMonth since) {
        TMember member = newMember(first, last, "mazowieckie");
        jdbc.update("UPDATE members SET member_since = ? WHERE id = ?", since.atDay(1), member.getId());
        return member;
    }

    protected TMember unbilledMember() {
        return ordinaryMember("Pay", "Er" + System.nanoTime(), NOW.minusMonths(6));
    }

    protected TMember payingMember() {
        TMember member = unbilledMember();
        dues.refresh(member.getId());
        return member;
    }

    protected static LocalDate today() {
        return LocalDate.now(Clock.systemUTC());
    }

    protected List<Due> allDues(TMember member) {
        return dues.duesOf(member.getId());
    }

    protected Due dueOf(TMember member, YearMonth month) {
        return allDues(member).stream().filter(d -> d.month().equals(month)).findFirst().orElseThrow();
    }

    protected long count(TMember member, DueStatus status) {
        return allDues(member).stream().filter(d -> d.status() == status).count();
    }

    protected static String codeOf(Runnable action) {
        try {
            action.run();
        } catch (DomainException e) {
            return e.getCode();
        }

        return "NONE";
    }
}
