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

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.fees.Due;
import org.mlodzirazem.panel.services.panelapi.fees.DuesRun;
import org.mlodzirazem.panel.services.panelapi.fees.DuesService;
import org.mlodzirazem.panel.services.panelapi.fees.NextDue;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.DueStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.TMemberFeeTierRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class DefaultDuesService implements DuesService {
    private static final int BATCH = 100;

    private final DuesSynchronizer synchronizer;
    private final FeeLock lock;
    private final FeeReader reader;
    private final TMemberRepository members;
    private final TMemberFeeTierRepository assignments;
    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional
    public DuesRun generateDues(YearMonth upTo) {
        lock.acquire();

        FeeContext context = synchronizer.context();
        DuesSynchronizer.Tally tally = new DuesSynchronizer.Tally();

        List<Long> ids = jdbc.sql("""
            SELECT id FROM members
            WHERE membership_type = 'ORDINARY' AND status IN ('ACTIVE', 'SUSPENDED', 'REMOVED')
              AND member_since IS NOT NULL
            ORDER BY id
            """).query(Long.class).list();

        for (int i = 0; i < ids.size(); i += BATCH) {
            members.findAllById(ids.subList(i, Math.min(ids.size(), i + BATCH)))
                   .forEach(m -> synchronizer.sync(context, m, upTo, tally));
            entityManager.flush();
            entityManager.clear();
        }

        DuesRun run = run(tally);

        if (run.changed()) {
            auditLog.record("fees :: dues-generate", new AuditPayload(
                Map.of("members", (long) run.members(), "created", (long) run.created(),
                    "updated", (long) run.updated(), "cancelled", (long) run.cancelled()),
                List.of(), null));
        }

        return run;
    }

    @Override
    @Transactional
    public DuesRun generateDues(long memberId, YearMonth upTo) {
        lock.acquire();

        TMember member = members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
        DuesSynchronizer.Tally tally = new DuesSynchronizer.Tally();
        synchronizer.sync(synchronizer.context(), member, upTo, tally);

        return run(tally);
    }

    @Override
    @Transactional
    public DuesRun refresh(long memberId) {
        return generateDues(memberId, YearMonth.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Due> duesOf(long memberId) {
        return reader.duesOf(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Due> duesOf(long memberId, DueStatus status) {
        return reader.duesOf(memberId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NextDue> nextDue(long memberId) {
        TMember member = members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
        YearMonth next = YearMonth.now(clock).plusMonths(1);

        if (!FeeRules.owesDueFor(member, next)) {
            return Optional.empty();
        }

        return FeePricing.price(synchronizer.context(), assignments.findByMemberIdOrderByValidFromAsc(memberId),
                           reader.adjustmentEntities(memberId), next)
                         .filter(fee -> fee.amount().signum() > 0)
                         .map(fee -> new NextDue(next, fee.amount()));
    }

    private static DuesRun run(DuesSynchronizer.Tally tally) {
        return new DuesRun(tally.members, tally.created, tally.updated, tally.cancelled);
    }
}
