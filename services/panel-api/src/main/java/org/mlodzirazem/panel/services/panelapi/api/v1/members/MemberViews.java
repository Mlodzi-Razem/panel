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

package org.mlodzirazem.panel.services.panelapi.api.v1.members;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.api.v1.common.Caller;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.ElectoralRights;
import org.mlodzirazem.panel.services.panelapi.members.ElectoralRightsService;
import org.mlodzirazem.panel.services.panelapi.members.MemberDistricts;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberFieldGroup;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.bodies.BodyMembershipService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.AffiliationKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliation;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mlodzirazem.panel.services.panelapi.api.v1.common.Names.mask;

@Component
@RequiredArgsConstructor
public class MemberViews {
    private static final Set<MemberField> SUMMARY_CONTACT = EnumSet.of(
        MemberField.PREFERRED_NAME_FIRST,
        MemberField.PREFERRED_NAME_MIDDLE,
        MemberField.PREFERRED_NAME_LAST,
        MemberField.PSEUDONYM
    );
    private static final Set<MemberField> SUMMARY_FORMAL = EnumSet.of(
        MemberField.STATUS,
        MemberField.MEMBERSHIP_TYPE,
        MemberField.MEMBER_ID,
        MemberField.DOMAIN_EMAIL
    );

    private final AffiliationService affiliations;
    private final TMemberDistrictAffiliationRepository affiliationRepository;
    private final BodyMembershipService bodyMemberships;
    private final ElectoralRightsService electoralRights;
    private final AuditLog auditLog;
    private final Clock clock;

    public record Visibility(
        boolean readContact,
        boolean readFormal,
        boolean manageContact,
        boolean manageFormal,
        boolean reveal
    ) {
        public boolean canRead() {
            return readContact || readFormal;
        }

        boolean allows(MemberFieldGroup group) {
            return switch (group) {
                case CONTACT, NAMING -> readContact;
                case FORMAL, ORGANISATIONAL -> readFormal;
            };
        }
    }

    public static final Visibility OWN = new Visibility(true, true, true, false, false);

    public Visibility visibility(Caller caller, TMember member, boolean revealRequested) {
        MemberDistricts districts = affiliations.districtsOf(member.getId());

        return visibility(caller, districts, revealRequested);
    }

    public Visibility visibility(Caller caller, MemberDistricts districts, boolean revealRequested) {
        var p = caller.permissions();
        boolean readContact = p.canForMember(PermissionType.CONTACT_PERSONAL_DATA_READ, districts);
        boolean readFormal = p.canForMember(PermissionType.FORMAL_PERSONAL_DATA_READ, districts);
        boolean manageContact = p.canForMember(PermissionType.CONTACT_PERSONAL_DATA_MANAGE, districts);
        boolean manageFormal = p.canForMember(PermissionType.FORMAL_PERSONAL_DATA_MANAGE, districts);

        if (revealRequested && !manageFormal) {
            throw new ForbiddenException("FORMAL_PERSONAL_DATA_MANAGE is required to reveal the identifier");
        }

        return new Visibility(readContact, readFormal, manageContact, manageFormal, revealRequested && readFormal);
    }

    public MemberDetailResponse detail(TMember member, Visibility visibility) {
        Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        boolean revealed = false;

        for (MemberField field : MemberField.values()) {
            if (!visibility.allows(field.group())) {
                continue;
            }

            Object value = field.get(member);

            if (field == MemberField.FORMAL_IDENTIFIER && value instanceof String identifier) {
                if (visibility.reveal()) {
                    revealed = true;
                } else {
                    value = mask(identifier);
                }
            }

            fields.put(field.propertyName(), value);
        }

        if (revealed) {
            auditLog.record("members :: reveal-formal-identifier", AuditPayload
                .of("memberId", member.getId())
                .withFields(List.of(MemberField.FORMAL_IDENTIFIER.propertyName())));
        }

        List<MemberDetailResponse.AffiliationView> affiliationViews = null;
        LocalDate endsOn = null;
        MemberDetailResponse.ElectoralRightsView rights = null;

        if (visibility.readFormal()) {
            affiliationViews = affiliations.active(member.getId())
                                           .stream()
                                           .map(a -> new MemberDetailResponse.AffiliationView(
                                               a.getDistrict().getId(), a.getDistrict().getName(), a.getKind(),
                                               a.isVotesHere(), a.getValidFrom()))
                                           .toList();
            endsOn = endsOn(member);
            rights = rights(electoralRights.rightsOf(member.getId()));
        }

        return new MemberDetailResponse(
            member.getId(),
            fields,
            new MemberDetailResponse.Access(visibility.readContact(), visibility.readFormal(),
                visibility.manageContact(), visibility.manageFormal(), revealed),
            affiliationViews,
            bodyFunctions(member.getId()),
            functionHistory(member.getId()),
            endsOn,
            rights
        );
    }

