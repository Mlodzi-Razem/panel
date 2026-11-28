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
import org.mlodzirazem.panel.services.panelapi.members.MemberFeeStateChanged;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.MemberUpdate;
import org.mlodzirazem.panel.services.panelapi.members.NewMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

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

    /**
     * Friendly translations for SQL 23505 on the member registry's unique keys (and the e-mail
     * sync trigger's row in member_emails), looked up by constraint name.
     */
    private static final Map<String, RuntimeException> UNIQUE_KEY_CONFLICTS = Map.of(
        "members_member_id_key",
        new ConflictException("MEMBER_ID_EXISTS", "The member number is already taken",
            List.of("memberId")),
        "members_domain_email_key",
        new ConflictException("DOMAIN_EMAIL_EXISTS", "The domain e-mail is already taken",
            List.of("domainEmail")),
        "members_formal_identifier_key",
        new ConflictException("FORMAL_IDENTIFIER_EXISTS", "The formal identifier is already taken",
            List.of("formalIdentifier")),
        "members_private_emails_key",
        new ConflictException("PRIVATE_EMAILS_EXISTS", "One of the private e-mails is already taken",
            List.of("privateEmails")),
        "member_emails_pkey",
        new ConflictException("EMAIL_ALREADY_USED", "One of these e-mails already belongs to another member",
            List.of("domainEmail", "privateEmails"))
    );

    private static final Set<MemberField> FEE_FIELDS = EnumSet.of(
        MemberField.STATUS,
        MemberField.MEMBERSHIP_TYPE,
        MemberField.MEMBER_SINCE
    );

    private final TMemberRepository members;
    private final AffiliationService affiliations;
    private final CurrentMemberResolver currentMemberResolver;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TMember get(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
    }

    @Override
    @Transactional
    public TMember create(NewMember newMember) {
        rejectMembershipActValuesOnCreate(newMember);

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

        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException conflict) {
            throw ConstraintConflicts.translate(conflict, UNIQUE_KEY_CONFLICTS);
        }

        affiliations.setPrimaryDistrict(member.getId(), newMember.primaryDistrictId(), false,
            newMember.affiliationValidFrom());

        auditLog.record("members :: create", AuditPayload.of("memberId", member.getId())
                                                         .withFields(names(newMember.values().keySet())));
        events.publishEvent(new MemberFeeStateChanged(member.getId()));

        return member;
    }

    @Override
    @Transactional
    public MemberUpdate update(long memberId, Map<MemberField, @Nullable Object> changes) {
        TMember member = get(memberId);

        rejectMembershipActChanges(member, changes);

        Set<MemberField> changed = apply(member, changes);

        if (!changed.isEmpty()) {
            currentMemberResolver.current().ifPresent(member::setUpdatedBy);

            try {
                members.saveAndFlush(member);
            } catch (DataIntegrityViolationException conflict) {
                throw ConstraintConflicts.translate(conflict, UNIQUE_KEY_CONFLICTS);
            }

            auditLog.record("members :: update", AuditPayload.of("memberId", memberId).withFields(names(changed)));

            if (changed.stream().anyMatch(FEE_FIELDS::contains)) {
                events.publishEvent(new MemberFeeStateChanged(memberId));
            }
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

    /**
     * Status, membership type and removal date change only through {@link
     * org.mlodzirazem.panel.services.panelapi.members.MembershipActService}: every such change is a resolution with a
     * decision reference, recorded in the acts register. A plain update that would make or undo a removal, or change
     * the membership type in any way (ordinary, supporting, honorary), or the removal date is refused.
     */
    private static void rejectMembershipActChanges(TMember member, Map<MemberField, @Nullable Object> changes) {
        if (changes.containsKey(MemberField.STATUS)) {
            Object next = changes.get(MemberField.STATUS);

            boolean removal = next == MemberStatus.REMOVED || member.getStatus() == MemberStatus.REMOVED;

            if (next != member.getStatus() && removal) {
                throw membershipActRequired(MemberField.STATUS);
            }
        }

        if (changes.containsKey(MemberField.REMOVED_AT) && !Objects.equals(changes.get(MemberField.REMOVED_AT),
            member.getRemovedAt())) {
            throw membershipActRequired(MemberField.REMOVED_AT);
        }

        if (changes.containsKey(MemberField.MEMBERSHIP_TYPE)) {
            Object next = changes.get(MemberField.MEMBERSHIP_TYPE);

            if (next != member.getMembershipType()) {
                throw membershipActRequired(MemberField.MEMBERSHIP_TYPE);
            }
        }
    }

    /** Creation may not start a member as removed or as honorary/supporting, except through the acts service. */
    private static void rejectMembershipActValuesOnCreate(NewMember newMember) {
        Map<MemberField, @Nullable Object> values = newMember.values();

        if (values.get(MemberField.STATUS) == MemberStatus.REMOVED || values.get(MemberField.REMOVED_AT) != null) {
            throw membershipActRequired(MemberField.STATUS);
        }

        Object type = values.get(MemberField.MEMBERSHIP_TYPE);

        if (type != null && type != MembershipType.ORDINARY && !newMember.allowMembershipType()) {
            throw membershipActRequired(MemberField.MEMBERSHIP_TYPE);
        }
    }

    private static ConflictException membershipActRequired(MemberField field) {
        return new ConflictException("MEMBERSHIP_ACT_REQUIRED",
            "This change is made only by a membership act with a decision reference", List.of(field.propertyName()));
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

            if (field == MemberField.MEMBER_ID && value != null) {
                MemberRules.assertMemberNumberNotReserved((String) value);
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
