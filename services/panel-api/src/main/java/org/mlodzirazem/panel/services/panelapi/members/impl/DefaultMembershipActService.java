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
package org.mlodzirazem.panel.services.panelapi.members.impl;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.InvalidInputException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.BodiesManagerGuard;
import org.mlodzirazem.panel.services.panelapi.members.MemberRules;
import org.mlodzirazem.panel.services.panelapi.members.CurrentMemberResolver;
import org.mlodzirazem.panel.services.panelapi.members.MemberFeeStateChanged;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.MembershipActService;
import org.mlodzirazem.panel.services.panelapi.members.NewMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipActKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipRemovalGround;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberMembershipAct;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberMembershipActRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
class DefaultMembershipActService implements MembershipActService {
    private static final Set<String> AUDIT_FIELDS = Set.of("status", "removedAt", "membershipType", "decisionReference");
    private static final int REFERENCE_MAX_LENGTH = 200;
    private static final int REASON_MAX_LENGTH = 500;

    private final TMemberRepository members;
    private final TMemberMembershipActRepository acts;
    private final TBodyMembershipRepository bodyMemberships;
    private final MemberService memberService;
    private final BodiesManagerGuard bodiesManagerGuard;
    private final CurrentMemberResolver currentMemberResolver;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional
    public TMember grantSupporting(GrantSupporting grant) {
        String reference = reference(grant.decisionReference());
        LocalDate grantedOn = notInFuture(grant.grantedOn());

        TMember created = memberService.create(newPerson(grant.member(), MembershipType.SUPPORTING, grantedOn));

        auditLog.record("members :: membership-grant-supporting",
            AuditPayload.of("memberId", created.getId()).withFields(AUDIT_FIELDS));
        recordAct(created, MembershipActKind.SUPPORTING_GRANT, null, MemberStatus.ACTIVE, null,
            grantedOn, reference, null);

        return created;
    }

    @Override
    @Transactional
    public TMember grantHonorary(GrantHonorary grant) {
        String reference = reference(grant.decisionReference());
        LocalDate grantedOn = notInFuture(grant.grantedOn());

        TMember created = memberService.create(newPerson(grant.member(), MembershipType.HONORARY, grantedOn));

        auditLog.record("members :: membership-grant-honorary",
            AuditPayload.of("memberId", created.getId()).withFields(AUDIT_FIELDS));
        recordAct(created, MembershipActKind.HONORARY_GRANT, null, MemberStatus.ACTIVE, null,
            grantedOn, reference, null);

        return created;
    }

    @Override
    @Transactional
    public TMember grantHonoraryToMember(GrantHonoraryToMember grant) {
        String reference = reference(grant.decisionReference());
        LocalDate grantedOn = notInFuture(grant.grantedOn());
        TMember member = memberService.get(grant.memberId());

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ConflictException("MEMBER_NOT_ACTIVE", "Only an active member can receive honorary membership");
        }

        if (member.getMembershipType() == MembershipType.HONORARY) {
            throw new ConflictException("ALREADY_HONORARY", "The member already has honorary membership");
        }

        if (bodyMemberships.countOpenEndedByMemberId(member.getId()) > 0) {
            throw new ConflictException("HAS_OPEN_MANDATES",
                "The member holds open-ended mandates; end them with their own resolution first");
        }

        MemberStatus previousStatus = member.getStatus();
        MembershipType previousType = member.getMembershipType();
        member.setMembershipType(MembershipType.HONORARY);
        save(member, "membership-grant-honorary");
        recordAct(member, MembershipActKind.HONORARY_GRANT, null, previousStatus, previousType, grantedOn, reference,
            null);

