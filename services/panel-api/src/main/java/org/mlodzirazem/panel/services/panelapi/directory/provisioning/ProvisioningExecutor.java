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

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProfile;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProfileSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountState.AccountKind;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountState.AccountOrigin;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.GroupCheck;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.GroupMappingSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningException;
import org.mlodzirazem.panel.services.panelapi.directory.provisioning.AccountRegistry.Account;
import org.mlodzirazem.panel.services.panelapi.directory.provisioning.ProvisioningTasks.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Carries out one outbox task against Graph. Every operation reads the state it is about to change and skips the write
 * when the target state is reached (Graph's {@code POST /users} and {@code assignLicense} are not idempotent), refuses
 * accounts that are not in the registry or fail the guard, and throws a {@link ProvisioningException} with a bare
 * code on failure. Personal data is read from the profile sources here and stays in this call.
 *
 * <p>The number bands, the registry and the guard make a task unable to touch an account outside what the Panel
 * manages; the order of the conversion steps (SEC-21) keeps a half-converted account from ever being linked to a member.</p>
 */
@Component
class ProvisioningExecutor {
    /** A trial account may be deleted only after it has been disabled for this long (SEC-24). */
    static final Duration TRIAL_DELETE_AFTER_DISABLE = Duration.ofDays(7);

    /**
     * @param alreadyDone the target state was reached without a write (idempotent replay)
     * @param deferUntil  not a failure: run again at that time without using an attempt
     * @param accountId   the registry id of the account involved
     */
    record Result(boolean alreadyDone, @Nullable Instant deferUntil, @Nullable Long accountId) {
        static Result done(@Nullable Long accountId) {
            return new Result(false, null, accountId);
        }

        static Result replay(@Nullable Long accountId) {
            return new Result(true, null, accountId);
        }

        static Result deferred(Instant until, @Nullable Long accountId) {
            return new Result(false, until, accountId);
        }
    }

    private final GraphDirectoryClient graph;
    private final AccountRegistry registry;
    private final ManagedAccountGuard guard;
    private final ProvisioningProperties properties;
    private final List<AccountProfileSource> profileSources;
    private final ObjectProvider<GroupMappingSource> mappings;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    ProvisioningExecutor(GraphDirectoryClient graph, AccountRegistry registry, ManagedAccountGuard guard,
                         ProvisioningProperties properties, List<AccountProfileSource> profileSources,
                         ObjectProvider<GroupMappingSource> mappings, PlatformTransactionManager transactionManager,
                         Clock clock) {
        this.graph = graph;
        this.registry = registry;
        this.guard = guard;
        this.properties = properties;
        this.profileSources = profileSources;
        this.mappings = mappings;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    Result run(Task task) {
        return switch (task.operation()) {
            case CREATE_TRIAL_ACCOUNT -> create(task, AccountKind.TRIAL);
            case CREATE_MEMBER_ACCOUNT -> create(task, AccountKind.MEMBER);
            case CONVERT_TRIAL_TO_MEMBER -> convert(task);
            case ADOPT_MEMBER_ACCOUNT -> adopt(task);
            case DISABLE_ACCOUNT -> setEnabled(task, false);
            case ENABLE_ACCOUNT -> setEnabled(task, true);
            case DELETE_ACCOUNT -> delete(task);
            case ADD_GROUP_MEMBER -> addToGroup(task);
            case REMOVE_GROUP_MEMBER -> removeFromGroup(task);
        };
    }

    // ------------------------------------------------------------------------------------------------- accounts

    private Result create(Task task, AccountKind kind) {
        AccountSubject subject = task.subject();
        Optional<Account> existing = registry.find(subject);

        if (existing.isPresent()) {
            Account account = existing.get();

            if (account.origin() == AccountOrigin.ADOPTED || account.deleted() || account.kind() != kind) {
                throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_ALREADY_REGISTERED);
            }

            // an earlier attempt created the account and stopped before the licence: resume
            GraphUser user = guard.verify(account);
            boolean changed = ensureLicense(account.objectId(), kind, user);

            return changed ? Result.done(account.id()) : Result.replay(account.id());
        }

        AccountProfile profile = profile(subject, kind);
        String number = profile.accountNumber();
        String upn = guard.upn(number);
        String objectId;

        Optional<GraphUser> found = graph.userByUpn(upn);

        if (found.isPresent()) {
            if (!createdForThisTask(found.get(), task, kind, number)) {
                throw new ProvisioningException(ProvisioningErrorCode.UPN_CONFLICT);
            }

            // the earlier attempt's POST succeeded but its answer was lost: do not create a second object
            objectId = found.get().id();
        } else {
            if (kind == AccountKind.MEMBER && !graph.userIdsByEmployeeId(number).isEmpty()) {
                throw new ProvisioningException(ProvisioningErrorCode.MEMBER_NUMBER_TAKEN_IN_ENTRA);
            }

            objectId = graph.createUser(new GraphDirectoryClient.NewUser(upn, profile.displayName(), profile.givenName(),
                profile.surname(), profile.department(), kind == AccountKind.MEMBER ? number : null, domainMail(profile.mail())));
        }

        Account account = register(subject, kind, AccountOrigin.CREATED, number, objectId);
        ensureLicense(objectId, kind, null);
        return Result.done(account.id());
    }

    /**
     * Trial to member in the order SEC-21 requires, each step re-readable: uniqueness check, user principal name and
     * profile, licences, and only at the very end the {@code employeeId} (from then on a sign-in may link the object to
     * the member). An interrupted conversion is resumed from what Graph shows.
     */
    private Result convert(Task task) {
        long memberId = task.subject().id();
        Long caseId = task.relatedCaseId();
        Optional<Account> memberAccount = registry.find(task.subject());
        Optional<Account> trial = caseId == null ? Optional.empty() : registry.find(new AccountSubject.Candidate(caseId));

        if (trial.isEmpty()) {
            // no trial account to convert (never created, or already converted): the member simply gets an account
            return memberAccount.isPresent() ? Result.replay(memberAccount.get().id()) : create(task, AccountKind.MEMBER);
        }

        if (memberAccount.isPresent()) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_ALREADY_REGISTERED);
        }

        Account trialAccount = trial.get();

        if (trialAccount.kind() != AccountKind.TRIAL || trialAccount.deleted()
            || properties.protectedObjectIds().contains(trialAccount.objectId().toLowerCase())) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        }

        AccountProfile profile = profile(task.subject(), AccountKind.MEMBER);
        String number = profile.accountNumber();
        String memberUpn = guard.upn(number);
        String objectId = trialAccount.objectId();

        GraphUser user = graph.user(objectId).orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.GRAPH_NOT_FOUND));
        boolean renamed = memberUpn.equalsIgnoreCase(user.userPrincipalName());

        if (renamed) {
            // resumed after step 2: the object is ours when it is a member user whose employeeId is empty or already the number
            if (!user.id().equalsIgnoreCase(objectId) || !"member".equalsIgnoreCase(user.userType())
                || (user.employeeId() != null && !user.employeeId().equals(number))) {
                throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
            }
        } else {
            guard.require(user, trialAccount.number(), AccountKind.TRIAL, objectId);

            // 1. nobody else holds the member number
            for (String holder : graph.userIdsByEmployeeId(number)) {
                if (!holder.equalsIgnoreCase(objectId)) {
                    throw new ProvisioningException(ProvisioningErrorCode.MEMBER_NUMBER_TAKEN_IN_ENTRA);
                }
            }

            Optional<GraphUser> clash = graph.userByUpn(memberUpn);

            if (clash.isPresent() && !clash.get().id().equalsIgnoreCase(objectId)) {
                throw new ProvisioningException(ProvisioningErrorCode.MEMBER_NUMBER_TAKEN_IN_ENTRA);
            }

            // 2. user principal name and profile
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("userPrincipalName", memberUpn);
            patch.put("mailNickname", number);
            patch.put("displayName", profile.displayName());
            patch.put("givenName", profile.givenName());
            patch.put("surname", profile.surname());

            if (profile.department() != null) {
                patch.put("department", profile.department());
            }

            if (properties.setMail() && domainMail(profile.mail()) != null) {
                patch.put("mail", domainMail(profile.mail()));
            }

            graph.patchUser(objectId, patch);
        }

        // 3. licences: the member's, and the trial's taken away when it differs
        String memberSku = sku(properties.memberLicenseSku());
        String trialSku = sku(properties.trialLicenseSku());
        List<String> remove = new ArrayList<>();

        if (trialSku != null && !trialSku.equals(memberSku) && user.licenseSkus().contains(trialSku)) {
            remove.add(trialSku);
        }

        String add = memberSku != null && !user.licenseSkus().contains(memberSku) ? memberSku : null;

        if (add != null || !remove.isEmpty()) {
            graph.assignLicense(objectId, add, remove);
        }

        // 4. employeeId last
        if (!number.equals(user.employeeId())) {
            graph.patchUser(objectId, Map.of("employeeId", number));
        }

        // 5. the registry follows
        registry.convertToMember(trialAccount.id(), memberId, number);
        return Result.done(trialAccount.id());
    }

    private Result adopt(Task task) {
        if (registry.find(task.subject()).isPresent()) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_ALREADY_REGISTERED);
        }

        AccountProfile profile = profile(task.subject(), AccountKind.MEMBER);
        String number = profile.accountNumber();
        GraphUser user = graph.userByUpn(guard.upn(number)).orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.ADOPTION_NO_MATCH));

        if (user.employeeId() == null || !user.employeeId().equals(number)) {
            throw new ProvisioningException(ProvisioningErrorCode.ADOPTION_NO_MATCH);
        }

        guard.require(user, number, AccountKind.MEMBER, user.id());

        if (registry.byObjectId(user.id()).isPresent()) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_ALREADY_REGISTERED);
        }

        Account account = register(task.subject(), AccountKind.MEMBER, AccountOrigin.ADOPTED, number, user.id());

        if (Boolean.FALSE.equals(user.accountEnabled())) {
            registry.setStatus(account.id(), "DISABLED");
        }

        return Result.done(account.id());
    }

    private Result setEnabled(Task task, boolean enabled) {
        Account account = registered(task.subject());
        GraphUser user = guard.verify(account);

        if (Boolean.valueOf(enabled).equals(user.accountEnabled())) {
            registry.setStatus(account.id(), enabled ? "ACTIVE" : "DISABLED");

            // D4: a replay after a crash between the PATCH and the revoke still revokes (idempotent)
            if (!enabled) {
                graph.revokeSignInSessions(account.objectId());
            }

            return Result.replay(account.id());
        }

        graph.patchUser(account.objectId(), Map.of("accountEnabled", enabled));
        registry.setStatus(account.id(), enabled ? "ACTIVE" : "DISABLED");

        if (!enabled) {
            graph.revokeSignInSessions(account.objectId());
        }

        return Result.done(account.id());
    }

    /**
     * Deletes a trial account (SEC-24): only a registered {@code TRIAL}, disabled for at least seven days, with an empty
     * {@code employeeId}. Too early is a deferral, not a failure. Member accounts are never deleted.
     */
    private Result delete(Task task) {
        Optional<Account> found = registry.find(task.subject());

        if (found.isPresent() && found.get().deleted()) {
            return Result.replay(found.get().id());
        }

        Account account = found.orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_REGISTERED));

        if (account.kind() != AccountKind.TRIAL) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        }

        if (!"DISABLED".equals(account.status())) {
            throw new ProvisioningException(ProvisioningErrorCode.PRECONDITION_FAILED);
        }

        Instant eligible = account.updatedAt().plus(TRIAL_DELETE_AFTER_DISABLE);

        if (clock.instant().isBefore(eligible)) {
            return Result.deferred(eligible, account.id());
        }

        GraphUser user;

        try {
            user = guard.verify(account);
        } catch (ProvisioningException e) {
            if (e.code() == ProvisioningErrorCode.GRAPH_NOT_FOUND) {
                registry.setStatus(account.id(), "DELETED");
                return Result.replay(account.id());
            }

            throw e;
        }

        if (!Boolean.FALSE.equals(user.accountEnabled())) {
            throw new ProvisioningException(ProvisioningErrorCode.PRECONDITION_FAILED);
        }

        graph.deleteUser(account.objectId());
        registry.setStatus(account.id(), "DELETED");
        return Result.done(account.id());
    }

    // ------------------------------------------------------------------------------------------------- groups

    private Result addToGroup(Task task) {
        GroupMappingSource.GroupMapping mapping = mapping(task);
        Account account = registered(task.subject());

        if (account.kind() != AccountKind.MEMBER) {
            // trial accounts never join groups (SEC-20d)
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        }

        if ("DISABLED".equals(account.status())) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_DISABLED);
        }

        if (!mapping.active()) {
            throw new ProvisioningException(ProvisioningErrorCode.PRECONDITION_FAILED);
        }

        guard.verify(account);
        requireManageable(mapping.groupObjectId());

        return graph.addGroupMember(mapping.groupObjectId(), account.objectId())
            ? Result.done(account.id())
            : Result.replay(account.id());
    }

    private Result removeFromGroup(Task task) {
        GroupMappingSource.GroupMapping mapping = mapping(task);
        Optional<Account> found = registry.find(task.subject());

        if (found.isEmpty() || found.get().deleted()) {
            // an account the Panel does not manage is never removed from anything (SEC-43)
            return Result.replay(found.map(Account::id).orElse(null));
        }

        Account account = found.get();

        if (account.kind() != AccountKind.MEMBER) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        }

        guard.verify(account);

        GroupCheck check = graph.checkGroup(mapping.groupObjectId());

        if (check == GroupCheck.NOT_FOUND) {
            return Result.replay(account.id());
        }

        if (check != GroupCheck.MANAGEABLE) {
            throw new ProvisioningException(ProvisioningErrorCode.GROUP_NOT_MANAGEABLE);
        }

        if (graph.groupOwnerIds(mapping.groupObjectId()).contains(account.objectId().toLowerCase())) {
            // owners of a group are never taken out by the Panel
            throw new ProvisioningException(ProvisioningErrorCode.PRECONDITION_FAILED);
        }

        return graph.removeGroupMember(mapping.groupObjectId(), account.objectId())
            ? Result.done(account.id())
            : Result.replay(account.id());
    }

    private void requireManageable(String groupObjectId) {
        if (graph.checkGroup(groupObjectId) != GroupCheck.MANAGEABLE) {
            throw new ProvisioningException(ProvisioningErrorCode.GROUP_NOT_MANAGEABLE);
        }
    }

    private GroupMappingSource.GroupMapping mapping(Task task) {
        GroupMappingSource source = mappings.getIfAvailable();
        Long mappingId = task.mappingId();

        if (source == null || mappingId == null || !(task.subject() instanceof AccountSubject.Member)) {
            throw new ProvisioningException(ProvisioningErrorCode.GROUP_MAPPING_NOT_FOUND);
        }

        GroupMappingSource.GroupMapping mapping = readOnly.execute(status -> source.mapping(mappingId))
            .orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.GROUP_MAPPING_NOT_FOUND));

        if (!GraphDirectoryClient.PrivateUuid.valid(mapping.groupObjectId())) {
            throw new ProvisioningException(ProvisioningErrorCode.GROUP_MAPPING_NOT_FOUND);
        }

        return new GroupMappingSource.GroupMapping(mapping.groupObjectId().toLowerCase(), mapping.active());
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private Account registered(AccountSubject subject) {
        Account account = registry.find(subject).orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_REGISTERED));

        if (account.deleted()) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_REGISTERED);
        }

        return account;
    }

    private Account register(AccountSubject subject, AccountKind kind, AccountOrigin origin, String number, String objectId) {
        try {
            return registry.insert(subject, kind, origin, number, objectId);
        } catch (DataIntegrityViolationException e) {
            // another worker registered the same account, or the object is registered for somebody else
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_ALREADY_REGISTERED);
        }
    }

    /**
     * The profile as the source has it now, checked for what Graph needs: a six-digit number in the band of the kind,
     * names, and for members an {@code employeeId} equal to the number (never one for a trial).
     */
    private AccountProfile profile(AccountSubject subject, AccountKind kind) {
        AccountProfileSource source = profileSources.stream().filter(s -> s.supports(subject.getClass())).findFirst()
            .orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.SUBJECT_NOT_FOUND));
        AccountProfile profile = readOnly.execute(status -> source.profile(subject))
            .orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.SUBJECT_NOT_FOUND));

        if (blank(profile.givenName()) || blank(profile.surname()) || blank(profile.displayName())) {
            throw new ProvisioningException(ProvisioningErrorCode.PROFILE_INCOMPLETE);
        }

        if (!guard.inBand(kind, profile.accountNumber())) {
            throw new ProvisioningException(ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        }

        if (kind == AccountKind.MEMBER && !profile.accountNumber().equals(profile.employeeId())
            || kind == AccountKind.TRIAL && profile.employeeId() != null) {
            throw new ProvisioningException(ProvisioningErrorCode.PROFILE_INCOMPLETE);
        }

        return profile;
    }

    /**
     * Whether an object found by the user principal name is the one an earlier attempt of this task created (D3): a member
     * user created in the few seconds around the task, whose {@code employeeId} is exactly what the POST sent (the number
     * for a member, none for a trial). An object made by hand just before the task does not qualify.
     */
    private boolean createdForThisTask(GraphUser user, Task task, AccountKind kind, String number) {
        boolean employeeOk = kind == AccountKind.TRIAL ? user.employeeId() == null : number.equals(user.employeeId());

        return employeeOk && "member".equalsIgnoreCase(user.userType()) && user.createdAt() != null
            && !user.createdAt().isBefore(task.createdAt().minusSeconds(5))
            && !properties.protectedObjectIds().contains(user.id().toLowerCase());
    }

    /**
     * D9: the {@code mail} property is written only when it is an address of the Panel's own domain; anything else (a
     * mistyped or foreign address in the member data) is left out.
     */
    private @Nullable String domainMail(@Nullable String mail) {
        return mail != null && mail.toLowerCase().endsWith("@" + properties.domain()) ? mail : null;
    }

    /**
     * Gives the account the licence of its kind unless it has it; false when nothing was written.
     */
    private boolean ensureLicense(String objectId, AccountKind kind, @Nullable GraphUser known) {
        String sku = sku(kind == AccountKind.TRIAL ? properties.trialLicenseSku() : properties.memberLicenseSku());

        if (sku == null) {
            return false;
        }

        GraphUser user = known != null ? known
            : graph.user(objectId).orElseThrow(() -> new ProvisioningException(ProvisioningErrorCode.GRAPH_NOT_FOUND));

        if (user.licenseSkus().contains(sku)) {
            return false;
        }

        graph.assignLicense(objectId, sku, List.of());
        return true;
    }

    private static @Nullable String sku(String configured) {
        return configured.isBlank() ? null : configured.strip().toLowerCase();
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
