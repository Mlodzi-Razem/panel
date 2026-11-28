package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import java.util.List;
import java.util.Optional;

/**
 * Provisioning of Microsoft Entra ID accounts and group memberships for members and candidates (trial accounts),
 * owned by package B2. Writes go through a transactional outbox: {@link #enqueue} only records a task in the caller's
 * transaction, and a background worker calls Microsoft Graph after that transaction has committed, so no business
 * operation ever waits for Graph.
 *
 * <h2>Switches</h2>
 * <ul>
 *     <li>{@code panel.directory.provisioning.enabled=false} (default): tasks are still recorded, but the worker does
 *     not start and nothing reaches Microsoft Graph — not even the token endpoint. Synchronous Graph methods throw
 *     {@link ProvisioningException} with {@link ProvisioningErrorCode#PROVISIONING_DISABLED};
 *     {@link #addressAvailability} answers {@link AddressAvailability#UNKNOWN}. After switching on, tasks older than
 *     {@code max-job-age} go to {@link ProvisioningTaskState#HELD} instead of running.</li>
 *     <li>{@code panel.directory.provisioning.destructive-enabled=false} (default): disable, delete and group-removal
 *     tasks wait in {@link ProvisioningTaskState#HELD}. With it on, at most {@code max-destructive-per-hour} of them run
 *     per hour across all replicas; the rest are held.</li>
 * </ul>
 *
 * <h2>Managed accounts</h2>
 * Only accounts in the Panel's registry are ever written: created by the Panel or explicitly adopted
 * ({@link ProvisioningRequest.AdoptMemberAccount}). Before every write the worker reads the account by its registered
 * object id and checks user type, user principal name {@code NNNNNN@<domain>}, the number band of the account kind,
 * the {@code employeeId} and the list of protected object ids. Any mismatch fails the task with
 * {@link ProvisioningErrorCode#ACCOUNT_NOT_MANAGED}, without retries.
 *
 * <h2>Personal data</h2>
 * The outbox stores references only (subject id, group mapping id, occurrence id, operation, state, error code).
 * Names, addresses and districts are read through {@link AccountProfileSource} when a task executes and are never
 * persisted by this module. A Temporary Access Pass is never persisted, logged, audited or put into an exception.
 */
public interface MemberAccountProvisioning {

    /**
     * Whether the worker may call Microsoft Graph: provisioning switched on, Graph configured and the application token
     * not carrying a forbidden tenant-wide permission.
     */
    boolean enabled();

    /**
     * Records a task in the caller's transaction (transactional outbox, also while provisioning is switched off). The
     * worker sees it only after that transaction commits; a rollback removes it with the business change. Without an
     * active transaction the task is committed on its own. Never calls Graph.
     *
     * <ul>
     *     <li>Idempotency: the key is {@code operation:subjectType:subjectId[:mappingId][:occurrenceId]}. Enqueueing a
     *     key that exists returns {@link ProvisioningTicket.Dispatch#DEDUPLICATED} with that task, whatever its state.
     *     A failed or held task is resumed with {@link #retry} or {@link #release}, not by enqueueing again.</li>
     *     <li>Supersession: an older opposite task for the same subject (and mapping) that has not started yet —
     *     {@code ENABLE ↔ DISABLE}, {@code ADD ↔ REMOVE} — becomes {@link ProvisioningTaskState#SUPERSEDED}, so only
     *     the latest intent runs.</li>
     *     <li>Order: tasks of one subject run strictly in the order they were recorded.</li>
     *     <li>{@link ProvisioningRequest.AdoptMemberAccount} and {@link ProvisioningRequest.EnableAccount} need the
     *     current user to hold {@code DIRECTORY_USERS_MANAGE} (GLOBAL) and not to be the subject; otherwise
     *     {@link ProvisioningException} with {@link ProvisioningErrorCode#ACTION_NOT_PERMITTED}. The other requests are
     *     system follow-ups of business changes the caller has already authorised.</li>
     * </ul>
     *
     * @throws IllegalArgumentException for a malformed request
     */
    ProvisioningTicket enqueue(ProvisioningRequest request);

    /**
     * State of one task, for reports and the back office.
     */
    Optional<ProvisioningTaskView> task(long taskId);