        return member;
    }

    @Override
    @Transactional
    public TMemberMembershipAct remove(Remove removal) {
        String reference = reference(removal.decisionReference());
        LocalDate removedOn = notInFuture(removal.removedOn());

        bodiesManagerGuard.lock();

        TMember member = memberService.get(removal.memberId());

        if (member.getStatus() == MemberStatus.REMOVED) {
            throw new ConflictException("ALREADY_REMOVED", "The member has already left the membership");
        }

        requireGroundApplies(member.getMembershipType(), removal.ground());

        if (bodiesManagerGuard.wouldLeaveNone(List.of(), member.getId(), 0)) {
            throw new RuleViolationException(BodiesManagerGuard.VIOLATION_CODE,
                "Removing this member would leave nobody able to manage bodies");
        }

        MemberStatus previousStatus = member.getStatus();
        MembershipType previousType = member.getMembershipType();
        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(removedOn);
        save(member, "membership-removal");

        return recordAct(member, MembershipActKind.REMOVAL, removal.ground(), previousStatus, previousType, removedOn,
            reference, null);
    }

    @Override
    @Transactional
    public TMemberMembershipAct reinstate(Reinstate reinstatement) {
        String reference = reference(reinstatement.decisionReference());
        String reason = reason(reinstatement.reason());
        LocalDate effectiveOn = notInFuture(reinstatement.effectiveOn());
        TMember member = memberService.get(reinstatement.memberId());

        if (member.getStatus() != MemberStatus.REMOVED) {
            throw new ConflictException("MEMBER_NOT_REMOVED", "Only a removed member can be reinstated");
        }

        MembershipType restored = typeRestoredOnReinstatement(member, effectiveOn);

        MemberStatus previousStatus = member.getStatus();
        MembershipType previousType = member.getMembershipType();
        member.setStatus(MemberStatus.ACTIVE);
        member.setRemovedAt(null);
        if (restored != null) {
            member.setMembershipType(restored);
        }
        save(member, "membership-reinstatement");

        return recordAct(member, MembershipActKind.REINSTATEMENT, null, previousStatus, previousType, effectiveOn,
            reference, reason);
    }

    @Override
    @Transactional
    public TMemberMembershipAct changeMembershipType(ChangeMembershipType change) {
        String reference = reference(change.decisionReference());
        String reason = reason(change.reason());
        LocalDate effectiveOn = notInFuture(change.effectiveOn());
        MembershipType target = change.target();

        if (target != MembershipType.ORDINARY && target != MembershipType.SUPPORTING) {
            throw new InvalidInputException("INVALID_VALUE", "Membership type can be changed only to ordinary or supporting");
        }

        TMember member = memberService.get(change.memberId());
        MembershipType previousType = member.getMembershipType();

        if (previousType == MembershipType.HONORARY) {
            throw new ConflictException("MEMBERSHIP_TYPE_NOT_CHANGEABLE",
                "Honorary membership is changed only by its own acts");
        }

        if (previousType == target) {
            throw new ConflictException("ALREADY_OF_TYPE", "The member already has this membership type");
        }

        MemberRules.validateAdmissionAge(member.getBirthDate(), target, effectiveOn);

        MemberStatus previousStatus = member.getStatus();
        member.setMembershipType(target);
        save(member, "membership-type-change");

        return persistAct(member, MembershipActKind.MEMBERSHIP_TYPE_CHANGE, null, previousStatus, previousType,
            target, effectiveOn, reference, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TMemberMembershipAct> history(long memberId) {
        memberService.get(memberId);

        return acts.findByMemberIdOrderByIdDesc(memberId);
    }

    /**
     * Values for a new person under an act. Creation starts from ACTIVE and ORDINARY (what {@code create} assigns
     * before the act applies), so those are recorded as the previous state of the act.
     */
    private NewMember newPerson(NewMember requested, MembershipType type, LocalDate grantedOn) {
        Map<MemberField, @Nullable Object> values = new EnumMap<>(MemberField.class);
        values.putAll(requested.values());
        values.put(MemberField.MEMBERSHIP_TYPE, type);
        values.put(MemberField.MEMBER_SINCE, grantedOn);

        return new NewMember(values, requested.primaryDistrictId(), true, requested.affiliationValidFrom(), true);
    }

    private void save(TMember member, String operation) {
        currentMemberResolver.current().ifPresent(member::setUpdatedBy);
        members.saveAndFlush(member);
        auditLog.record("members :: " + operation,
            AuditPayload.of("memberId", member.getId()).withFields(AUDIT_FIELDS));
        events.publishEvent(new MemberFeeStateChanged(member.getId()));
    }

    /**
     * The type to restore when a member removed on the ground HONORARY_REVOKED is reinstated: the type recorded as
     * before the latest honorary grant. Null means nothing is restored (other grounds). A grant to a new person has no
     * earlier type, so reinstatement is refused for it.
     */
    private @Nullable MembershipType typeRestoredOnReinstatement(TMember member, LocalDate effectiveOn) {
        List<TMemberMembershipAct> history = acts.findByMemberIdOrderByIdDesc(member.getId());

        Optional<TMemberMembershipAct> lastRemoval = history.stream()
            .filter(a -> a.getActKind() == MembershipActKind.REMOVAL).findFirst();

        if (lastRemoval.isEmpty() || lastRemoval.get().getRemovalGround() != MembershipRemovalGround.HONORARY_REVOKED) {
            return null;
        }

        MembershipType restored = history.stream()
            .filter(a -> a.getActKind() == MembershipActKind.HONORARY_GRANT)
            .findFirst()
            .map(TMemberMembershipAct::getPreviousMembershipType)
            .orElse(null);

        if (restored == null) {
            throw new ConflictException("NO_PREVIOUS_MEMBERSHIP_TYPE",
                "No membership type to restore: the honorary membership was granted to a new person");
        }

        MemberRules.validateAdmissionAge(member.getBirthDate(), restored, effectiveOn);

        return restored;
    }

    private TMemberMembershipAct recordAct(
        TMember member,
        MembershipActKind kind,
        @Nullable MembershipRemovalGround ground,
        MemberStatus previousStatus,
        @Nullable MembershipType previousType,
        LocalDate effectiveOn,
        String reference,
        @Nullable String reason
    ) {
        return persistAct(member, kind, ground, previousStatus, previousType, null, effectiveOn, reference, reason);
    }

    private TMemberMembershipAct persistAct(
        TMember member,
        MembershipActKind kind,
        @Nullable MembershipRemovalGround ground,
        MemberStatus previousStatus,
        @Nullable MembershipType previousType,
        @Nullable MembershipType typeAfter,
        LocalDate effectiveOn,
        String reference,
        @Nullable String reason
    ) {
        TMemberMembershipAct act = new TMemberMembershipAct();
        act.setMember(member);
        act.setActKind(kind);
        act.setRemovalGround(ground);
        act.setPreviousStatus(previousStatus);
        act.setPreviousMembershipType(previousType);
        act.setMembershipTypeAfter(typeAfter);
        act.setEffectiveOn(effectiveOn);
        act.setDecisionReference(reference);
        act.setReason(reason);
        currentMemberResolver.current().ifPresent(act::setCreatedBy);

        return acts.saveAndFlush(act);
    }

    private static void requireGroundApplies(MembershipType type, MembershipRemovalGround ground) {
        boolean applies = switch (ground) {
            case AGE_26 -> type == MembershipType.ORDINARY;
            case HONORARY_REVOKED -> type == MembershipType.HONORARY;
            default -> true;
        };

        if (!applies) {
            throw new RuleViolationException("GROUND_NOT_APPLICABLE",
                "This ground does not apply to the member's membership type");
        }
    }

    private static String reference(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("FIELD_REQUIRED", "decisionReference is required");
        }

        String reference = raw.strip();

        if (reference.length() > REFERENCE_MAX_LENGTH) {
            throw new InvalidInputException("INVALID_VALUE", "decisionReference is too long");
        }

        return reference;
    }

    private static String reason(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("FIELD_REQUIRED", "reason is required");
        }

        String reason = raw.strip();

        if (reason.length() > REASON_MAX_LENGTH) {
            throw new InvalidInputException("INVALID_VALUE", "reason is too long");
        }

        return reason;
    }

    private LocalDate notInFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new RuleViolationException("DATE_IN_FUTURE", "The date cannot be in the future");
        }

        return date;
    }
}
