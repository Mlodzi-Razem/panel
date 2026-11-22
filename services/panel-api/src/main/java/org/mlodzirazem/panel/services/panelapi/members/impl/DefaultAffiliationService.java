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
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ConstraintConflicts;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.members.MemberDistricts;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.AffiliationKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliation;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
class DefaultAffiliationService implements AffiliationService {
    private static final String OPERATION_PRIMARY = "members :: affiliation-primary";
    private static final String OPERATION_SUPPORTING = "members :: affiliation-supporting";
    private static final String OPERATION_VOTING = "members :: affiliation-voting";
    private static final List<String> FIELDS = List.of("districtAffiliations");

    /**
     * Friendly translations for SQL 23505 on the partial unique indexes guarding open affiliations,
     * looked up by index name.
     */
    private static final Map<String, RuntimeException> OPEN_AFFILIATION_INDEXES = Map.of(
        "member_district_affiliations_open_primary_idx",
        new ConflictException("OPEN_PRIMARY_EXISTS", "The member already has an open primary district affiliation",
            List.of("districtAffiliations")),
        "member_district_affiliations_open_votes_idx",
        new ConflictException("OPEN_VOTES_EXISTS", "The member already votes in another open district affiliation",
            List.of("districtAffiliations")),
        "member_district_affiliations_open_district_idx",
        new ConflictException("OPEN_DISTRICT_EXISTS", "The member already has an open affiliation with this district",
            List.of("districtAffiliations"))
    );

    private final TMemberDistrictAffiliationRepository affiliations;
    private final TMemberRepository members;
    private final TBodyRepository bodies;
    private final CurrentMemberResolver currentMemberResolver;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<TMemberDistrictAffiliation> active(long memberId) {
        return affiliations.findActiveByMemberId(memberId, today());
    }