    /**
     * Puts a {@link ProvisioningTaskState#FAILED} task back to {@link ProvisioningTaskState#PENDING} with zero
     * attempts; operation and subject stay. Requires {@code DIRECTORY_USERS_MANAGE} for account tasks and
     * {@code DIRECTORY_GROUPS_MANAGE} for group tasks, never on one's own account; audited.
     *
     * @throws ProvisioningException {@link ProvisioningErrorCode#ACTION_NOT_PERMITTED} or
     *                               {@link ProvisioningErrorCode#TASK_NOT_RETRYABLE} (not failed / not found)
     */
    ProvisioningTaskView retry(long taskId);

    /**
     * Releases a {@link ProvisioningTaskState#HELD} task to {@link ProvisioningTaskState#PENDING}; it then runs even
     * if it is old or destructive (the hourly limit still counts it). Same permissions as {@link #retry}; audited.
     *
     * @throws ProvisioningException {@link ProvisioningErrorCode#ACTION_NOT_PERMITTED} or
     *                               {@link ProvisioningErrorCode#TASK_NOT_RETRYABLE} (not held / not found)
     */
    ProvisioningTaskView release(long taskId);

    /**
     * The account of a member or a candidate as the Panel's registry and outbox know it, without calling Graph. Never
     * null: {@link AccountStatus#NONE} when there is no account and nothing open.
     */
    AccountState accountState(AccountSubject subject);

    /**
     * Synchronously issues a one-time Temporary Access Pass for a registered account and returns it in memory only.
     * Call it only from the POST handler of the activation page, once per successful claim of an activation link.
     * Creating a pass replaces any pass the account still has. The value is never persisted, logged, audited or put
     * into an exception (or its cause).
     *
     * @param accountId the registry id ({@link AccountState#accountId()}), never an Entra object id or a UPN
     * @throws ProvisioningException {@link ProvisioningErrorCode#PROVISIONING_DISABLED},
     *                               {@link ProvisioningErrorCode#ACCOUNT_NOT_REGISTERED},
     *                               {@link ProvisioningErrorCode#ACCOUNT_DISABLED},
     *                               {@link ProvisioningErrorCode#ACCOUNT_NOT_MANAGED}, or — transient, "try again in a
     *                               few minutes" — {@link ProvisioningErrorCode#GRAPH_THROTTLED} (with
     *                               {@link ProvisioningException#retryAfter()}), {@link ProvisioningErrorCode#GRAPH_UNAVAILABLE},
     *                               {@link ProvisioningErrorCode#GRAPH_FORBIDDEN} (administrative-unit delay after
     *                               creation); permanent otherwise ({@link ProvisioningErrorCode#GRAPH_REJECTED})
     */
    TemporaryAccessPass issueTemporaryAccessPass(long accountId);

    /**
     * Synchronously checks whether a group may be mapped to a body: it exists, is not role-assignable, has no dynamic
     * membership rule and is a member of the configured group administrative unit. Does not throw for the group's
     * properties; those are the answer.
     *
     * @param groupObjectId the Entra object id of the group (UUID)
     * @throws ProvisioningException {@link ProvisioningErrorCode#PROVISIONING_DISABLED} or a transient {@code GRAPH_*}
     *                               code
     * @throws IllegalArgumentException when the id is not a UUID
     */
    GroupCheck checkGroup(String groupObjectId);

    /**
     * Synchronously reads the direct user members and the owners of a group, following {@code @odata.nextLink} and
     * honouring {@code Retry-After}, and matches them against the registry. For reconciliation dry runs. The group
     * must pass {@link #checkGroup}; Graph is asked for object ids only.
     *
     * @throws ProvisioningException {@link ProvisioningErrorCode#PROVISIONING_DISABLED},
     *                               {@link ProvisioningErrorCode#GROUP_NOT_MANAGEABLE} or a {@code GRAPH_*} code
     */
    List<DirectoryGroupMember> groupMembers(String groupObjectId);

    /**
     * Synchronously checks whether an e-mail address is already used in the directory as {@code mail},
     * {@code userPrincipalName} or one of the {@code proxyAddresses} of a user or a group (point queries with
     * {@code $top=1}, {@code $select=id}). For choosing a free {@code imie.nazwisko@} address before enqueueing an
     * account task. The address is sent to Graph only, never logged. Does not throw: switched off, unconfigured or Graph
     * errors answer {@link AddressAvailability#UNKNOWN}.
     */
    AddressAvailability addressAvailability(String address);
}
