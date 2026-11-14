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
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.NotFoundException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.members.ChangeRequestDetails;
import org.mlodzirazem.panel.services.panelapi.members.ChangeRequestService;
import org.mlodzirazem.panel.services.panelapi.members.EffectivePermissions;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberFieldGroup;
import org.mlodzirazem.panel.services.panelapi.members.PermissionService;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.ChangeRequestStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberChangeRequest;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberChangeRequestRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class DefaultChangeRequestService implements ChangeRequestService {
    private static final Set<MemberField> FORMAL_FIELDS = MemberField.inGroup(MemberFieldGroup.FORMAL);

    private final TMemberChangeRequestRepository requests;
    private final TMemberRepository members;
    private final PermissionService permissions;
    private final MemberFieldCodec codec;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    @Transactional
    public TMemberChangeRequest create(
        long memberId,
        long requestedById,
        Map<MemberField, @Nullable Object> proposed,
        @Nullable String reason
    ) {
        TMember member = member(memberId);
        TMember requester = member(requestedById);

        Map<MemberField, @Nullable Object> effective = new EnumMap<>(MemberField.class);
        Map<MemberField, @Nullable Object> snapshot = new EnumMap<>(MemberField.class);

        proposed.forEach((field, value) -> {
            if (!FORMAL_FIELDS.contains(field)) {
                throw new RuleViolationException("NOT_FORMAL_FIELD",
                    field.propertyName() + " cannot be changed through a change request");
            }

            Object current = field.get(member);

            if (value != null && !field.type().isInstance(value)) {
                throw new RuleViolationException("INVALID_VALUE", field.propertyName() + " has an invalid type");
            }

            if (value == null && isRequired(field)) {
                throw new RuleViolationException("FIELD_REQUIRED", field.propertyName() + " is required");
            }

            if (!Objects.equals(codec.canonical(current), codec.canonical(value))) {
                effective.put(field, value);
                snapshot.put(field, current);
            }
        });

        if (effective.isEmpty()) {
            throw new RuleViolationException("NO_CHANGES", "The request changes nothing");
        }

        if (requests.findByMemberIdAndStatus(memberId, ChangeRequestStatus.PENDING).isPresent()) {
            throw new ConflictException("PENDING_EXISTS", "The member already has a pending change request");
        }

        TMemberChangeRequest request = new TMemberChangeRequest();
        request.setMember(member);
        request.setRequestedBy(requester);
        request.setProposedValues(codec.toJson(effective));
        request.setSnapshot(codec.toJson(snapshot));
        request.setReason(reason);
        request.setCreatedBy(requester);
        requests.saveAndFlush(request);

        auditLog.record("members :: change-request-create", payload(request, effective.keySet())
            .withOutcome(ChangeRequestStatus.PENDING.name()));

        return request;
    }

    @Override
    @Transactional
    public TMemberChangeRequest cancel(long requestId, long actorMemberId) {
        TMemberChangeRequest request = lockedPending(requestId);

        if (!request.getRequestedBy().getId().equals(actorMemberId)) {
            throw new ForbiddenException("Only the requester can cancel a change request");
        }

        return decide(request, ChangeRequestStatus.CANCELLED, actorMemberId, null, "members :: change-request-cancel");
    }

    @Override
    @Transactional
    public TMemberChangeRequest approve(long requestId, long deciderMemberId, @Nullable String note) {
        TMemberChangeRequest request = lockedPending(requestId);
        requireNotSelf(deciderMemberId, request);
        requireManage(deciderMemberId, request);

        TMember member = request.getMember();
        Map<MemberField, @Nullable Object> proposed = codec.fromJson(request.getProposedValues());
        Map<MemberField, @Nullable Object> snapshot = codec.fromJson(request.getSnapshot());

        List<String> stale = proposed.keySet()
                                     .stream()
                                     .filter(field -> !Objects.equals(
                                         codec.canonical(field.get(member)),
                                         codec.canonical(snapshot.get(field))))
                                     .map(MemberField::propertyName)
                                     .sorted()
                                     .toList();

        if (!stale.isEmpty()) {
            throw new ConflictException("STALE_SNAPSHOT", "Fields changed since the request was made", stale);
        }

        proposed.forEach((field, value) -> field.set(member, value));
        member.setUpdatedBy(members.getReferenceById(deciderMemberId));
        members.saveAndFlush(member);

        return decide(request, ChangeRequestStatus.APPROVED, deciderMemberId, note,
            "members :: change-request-approve");
    }

    @Override
    @Transactional
    public TMemberChangeRequest reject(long requestId, long deciderMemberId, @Nullable String note) {
        TMemberChangeRequest request = lockedPending(requestId);
        requireNotSelf(deciderMemberId, request);
        requireManage(deciderMemberId, request);

        return decide(request, ChangeRequestStatus.REJECTED, deciderMemberId, note, "members :: change-request-reject");
    }

    @Override
    @Transactional(readOnly = true)
    public ChangeRequestDetails details(long requestId) {
        TMemberChangeRequest request = requests.findById(requestId)
                                               .orElseThrow(() -> new NotFoundException("Change request", requestId));

        return new ChangeRequestDetails(
            request,
            codec.fromJson(request.getProposedValues()),
            codec.fromJson(request.getSnapshot())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TMemberChangeRequest> forMember(long memberId) {
        return requests.findAllByMemberId(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TMemberChangeRequest> pendingFor(long actorMemberId) {
        EffectivePermissions actor = permissions.of(actorMemberId);

        if (!actor.can(PermissionType.FORMAL_PERSONAL_DATA_MANAGE)) {
            return List.of();
        }

        return requests.findByStatusOrderByCreatedAtAsc(ChangeRequestStatus.PENDING)
                       .stream()
                       .filter(r -> permissions.can(actorMemberId, PermissionType.FORMAL_PERSONAL_DATA_MANAGE,
                           r.getMember().getId()))
                       .collect(Collectors.toList());
    }

    private TMemberChangeRequest decide(
        TMemberChangeRequest request,
        ChangeRequestStatus status,
        long actorMemberId,
        @Nullable String note,
        String operationId
    ) {
        TMember actor = members.getReferenceById(actorMemberId);

        request.setStatus(status);
        request.setDecidedBy(actor);
        request.setDecidedAt(ZonedDateTime.now(clock));
        request.setDecisionNote(note);
        request.setUpdatedBy(actor);
        requests.saveAndFlush(request);

        auditLog.record(operationId, payload(request, codec.fromJson(request.getProposedValues()).keySet())
            .withId("deciderId", actorMemberId)
            .withOutcome(status.name()));

        return request;
    }

    private void requireNotSelf(long deciderMemberId, TMemberChangeRequest request) {
        if (request.getRequestedBy().getId().equals(deciderMemberId)
            || request.getMember().getId().equals(deciderMemberId)) {
            throw new ForbiddenException("SELF_DECISION_FORBIDDEN", "Nobody decides their own change request");
        }
    }

    private void requireManage(long deciderMemberId, TMemberChangeRequest request) {
        if (!permissions.can(deciderMemberId, PermissionType.FORMAL_PERSONAL_DATA_MANAGE,
            request.getMember().getId())) {
            throw new ForbiddenException("Missing FORMAL_PERSONAL_DATA_MANAGE for the member's district");
        }
    }

    private TMemberChangeRequest lockedPending(long requestId) {
        TMemberChangeRequest request = requests.findByIdForUpdate(requestId)
                                               .orElseThrow(() -> new NotFoundException("Change request", requestId));

        if (request.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ConflictException("NOT_PENDING", "The change request is already " + request.getStatus());
        }

        return request;
    }

    private TMember member(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new NotFoundException("Member", memberId));
    }

    private static boolean isRequired(MemberField field) {
        return field == MemberField.FORMAL_NAME_FIRST
            || field == MemberField.FORMAL_NAME_LAST
            || field == MemberField.BIRTH_DATE;
    }

    private static AuditPayload payload(TMemberChangeRequest request, Set<MemberField> fields) {
        return AuditPayload.of("changeRequestId", request.getId())
                           .withId("memberId", request.getMember().getId())
                           .withFields(fields.stream().map(MemberField::propertyName).toList());
    }
}
