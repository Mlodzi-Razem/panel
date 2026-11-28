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

package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.audit.AuditPayload;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountState;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountStatus;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AddressAvailability;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.DirectoryGroupMember;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.GroupCheck;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.MemberAccountProvisioning;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningActorSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningException;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningOperation;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningRequest;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTaskState;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTaskView;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTicket;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.TemporaryAccessPass;
import org.mlodzirazem.panel.services.panelapi.directory.provisioning.ProvisioningTasks.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of {@link MemberAccountProvisioning}. {@link #enqueue} only writes an outbox row in the caller's
 * transaction (also with the switch off, SEC-35): no business operation ever waits for Graph, and a rollback takes the
 * task with it. The methods that do call Graph refuse with {@code PROVISIONING_DISABLED} unless {@link ProvisioningGate}
 * says provisioning is on.
 */
@Service
@RequiredArgsConstructor
class DefaultMemberAccountProvisioning implements MemberAccountProvisioning {
    private final ProvisioningGate gate;
    private final ProvisioningTasks tasks;
    private final AccountRegistry registry;
    private final GraphDirectoryClient graph;
    private final ManagedAccountGuard guard;
    private final ObjectProvider<ProvisioningActorSource> actors;
    private final AuditLog auditLog;
    private final Clock clock;

    @Override
    public boolean enabled() {
        return gate.enabled();
    }

    @Override
    @Transactional
    public ProvisioningTicket enqueue(ProvisioningRequest request) {
        if (request.subject().id() <= 0 || (request.mappingIdOrNull() != null && request.mappingIdOrNull() <= 0)
            || (request.occurrenceIdOrNull() != null && request.occurrenceIdOrNull() <= 0)) {
            throw new IllegalArgumentException("Ids of a provisioning request are positive");
        }

        Optional<ProvisioningActorSource.Actor> actor = actor();

        if (request instanceof ProvisioningRequest.AdoptMemberAccount || request instanceof ProvisioningRequest.EnableAccount) {
            ProvisioningActorSource.Actor caller = actor.orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.ACTION_NOT_PERMITTED));

            if (!caller.manageUsers() || isSubject(caller, request.subject())) {
                throw new ProvisioningException(ProvisioningErrorCode.ACTION_NOT_PERMITTED);
            }
        }

        Long relatedCase = request instanceof ProvisioningRequest.ConvertTrialToMember convert ? convert.caseId() : null;
        Optional<Long> inserted = tasks.insert(request, relatedCase, actor.map(ProvisioningActorSource.Actor::memberId).orElse(null));

        if (inserted.isEmpty()) {
            Task existing = tasks.byKey(ProvisioningTasks.key(request));
            return new ProvisioningTicket(ProvisioningTicket.Dispatch.DEDUPLICATED, existing.id(), existing.state());
        }

        // W4: the person who asked is audited here, at the request; the worker only sees the system
        if (request instanceof ProvisioningRequest.AdoptMemberAccount || request instanceof ProvisioningRequest.EnableAccount) {
            String name = request instanceof ProvisioningRequest.AdoptMemberAccount ? "account-adopt-request" : "account-enable-request";
            auditLog.record("directory :: " + name,
                AuditPayload.of("taskId", inserted.get()).withId("subjectId", request.subject().id()).withOutcome("ENQUEUED"));
        }

        var opposite = request.operation().opposite();

        if (opposite != null) {
            int superseded = tasks.supersede(opposite, request.subject(), request.mappingIdOrNull(), inserted.get(), clock.instant());

            // a pending opposite intent that gave way to this one (e.g. an enable that cancels a waiting disable) is audited too
            if (superseded > 0 && actor.isPresent()) {
                auditLog.record("directory :: job-supersede", AuditPayload.of("taskId", inserted.get())
                    .withId("supersededCount", superseded).withOutcome("SUPERSEDED"));
            }
        }

        return new ProvisioningTicket(ProvisioningTicket.Dispatch.ENQUEUED, inserted.get(), ProvisioningTaskState.PENDING);
    }

    @Override
    public Optional<ProvisioningTaskView> task(long taskId) {
        return tasks.find(taskId).map(Task::view);
    }

    @Override
    @Transactional
    public ProvisioningTaskView retry(long taskId) {
        Task task = managedTask(taskId);

        if (!tasks.retry(taskId, clock.instant())) {
            throw new ProvisioningException(ProvisioningErrorCode.TASK_NOT_RETRYABLE);
        }

        auditLog.record("directory :: job-retry", AuditPayload.of("taskId", taskId).withId("subjectId", task.subject().id()).withOutcome("SUCCESS"));
        return tasks.find(taskId).orElseThrow().view();
    }

    @Override
    @Transactional
    public ProvisioningTaskView release(long taskId) {
        Task task = managedTask(taskId);
        long actorId = actor().orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.ACTION_NOT_PERMITTED)).memberId();

        if (!tasks.release(taskId, actorId, clock.instant())) {
            throw new ProvisioningException(ProvisioningErrorCode.TASK_NOT_RETRYABLE);
        }

        auditLog.record("directory :: job-release", AuditPayload.of("taskId", taskId).withId("subjectId", task.subject().id()).withOutcome("SUCCESS"));
        return tasks.find(taskId).orElseThrow().view();
    }

    @Override
    public AccountState accountState(AccountSubject subject) {
        Optional<AccountRegistry.Account> account = registry.find(subject).filter(a -> !a.deleted());
        Optional<Task> open = tasks.openAccountTask(subject);

        if (open.isPresent()) {
            Task task = open.get();
            return state(AccountStatus.PENDING, account.orElse(null), task.operation(), task.errorCode(), task.createdAt());
        }

        Optional<Task> finished = tasks.latestFinishedAccountTask(subject);

        if (finished.isPresent() && finished.get().state() == ProvisioningTaskState.FAILED) {
            Task task = finished.get();
            return state(AccountStatus.FAILED, account.orElse(null), null, task.errorCode(), task.finishedAt());
        }

        return account
            .map(a -> state("DISABLED".equals(a.status()) ? AccountStatus.DISABLED : AccountStatus.ACTIVE, a, null, null, a.updatedAt()))
            .orElseGet(AccountState::none);
    }

    @Override
    public TemporaryAccessPass issueTemporaryAccessPass(long accountId) {
        requireEnabled();

        AccountRegistry.Account account = registry.byId(accountId).filter(a -> !a.deleted())
            .orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_REGISTERED));

        if ("DISABLED".equals(account.status())) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_DISABLED);
        }

        GraphUser user = guard.verify(account);

        if (!Boolean.TRUE.equals(user.accountEnabled())) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_DISABLED);
        }

        return graph.issueTemporaryAccessPass(account.objectId());
    }

    @Override
    public GroupCheck checkGroup(String groupObjectId) {
        requireEnabled();
        return graph.checkGroup(groupId(groupObjectId));
    }

    @Override
    public List<DirectoryGroupMember> groupMembers(String groupObjectId) {
        requireEnabled();
        String group = groupId(groupObjectId);

        if (graph.checkGroup(group) != GroupCheck.MANAGEABLE) {
            throw new ProvisioningException(ProvisioningErrorCode.GROUP_NOT_MANAGEABLE);
        }

        Set<String> members = graph.groupUserIds(group);
        Set<String> owners = graph.groupOwnerIds(group);
        Set<String> everyone = new LinkedHashSet<>(members);
        everyone.addAll(owners);

        List<DirectoryGroupMember> result = new ArrayList<>();

        for (String objectId : everyone) {
            Optional<AccountRegistry.Account> account = registry.byObjectId(objectId);
            result.add(new DirectoryGroupMember(objectId, account.map(AccountRegistry.Account::id).orElse(null),
                account.map(AccountRegistry.Account::memberId).orElse(null), members.contains(objectId), owners.contains(objectId)));
        }

        return result;
    }

    @Override
    public AddressAvailability addressAvailability(String address) {
        if (!gate.enabled()) {
            return AddressAvailability.UNKNOWN;
        }

        return graph.addressAvailability(address);
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private void requireEnabled() {
        if (!gate.enabled()) {
            throw new ProvisioningException(ProvisioningErrorCode.PROVISIONING_DISABLED);
        }
    }

    private Optional<ProvisioningActorSource.Actor> actor() {
        ProvisioningActorSource source = actors.getIfAvailable();
        return source == null ? Optional.empty() : source.current();
    }

    /**
     * The task a person wants to retry or release: the permission matching its kind (accounts or groups) and never on
     * the person's own account. Without a permission nothing is revealed about whether the task exists.
     */
    private Task managedTask(long taskId) {
        ProvisioningActorSource.Actor caller = actor().filter(a -> a.manageUsers() || a.manageGroups())
            .orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.ACTION_NOT_PERMITTED));
        Task task = tasks.find(taskId).orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.TASK_NOT_RETRYABLE));
        boolean permitted = task.operation().groupOperation() ? caller.manageGroups() : caller.manageUsers();

        if (!permitted || isSubject(caller, task.subject())) {
            throw new ProvisioningException(ProvisioningErrorCode.ACTION_NOT_PERMITTED);
        }

        return task;
    }

    private static boolean isSubject(ProvisioningActorSource.Actor actor, AccountSubject subject) {
        return subject instanceof AccountSubject.Member member && member.id() == actor.memberId();
    }

    private static AccountState state(AccountStatus status, AccountRegistry.@Nullable Account account,
                                      @Nullable ProvisioningOperation pending,
                                      @Nullable ProvisioningErrorCode error, @Nullable Instant updatedAt) {
        return new AccountState(status,
            account == null ? null : account.id(),
            account == null ? null : account.kind(),
            account == null ? null : account.origin(),
            account == null ? null : account.number(),
            pending, error, updatedAt);
    }

    private static String groupId(String groupObjectId) {
        if (!GraphDirectoryClient.PrivateUuid.valid(groupObjectId)) {
            throw new IllegalArgumentException("A group object id must be a UUID");
        }

        return groupObjectId.toLowerCase();
    }
}
