package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One intent for the provisioning worker. Carries references only (bigint ids); the worker reads names and other data
 * at execution time through {@link AccountProfileSource} and resolves groups through {@link GroupMappingSource}.
 *
 * <p>{@code occurrenceId} on repeatable operations is the id of the business fact that caused the task (for example the
 * body membership id for group operations, the recruitment case event id or the member status change id for disable /
 * enable). It makes the idempotency key unique per fact, so the same operation can legitimately happen again later
 * (re-granted mandate, account disabled a second time) while a duplicate delivery of the same fact stays a no-op.
 */
public sealed interface ProvisioningRequest {

    ProvisioningOperation operation();

    AccountSubject subject();

    /**
     * The body-to-group mapping (owned by B4) for group operations; null otherwise.
     */
    default @Nullable Long mappingIdOrNull() {
        return null;
    }

    /**
     * The business fact behind a repeatable operation; null for one-shot operations.
     */
    default @Nullable Long occurrenceIdOrNull() {
        return null;
    }

    /**
     * Creates the trial account {@code 99NNNN@<domain>} of a candidate whose declaration arrived (D4): no
     * {@code employeeId}, trial licence. The trial number comes from the case through {@link AccountProfileSource} and
     * must be allocated before enqueueing.
     */
    record CreateTrialAccount(long caseId) implements ProvisioningRequest {
        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.CREATE_TRIAL_ACCOUNT;
        }

        @Override
        public AccountSubject subject() {
            return new AccountSubject.Candidate(caseId);
        }
    }

    /**
     * Turns the case's trial account into the member's account (same Entra object) once the case is ACCEPTED with this
     * member: uniqueness check of the member number in Entra, then user principal name {@code %06d@<domain>}, names,
     * department, member licence, and {@code employeeId} last. The registry entry becomes a MEMBER account of the
     * member. Without a registered trial account the member account is created instead.
     */
    record ConvertTrialToMember(long caseId, long memberId) implements ProvisioningRequest {
        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.CONVERT_TRIAL_TO_MEMBER;
        }

        @Override
        public AccountSubject subject() {
            return new AccountSubject.Member(memberId);
        }
    }

    /**
     * Creates the member account {@code %06d@<domain>} with {@code employeeId} = member number. Fails with
     * {@link ProvisioningErrorCode#MEMBER_NUMBER_TAKEN_IN_ENTRA} when any object already has that user principal name
     * or {@code employeeId}: existing accounts are taken over only through {@link AdoptMemberAccount}.
     */
    record CreateMemberAccount(long memberId) implements ProvisioningRequest {
        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.CREATE_MEMBER_ACCOUNT;
        }

        @Override
        public AccountSubject subject() {
            return new AccountSubject.Member(memberId);
        }
    }

    /**
     * Registers an existing account (created before the Panel) as the member's MEMBER account, origin ADOPTED: exactly
     * one object with user principal name {@code %06d@<domain>}, {@code employeeId} equal to the member number, user
     * type Member, not protected. Requires {@code DIRECTORY_USERS_MANAGE}; audited as {@code account-adopt}. Writes
     * nothing to Entra.
     */
    record AdoptMemberAccount(long memberId) implements ProvisioningRequest {
        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.ADOPT_MEMBER_ACCOUNT;
        }

        @Override
        public AccountSubject subject() {
            return new AccountSubject.Member(memberId);
        }
    }

    /**
     * Blocks sign-in ({@code accountEnabled=false}) and revokes sign-in sessions; reversible. Destructive. A subject with
     * no registered account is NOT skipped silently: the task ends FAILED with {@code ACCOUNT_NOT_REGISTERED} and no Graph
     * call is made (W6). The group removal, by contrast, treats an unregistered account as nothing to remove (SEC-43).
     */
    record DisableAccount(AccountSubject subject, long occurrenceId) implements ProvisioningRequest {
        public DisableAccount {
            Objects.requireNonNull(subject);
        }

        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.DISABLE_ACCOUNT;
        }

        @Override
        public Long occurrenceIdOrNull() {
            return occurrenceId;
        }
    }

    /**
     * Allows sign-in again. Requires {@code DIRECTORY_USERS_MANAGE}.
     */
    record EnableAccount(AccountSubject subject, long occurrenceId) implements ProvisioningRequest {
        public EnableAccount {
            Objects.requireNonNull(subject);
        }

        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.ENABLE_ACCOUNT;
        }

        @Override
        public Long occurrenceIdOrNull() {
            return occurrenceId;
        }
    }

    /**
     * Deletes a candidate's trial account (Entra soft delete, restorable for 30 days). Destructive. Runs only for a
     * registered TRIAL account in the trial band with empty {@code employeeId}, disabled for at least seven days, of a
     * REJECTED case; enqueue it from the anonymisation event. Member accounts are never deleted by the Panel.
     */
    record DeleteTrialAccount(long caseId) implements ProvisioningRequest {
        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.DELETE_ACCOUNT;
        }

        @Override
        public AccountSubject subject() {
            return new AccountSubject.Candidate(caseId);
        }
    }

    /**
     * Adds the member's registered MEMBER account to the mapped group ({@code POST /groups/{g}/members/$ref}); already a
     * member counts as success. The mapping must be active.
     */
    record AddGroupMember(long memberId, long mappingId, long occurrenceId) implements ProvisioningRequest {
        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.ADD_GROUP_MEMBER;
        }

        @Override
        public AccountSubject subject() {
            return new AccountSubject.Member(memberId);
        }

        @Override
        public Long mappingIdOrNull() {
            return mappingId;
        }

        @Override
        public Long occurrenceIdOrNull() {
            return occurrenceId;
        }
    }

    /**
     * Removes the member's registered MEMBER account from the mapped group, only through
     * {@code DELETE /groups/{g}/members/{u}/$ref}; never removes group owners; absent member counts as success. Works on
     * closed mappings too. Destructive.
     */
    record RemoveGroupMember(long memberId, long mappingId, long occurrenceId) implements ProvisioningRequest {
        @Override
        public ProvisioningOperation operation() {
            return ProvisioningOperation.REMOVE_GROUP_MEMBER;
        }

        @Override
        public AccountSubject subject() {
            return new AccountSubject.Member(memberId);
        }

        @Override
        public Long mappingIdOrNull() {
            return mappingId;
        }

        @Override
        public Long occurrenceIdOrNull() {
            return occurrenceId;
        }
    }
}