    public Map<Long, List<TMemberDistrictAffiliation>> activeAffiliations(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }

        return affiliationRepository.findActiveByMemberIdIn(memberIds, LocalDate.now(clock))
                                    .stream()
                                    .collect(Collectors.groupingBy(a -> a.getMember().getId()));
    }

    public static MemberDistricts districtsOf(List<TMemberDistrictAffiliation> active) {
        Long primary = null;
        Set<Long> supporting = new HashSet<>();

        for (TMemberDistrictAffiliation a : active) {
            if (a.getKind() == AffiliationKind.PRIMARY) {
                primary = a.getDistrict().getId();
            } else {
                supporting.add(a.getDistrict().getId());
            }
        }

        return new MemberDistricts(primary, supporting);
    }

    public MemberSummaryResponse summary(
        TMember member,
        List<TMemberDistrictAffiliation> active,
        Caller caller,
        boolean withEnding
    ) {
        Visibility visibility = visibility(caller, districtsOf(active), false);
        Map<String, @Nullable Object> fields = new LinkedHashMap<>();

        if (visibility.readContact()) {
            SUMMARY_CONTACT.forEach(f -> fields.put(f.propertyName(), f.get(member)));
        }

        LocalDate endsOn = null;

        if (visibility.readFormal()) {
            SUMMARY_FORMAL.forEach(f -> fields.put(f.propertyName(), f.get(member)));

            if (withEnding) {
                fields.put(MemberField.BIRTH_DATE.propertyName(), member.getBirthDate());
                endsOn = endsOn(member);
            }
        }

        MemberSummaryResponse.DistrictRef primary = active.stream()
                                                          .filter(a -> a.getKind() == AffiliationKind.PRIMARY)
                                                          .map(a -> ref(a))
                                                          .findFirst()
                                                          .orElse(null);
        List<MemberSummaryResponse.DistrictRef> supporting = active.stream()
                                                                   .filter(a -> a.getKind()
                                                                       == AffiliationKind.SUPPORTING)
                                                                   .map(MemberViews::ref)
                                                                   .toList();

        return new MemberSummaryResponse(member.getId(), fields, primary, supporting, endsOn);
    }

    private static MemberSummaryResponse.DistrictRef ref(TMemberDistrictAffiliation a) {
        return new MemberSummaryResponse.DistrictRef(a.getDistrict().getId(), a.getDistrict().getName());
    }

    private @Nullable LocalDate endsOn(TMember member) {
        if (member.getMembershipType() == MembershipType.HONORARY) {
            return null;
        }

        return MemberRules.membershipEndDate(member.getBirthDate());
    }

    private List<MemberDetailResponse.BodyFunctionView> bodyFunctions(long memberId) {
        return bodyMemberships.activeOf(memberId).stream().map(MemberViews::function).toList();
    }

    private List<MemberDetailResponse.FunctionHistoryView> functionHistory(long memberId) {
        LocalDate today = LocalDate.now(clock);
        Set<Long> current = bodyMemberships.activeOf(memberId).stream()
                                            .map(TBodyMembership::getId)
                                            .collect(Collectors.toSet());

        return bodyMemberships.historyOf(memberId).stream()
                              .map(bm -> new MemberDetailResponse.FunctionHistoryView(
                                  function(bm),
                                  current.contains(bm.getId()) ? MemberDetailResponse.FunctionState.CURRENT
                                      : bm.getValidFrom().isAfter(today) ? MemberDetailResponse.FunctionState.UPCOMING
                                      : MemberDetailResponse.FunctionState.ENDED,
                                  bm.getDecisionReference()))
                              .toList();
    }

    private static MemberDetailResponse.BodyFunctionView function(TBodyMembership bm) {
        return new MemberDetailResponse.BodyFunctionView(
            bm.getId(),
            bm.getBody().getId(),
            bm.getBody().getName(),
            bm.getBody().kind(),
            bm.getMemberType().getId(),
            bm.getMemberType().getCode(),
            bm.getMemberType().getName(),
            bm.getValidFrom(),
            bm.getValidTo()
        );
    }

    private static MemberDetailResponse.ElectoralRightsView rights(ElectoralRights r) {
        return new MemberDetailResponse.ElectoralRightsView(
            r.activeOrdinaryMember(), r.suspended(), r.feeArrears(), r.eligible(),
            r.canVoteInNationalElection(), r.primaryDistrictId(), r.votingDistrictId());
    }
}
