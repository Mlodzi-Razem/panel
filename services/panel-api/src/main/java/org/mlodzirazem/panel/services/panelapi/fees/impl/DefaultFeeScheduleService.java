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

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.fees.DuesService;
import org.mlodzirazem.panel.services.panelapi.fees.FeeRate;
import org.mlodzirazem.panel.services.panelapi.fees.FeeScheduleService;
import org.mlodzirazem.panel.services.panelapi.fees.FeeTier;
import org.mlodzirazem.panel.services.panelapi.fees.FeeTierUpdate;
import org.mlodzirazem.panel.services.panelapi.fees.NewFeeTier;
import org.mlodzirazem.panel.services.panelapi.fees.ResolvedFee;
import org.mlodzirazem.panel.services.panelapi.fees.TierAssignment;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeDueRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeTier;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeTierRate;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeTierRateRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TFeeTierRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TMemberFeeTier;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TMemberFeeTierRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class DefaultFeeScheduleService implements FeeScheduleService {
    private final TFeeTierRepository tiers;
    private final TFeeTierRateRepository rates;
    private final TFeeDueRepository dues;
    private final TMemberFeeTierRepository assignments;
    private final TMemberRepository members;
    private final DuesSynchronizer synchronizer;
    private final FeeReader reader;
    private final FeeLock lock;
    private final DuesService duesService;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<FeeTier> tiers(boolean includeInactive) {
        return (includeInactive ? tiers.findAllByOrderByCodeAsc() : tiers.findByActiveTrueOrderByCodeAsc())
            .stream().map(DefaultFeeScheduleService::view).toList();
    }

    @Override
    @Transactional
    public FeeTier createTier(long actorMemberId, NewFeeTier newTier) {
        lock.acquire();

        String code = Money.text(newTier.code(), "FEE_TIER_INVALID");
        String name = Money.text(newTier.name(), "FEE_TIER_INVALID");

        if (tiers.findByCode(code).isPresent()) {
            throw new ConflictException("FEE_TIER_CODE_TAKEN", "A tier with this code exists", List.of("code"));
        }

        boolean hasDefault = tiers.findByDefaultTierTrue().isPresent();

        if (newTier.makeDefault() && hasDefault) {
            requireNoDues();
        }

        boolean makeDefault = newTier.makeDefault() || !hasDefault;
        clearDefault(makeDefault);

        TFeeTier tier = new TFeeTier();
        tier.setCode(code);
        tier.setName(name);
        tier.setDefaultTier(makeDefault);
        tier.setCreatedBy(members.getReferenceById(actorMemberId));
        tiers.saveAndFlush(tier);

        auditLog.record("fees :: tier-create", AuditPayload.of("tierId", tier.getId())
                                                           .withFields(List.of("code", "default", "name")));

        return view(tier);
    }

    @Override
    @Transactional
    public FeeTier updateTier(long actorMemberId, long tierId, FeeTierUpdate update) {
        lock.acquire();

        TFeeTier tier = tier(tierId);
        List<String> changed = new ArrayList<>();
        boolean defaultChanged = false;

        if (update.name() != null && !update.name().strip().equals(tier.getName())) {
            tier.setName(Money.text(update.name(), "FEE_TIER_INVALID"));
            changed.add("name");
        }

        if (update.active() != null && update.active() != tier.isActive()) {
            if (!update.active() && tier.isDefaultTier()) {
                throw new RuleViolationException("FEE_TIER_DEFAULT_REQUIRED", "The default tier must stay active");
            }

            tier.setActive(update.active());
            changed.add("active");
        }

        if (update.makeDefault() != null && update.makeDefault() != tier.isDefaultTier()) {
            if (!update.makeDefault()) {
                throw new RuleViolationException("FEE_TIER_DEFAULT_REQUIRED",
                    "Mark another tier as default instead");
            }

            if (!tier.isActive()) {
                throw new RuleViolationException("FEE_TIER_INACTIVE", "An inactive tier cannot be the default");
            }

            requireNoDues();
            clearDefault(true);
            tier.setDefaultTier(true);
            changed.add("default");
            defaultChanged = true;
        }

        if (!changed.isEmpty()) {
            tier.setUpdatedBy(members.getReferenceById(actorMemberId));
            tiers.saveAndFlush(tier);
            auditLog.record("fees :: tier-update", AuditPayload.of("tierId", tierId).withFields(changed));
        }

        FeeTier result = view(tier);

        if (defaultChanged) {
            duesService.generateDues(YearMonth.now(clock));
        }

        return result;
    }

    @Override
    @Transactional
    public FeeRate addRate(
        long actorMemberId,
        long tierId,
        YearMonth validFrom,
        BigDecimal monthlyAmount,
        String resolutionReference
    ) {
        lock.acquire();

        TFeeTier tier = tier(tierId);
        BigDecimal amount = Money.positive(monthlyAmount, "RATE_AMOUNT_INVALID");
        String reference = Money.text(resolutionReference, "RATE_REFERENCE_REQUIRED");

        if (rates.existsByTierIdAndValidFrom(tierId, Months.day(validFrom))) {
            throw new ConflictException("RATE_EXISTS", "The tier already has a rate starting in this month",
                List.of("validFrom"));
        }

        if (dues.existsByTierIdAndDueMonthGreaterThanEqual(tierId, Months.day(validFrom))) {
            throw new RuleViolationException("RATE_MONTH_HAS_DUES",
                "Dues already exist for this month or later; a rate may only start in a month without dues");
        }

        if (validFrom.isBefore(YearMonth.now(clock))) {
            throw new RuleViolationException("RATE_MONTH_IN_PAST",
                "A rate may not start before the current month; past dues are never generated from a new rate");
        }

        TFeeTierRate rate = new TFeeTierRate();
        rate.setTier(tier);
        rate.setMonthlyAmount(amount);
        rate.setValidFrom(Months.day(validFrom));
        rate.setResolutionReference(reference);
        rate.setCreatedBy(members.getReferenceById(actorMemberId));
        rates.saveAndFlush(rate);

        auditLog.record("fees :: rate-add", AuditPayload.of("tierId", tierId).withId("rateId", rate.getId())
                                                        .withFields(List.of("monthlyAmount", "resolutionReference",
                                                            "validFrom")));

        FeeRate result = view(rate);
        duesService.generateDues(YearMonth.now(clock));

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeeRate> rates(long tierId) {
        tier(tierId);
        return rates.findByTierIdOrderByValidFromAsc(tierId).stream().map(DefaultFeeScheduleService::view).toList();
    }

    @Override
    @Transactional
    public TierAssignment assignTier(long actorMemberId, long memberId, long tierId, YearMonth validFrom) {
        OwnFeesGuard.reject(actorMemberId, memberId);

        if (validFrom.isBefore(YearMonth.now(clock))) {
            throw new RuleViolationException("ASSIGNMENT_MONTH_IN_PAST",
                "A tier assignment may not start before the current month");
        }

        lock.acquire();

        TMember member = members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
        TFeeTier tier = tier(tierId);

        if (!tier.isActive()) {
            throw new RuleViolationException("FEE_TIER_INACTIVE", "Members cannot be assigned to an inactive tier");
        }

        TMemberFeeTier assignment = assignments.findByMemberIdAndValidFrom(memberId, Months.day(validFrom))
                                               .orElseGet(TMemberFeeTier::new);
        boolean created = assignment.getId() == null;

        assignment.setMember(member);
        assignment.setTier(tier);
        assignment.setValidFrom(Months.day(validFrom));

        if (created) {
            assignment.setCreatedBy(members.getReferenceById(actorMemberId));
        } else {
            assignment.setUpdatedBy(members.getReferenceById(actorMemberId));
        }

        assignments.saveAndFlush(assignment);

        auditLog.record("fees :: tier-assign", AuditPayload.of("memberId", memberId).withId("tierId", tierId)
                                                           .withFields(List.of("validFrom")));

        TierAssignment result = view(assignment);
        duesService.refresh(memberId);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TierAssignment> assignments(long memberId) {
        return assignments.findByMemberIdOrderByValidFromAsc(memberId).stream()
                          .map(DefaultFeeScheduleService::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedFee> resolve(long memberId, YearMonth month) {
        return FeePricing.price(synchronizer.context(), assignments.findByMemberIdOrderByValidFromAsc(memberId),
            reader.adjustmentEntities(memberId), month);
    }

    /**
     * Members without an explicit assignment are priced by the default tier in every month, so switching it would
     * reprice open dues of past months.
     */
    private void requireNoDues() {
        if (dues.count() > 0) {
            throw new RuleViolationException("FEE_TIER_DEFAULT_LOCKED",
                "The default tier cannot change once dues exist");
        }
    }

    private void clearDefault(boolean needed) {
        if (needed) {
            tiers.findByDefaultTierTrue().ifPresent(t -> {
                t.setDefaultTier(false);
                tiers.saveAndFlush(t);
            });
        }
    }

    private TFeeTier tier(long tierId) {
        return tiers.findById(tierId).orElseThrow(() -> new NotFoundException("Fee tier", tierId));
    }

    private static FeeTier view(TFeeTier t) {
        return new FeeTier(t.getId(), t.getCode(), t.getName(), t.isDefaultTier(), t.isActive());
    }

    private static FeeRate view(TFeeTierRate r) {
        return new FeeRate(r.getId(), r.getTier().getId(), r.getMonthlyAmount(), Months.of(r.getValidFrom()),
            r.getResolutionReference());
    }

    private static TierAssignment view(TMemberFeeTier a) {
        return new TierAssignment(a.getId(), a.getMember().getId(), a.getTier().getId(), Months.of(a.getValidFrom()));
    }
}
