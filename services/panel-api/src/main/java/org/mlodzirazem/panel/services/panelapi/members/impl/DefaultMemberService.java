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
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.BodiesManagerGuard;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.MemberUpdate;
import org.mlodzirazem.panel.services.panelapi.members.NewMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DefaultMemberService implements MemberService {
    private static final Set<MemberField> NOT_NULL_FIELDS = EnumSet.of(
        MemberField.PREFERRED_NAME_FIRST,
        MemberField.PREFERRED_NAME_LAST,
        MemberField.FORMAL_NAME_FIRST,
        MemberField.FORMAL_NAME_LAST,
        MemberField.BIRTH_DATE,
        MemberField.STATUS,
        MemberField.MEMBERSHIP_TYPE
    );

    private final TMemberRepository members;
    private final AffiliationService affiliations;
    private final BodiesManagerGuard bodiesManagerGuard;
    private final CurrentMemberResolver currentMemberResolver;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TMember get(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
    }

    @Override
    @Transactional
    public TMember create(NewMember newMember) {
        for (MemberField required : List.of(
            MemberField.PREFERRED_NAME_FIRST,
            MemberField.PREFERRED_NAME_LAST,
            MemberField.FORMAL_NAME_FIRST,
            MemberField.FORMAL_NAME_LAST,
            MemberField.BIRTH_DATE
        )) {
            Object value = newMember.values().get(required);

            if (value == null || (value instanceof String s && s.isBlank())) {
                throw new RuleViolationException("FIELD_REQUIRED", required.propertyName() + " is required");
            }
        }

        TMember member = new TMember();
        member.setStatus(MemberStatus.ACTIVE);
        member.setMembershipType(MembershipType.ORDINARY);
        apply(member, newMember.values());

        if (newMember.enforceAdmissionAge()) {
            LocalDate admission = member.getMemberSince() != null ? member.getMemberSince() : LocalDate.now(clock);
            MemberRules.validateAdmissionAge(member.getBirthDate(), member.getMembershipType(), admission);
        }

        currentMemberResolver.current().ifPresent(member::setCreatedBy);
        members.saveAndFlush(member);

        affiliations.setPrimaryDistrict(member.getId(), newMember.primaryDistrictId(), false);

        auditLog.record("members :: create", AuditPayload.of("memberId", member.getId())
                                                         .withFields(names(newMember.values().keySet())));

        return member;
    }

    @Override
    @Transactional
    public MemberUpdate update(long memberId, Map<MemberField, @Nullable Object> changes) {
        boolean removing = changes.get(MemberField.STATUS) == MemberStatus.REMOVED;

        if (removing) {
            bodiesManagerGuard.lock();
        }

        TMember member = get(memberId);

        if (removing && member.getStatus() != MemberStatus.REMOVED
            && bodiesManagerGuard.wouldLeaveNone(List.of(), memberId, 0)) {
            throw new RuleViolationException(BodiesManagerGuard.VIOLATION_CODE,
                "Removing this member would leave nobody able to manage bodies");
        }

        Set<MemberField> changed = apply(member, changes);

        if (!changed.isEmpty()) {
            currentMemberResolver.current().ifPresent(member::setUpdatedBy);
            members.saveAndFlush(member);

            auditLog.record("members :: update", AuditPayload.of("memberId", memberId).withFields(names(changed)));
        }

        return new MemberUpdate(member, changed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TMember> membersEndingIn(int year) {
        int birthYear = year - MemberRules.MEMBERSHIP_END_AGE;

        return members.findByBirthDateBetween(
            LocalDate.of(birthYear, 1, 1),
            LocalDate.of(birthYear, 12, 31),
            EnumSet.of(MembershipType.ORDINARY, MembershipType.SUPPORTING),
            EnumSet.of(MemberStatus.ACTIVE, MemberStatus.SUSPENDED, MemberStatus.PROBATION)
        );
    }

    private static Set<MemberField> apply(TMember member, Map<MemberField, @Nullable Object> values) {
        Set<MemberField> changed = EnumSet.noneOf(MemberField.class);

        values.forEach((field, value) -> {
            if (value != null && !field.type().isInstance(value)) {
                throw new RuleViolationException("INVALID_VALUE", field.propertyName() + " has an invalid type");
            }

            if (value == null && NOT_NULL_FIELDS.contains(field)) {
                throw new RuleViolationException("FIELD_REQUIRED", field.propertyName() + " is required");
            }

            if (value instanceof String s && s.isBlank() && NOT_NULL_FIELDS.contains(field)) {
                throw new RuleViolationException("FIELD_REQUIRED", field.propertyName() + " is required");
            }

            Object current = field.get(member);

            if (!Objects.equals(current, value) && !(isEmptyList(current) && value == null)) {
                field.set(member, value);
                changed.add(field);
            }
        });

        return changed;
    }

    private static boolean isEmptyList(@Nullable Object value) {
        return value instanceof List<?> list && list.isEmpty();
    }

    private static Set<String> names(Set<MemberField> fields) {
        return fields.stream().map(MemberField::propertyName).collect(Collectors.toSet());
    }
}