    @Override
    @Transactional(readOnly = true)
    public MemberDistricts districtsOf(long memberId) {
        Long primary = null;
        Set<Long> supporting = new HashSet<>();

        for (TMemberDistrictAffiliation a : active(memberId)) {
            if (a.getKind() == AffiliationKind.PRIMARY) {
                primary = a.getDistrict().getId();
            } else {
                supporting.add(a.getDistrict().getId());
            }
        }

        return new MemberDistricts(primary, supporting);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TBody> primaryDistrict(long memberId) {
        return active(memberId).stream()
                               .filter(a -> a.getKind() == AffiliationKind.PRIMARY)
                               .map(TMemberDistrictAffiliation::getDistrict)
                               .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TBody> votingDistrict(long memberId) {
        return active(memberId).stream()
                               .filter(TMemberDistrictAffiliation::isVotesHere)
                               .map(TMemberDistrictAffiliation::getDistrict)
                               .findFirst();
    }

    @Override
    @Transactional
    public TMemberDistrictAffiliation setPrimaryDistrict(
        long memberId,
        long districtId,
        boolean keepPreviousAsSupporting
    ) {
        TMember member = member(memberId);
        TBody district = district(districtId);
        List<TMemberDistrictAffiliation> open = affiliations.findOpenByMemberId(memberId);

        TMemberDistrictAffiliation currentPrimary = open.stream()
                                                        .filter(a -> a.getKind() == AffiliationKind.PRIMARY)
                                                        .findFirst()
                                                        .orElse(null);

        if (currentPrimary != null && currentPrimary.getDistrict().getId().equals(districtId)) {
            return currentPrimary;
        }

        TMemberDistrictAffiliation promoted = open.stream()
                                                  .filter(a -> a.getKind() == AffiliationKind.SUPPORTING
                                                      && a.getDistrict().getId().equals(districtId))
                                                  .findFirst()
                                                  .orElse(null);

        boolean votesHere = (currentPrimary != null && currentPrimary.isVotesHere())
            || (promoted != null && promoted.isVotesHere())
            || open.stream().noneMatch(TMemberDistrictAffiliation::isVotesHere);

        if (promoted != null) {
            endOrDelete(promoted);
        }

        if (currentPrimary != null) {
            TBody previousDistrict = currentPrimary.getDistrict();
            endOrDelete(currentPrimary);

            if (keepPreviousAsSupporting) {
                create(member, previousDistrict, AffiliationKind.SUPPORTING, false);
            }
        }

        TMemberDistrictAffiliation created = create(member, district, AffiliationKind.PRIMARY, votesHere);

        auditLog.record(OPERATION_PRIMARY, payload(memberId, districtId));

        return created;
    }

    @Override
    @Transactional
    public TMemberDistrictAffiliation addSupportingDistrict(long memberId, long districtId, boolean votesHere) {
        TMember member = member(memberId);
        TBody district = district(districtId);
        List<TMemberDistrictAffiliation> open = affiliations.findOpenByMemberId(memberId);

        if (open.stream().noneMatch(a -> a.getKind() == AffiliationKind.PRIMARY)) {
            throw new RuleViolationException("NO_PRIMARY_DISTRICT", "Member has no primary district");
        }

        if (open.stream().anyMatch(a -> a.getDistrict().getId().equals(districtId))) {
            throw new RuleViolationException("ALREADY_AFFILIATED", "Member is already affiliated with the district");
        }

        if (votesHere) {
            clearVotes(open);
        }

        TMemberDistrictAffiliation created = create(member, district, AffiliationKind.SUPPORTING, votesHere);

        auditLog.record(OPERATION_SUPPORTING, payload(memberId, districtId));

        return created;
    }

    @Override
    @Transactional
    public void endSupportingDistrict(long memberId, long districtId) {
        List<TMemberDistrictAffiliation> open = affiliations.findOpenByMemberId(memberId);

        TMemberDistrictAffiliation supporting = open.stream()
                                                    .filter(a -> a.getKind() == AffiliationKind.SUPPORTING
                                                        && a.getDistrict().getId().equals(districtId))
                                                    .findFirst()
                                                    .orElseThrow(() -> new NotFoundException(
                                                        "Supporting affiliation of member " + memberId + " with district",
                                                        districtId));

        boolean hadVotes = supporting.isVotesHere();
        endOrDelete(supporting);

        if (hadVotes) {
            open.stream()
                .filter(a -> a.getKind() == AffiliationKind.PRIMARY)
                .findFirst()
                .ifPresent(primary -> {
                    primary.setVotesHere(true);
                    write(primary);
                });
        }

        auditLog.record(OPERATION_SUPPORTING, payload(memberId, districtId));
    }

    @Override
    @Transactional
    public void setVotingDistrict(long memberId, long districtId) {
        List<TMemberDistrictAffiliation> open = affiliations.findOpenByMemberId(memberId);

        TMemberDistrictAffiliation target = open.stream()
                                                .filter(a -> a.getDistrict().getId().equals(districtId))
                                                .findFirst()
                                                .orElseThrow(() -> new RuleViolationException(
                                                    "NOT_AFFILIATED", "Member is not affiliated with the district"));

        if (target.isVotesHere()) {
            return;
        }

        clearVotes(open);
        target.setVotesHere(true);
        write(target);

        auditLog.record(OPERATION_VOTING, payload(memberId, districtId));
    }

    private void clearVotes(List<TMemberDistrictAffiliation> open) {
        boolean changed = false;

        for (TMemberDistrictAffiliation a : open) {
            if (a.isVotesHere()) {
                a.setVotesHere(false);
                affiliations.save(a);
                changed = true;
            }
        }

        if (changed) {
            flushWrites();
        }
    }

    private void endOrDelete(TMemberDistrictAffiliation affiliation) {
        LocalDate today = today();

        if (affiliation.getValidFrom().isBefore(today)) {
            affiliation.setValidTo(today);
            affiliation.setUpdatedBy(actor());
            write(affiliation);
        } else {
            affiliations.delete(affiliation);
            flushWrites();
        }
    }

    private TMemberDistrictAffiliation create(
        TMember member,
        TBody district,
        AffiliationKind kind,
        boolean votesHere
    ) {
        TMemberDistrictAffiliation affiliation = new TMemberDistrictAffiliation();
        affiliation.setMember(member);
        affiliation.setDistrict(district);
        affiliation.setKind(kind);
        affiliation.setVotesHere(votesHere);
        affiliation.setValidFrom(today());
        affiliation.setCreatedBy(actor());

        return write(affiliation);
    }

    private TMemberDistrictAffiliation write(TMemberDistrictAffiliation affiliation) {
        try {
            return affiliations.saveAndFlush(affiliation);
        } catch (DataIntegrityViolationException conflict) {
            throw ConstraintConflicts.translate(conflict, OPEN_AFFILIATION_INDEXES);
        }
    }

    private void flushWrites() {
        try {
            affiliations.flush();
        } catch (DataIntegrityViolationException conflict) {
            throw ConstraintConflicts.translate(conflict, OPEN_AFFILIATION_INDEXES);
        }
    }

    private TMember member(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
    }

    private TBody district(long districtId) {
        TBody body = bodies.findById(districtId).orElseThrow(() -> new NotFoundException("District", districtId));

        if (!body.isDistrict()) {
            throw new RuleViolationException("NOT_A_DISTRICT", "Body " + districtId + " is not a district");
        }

        return body;
    }

    private @Nullable TMember actor() {
        return currentMemberResolver.current().orElse(null);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static AuditPayload payload(long memberId, long districtId) {
        return AuditPayload.of("memberId", memberId).withId("districtId", districtId).withFields(FIELDS);
    }
}
