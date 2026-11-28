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

package org.mlodzirazem.panel.services.panelapi.members.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.members.ElectoralRights;
import org.mlodzirazem.panel.services.panelapi.members.ElectoralRightsService;
import org.mlodzirazem.panel.services.panelapi.members.FeeArrearsPolicy;
import org.mlodzirazem.panel.services.panelapi.members.KkwDistrictFunction;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.AffiliationKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliation;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
class DefaultElectoralRightsService implements ElectoralRightsService {
    private static final String ACTIVE_SUSPENSIONS = """
        SELECT suspended_user_id FROM suspensions
        WHERE status = 'ACTIVE'
          AND scope = 'FULL'
          AND suspension_start <= :today
          AND (suspension_end IS NULL OR suspension_end > :today)
          AND (lifted_at IS NULL OR lifted_at > :today)
        """;

    /** LEAST ignores NULLs, so it is the earlier of the two ends, or NULL while the suspension has none. */
    private static final String SUSPENSIONS_OVERLAPPING = """
        SELECT COUNT(*) FROM suspensions
        WHERE suspended_user_id = :memberId
          AND status = 'ACTIVE'
          AND scope IN ('FULL', 'PASSIVE_ELECTORAL')
          AND (CAST(:to AS DATE) IS NULL OR suspension_start < CAST(:to AS DATE))
          AND (LEAST(suspension_end, lifted_at) IS NULL
               OR (LEAST(suspension_end, lifted_at) > CAST(:from AS DATE)
                   AND LEAST(suspension_end, lifted_at) > suspension_start))
        """;

    private final TMemberRepository members;
    private final TMemberDistrictAffiliationRepository affiliations;
    private final TBodyMembershipRepository bodyMemberships;
    private final JdbcClient jdbc;
    private final ObjectProvider<FeeArrearsPolicy> feeArrearsPolicy;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ElectoralRights rightsOf(long memberId) {
        TMember member = members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
        LocalDate today = today();

        Long primary = null;
        Long voting = null;

        for (TMemberDistrictAffiliation a : affiliations.findActiveByMemberId(memberId, today)) {
            if (a.getKind() == AffiliationKind.PRIMARY) {
                primary = a.getDistrict().getId();
            }

            if (a.isVotesHere()) {
                voting = a.getDistrict().getId();
            }
        }

        return rights(member, suspendedIds(today).contains(memberId), primary, voting, today);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean suspendedDuring(long memberId, LocalDate from, @Nullable LocalDate to) {
        return jdbc.sql(SUSPENSIONS_OVERLAPPING)
                   .param("memberId", memberId)
                   .param("from", from)
                   .param("to", to, Types.DATE)
                   .query(Long.class)
                   .single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TMember> nationalElectorate() {
        LocalDate today = today();
        Set<Long> suspended = suspendedIds(today);

        return members.findByStatusAndMembershipType(MemberStatus.ACTIVE, MembershipType.ORDINARY)
                      .stream()
                      .filter(m -> rights(m, suspended.contains(m.getId()), null, null, today).eligible())
                      .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TMember> districtElectorate(long districtId) {
        return electorate(districtId, a -> a.getKind() == AffiliationKind.PRIMARY);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TMember> delegateElectorate(long districtId) {
        return electorate(districtId, TMemberDistrictAffiliation::isVotesHere);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KkwDistrictFunction> kkwMembersWithDistrictFunctions() {
        LocalDate today = today();

        Map<Long, Long> kkwBodyByMember = new HashMap<>();
        for (TBodyMembership m : bodyMemberships.findActiveByKindIn(EnumSet.of(BodyKind.ELECTORAL_COMMISSION), today)) {
            kkwBodyByMember.putIfAbsent(m.getMember().getId(), m.getBody().getId());
        }

        Map<Long, Set<Long>> districtsByMember = new HashMap<>();
        EnumSet<BodyKind> districtFunctions = EnumSet.noneOf(BodyKind.class);
        for (BodyKind kind : BodyKind.values()) {
            if (kind.isDistrictFunction()) {
                districtFunctions.add(kind);
            }
        }

        for (TBodyMembership m : bodyMemberships.findActiveByKindIn(districtFunctions, today)) {
            if (!kkwBodyByMember.containsKey(m.getMember().getId())) {
                continue;
            }

            Long districtId = districtOf(m.getBody());

            if (districtId != null) {
                districtsByMember.computeIfAbsent(m.getMember().getId(), k -> new HashSet<>()).add(districtId);
            }
        }

        return districtsByMember.entrySet()
                                .stream()
                                .map(e -> new KkwDistrictFunction(e.getKey(), kkwBodyByMember.get(e.getKey()), e.getValue()))
                                .sorted(Comparator.comparingLong(KkwDistrictFunction::memberId))
                                .toList();
    }

    private List<TMember> electorate(long districtId, Predicate<TMemberDistrictAffiliation> filter) {
        LocalDate today = today();
        Set<Long> suspended = suspendedIds(today);

        return affiliations.findActiveByDistrictId(districtId, today)
                           .stream()
                           .filter(filter)
                           .map(TMemberDistrictAffiliation::getMember)
                           .filter(m -> rights(m, suspended.contains(m.getId()), districtId, districtId, today).eligible())
                           .sorted(Comparator.comparing(TMember::getId))
                           .toList();
    }

    private ElectoralRights rights(
        TMember member,
        boolean suspended,
        @Nullable Long primary,
        @Nullable Long voting,
        LocalDate today
    ) {
        boolean activeOrdinary = member.getStatus() == MemberStatus.ACTIVE
            && member.getMembershipType() == MembershipType.ORDINARY;

        FeeArrearsPolicy policy = feeArrearsPolicy.getIfAvailable(() -> (m, d) -> false);

        return new ElectoralRights(
            member.getId(),
            activeOrdinary,
            suspended || member.getStatus() == MemberStatus.SUSPENDED,
            activeOrdinary && policy.hasUnjustifiedArrears(member, today),
            primary,
            voting
        );
    }

    private Set<Long> suspendedIds(LocalDate today) {
        return new HashSet<>(jdbc.sql(ACTIVE_SUSPENSIONS).param("today", today).query(Long.class).list());
    }

    private static @Nullable Long districtOf(TBody body) {
        if (body.isDistrict()) {
            return body.getId();
        }

        TBody parent = body.getParentBody();
        return parent == null ? null : parent.getId();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
