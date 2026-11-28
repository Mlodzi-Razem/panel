package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

/**
 * Why a task failed or is held, or why a synchronous call was refused. Stored in the database, written to audit as the
 * outcome, shown in the back office and sent to the front end; the names are stable. Never carries Graph response
 * bodies.
 */
public enum ProvisioningErrorCode {
    // --- switches and permissions
    /** Provisioning switched off or Graph not configured. */
    PROVISIONING_DISABLED(false),
    /** The application token carries a forbidden tenant-wide permission (SEC-1/SEC-2); the whole worker stops. */
    GRAPH_PERMISSIONS_TOO_BROAD(false),
    /** The current user lacks the permission, or acts on their own account. */
    ACTION_NOT_PERMITTED(false),
    /** {@code retry}/{@code release} on a task that is not failed/held or does not exist. */
    TASK_NOT_RETRYABLE(false),

    // --- hold reasons (state HELD)
    /** Destructive operations are switched off. */
    HOLD_DESTRUCTIVE_DISABLED(false),
    /** The hourly limit of destructive operations is used up. */
    HOLD_DESTRUCTIVE_LIMIT(false),
    /** Older than max-job-age when provisioning was switched on. */
    HOLD_STALE(false),

    // --- data and preconditions
    /** The member or case does not exist (any more), or {@link AccountProfileSource} has no profile for it. */
    SUBJECT_NOT_FOUND(false),
    /** The profile lacks something the operation needs (no trial number, no member number, no names). */
    PROFILE_INCOMPLETE(false),
    /** The business state does not allow the operation (case not ACCEPTED for this member, trial not disabled long enough, ...). */
    PRECONDITION_FAILED(false),
    /** The group mapping does not exist, or is closed for an add. */
    GROUP_MAPPING_NOT_FOUND(false),

    // --- registry and guard
    /**
     * The subject has no registered account. Final for the task (D7, W6): a disable or enable of an account outside the
     * registry fails visibly; a new task is needed once the account is registered (ADOPT, CREATE).
     */
    ACCOUNT_NOT_REGISTERED(false),
    /** Create or adopt for a subject that already has a registered account. */
    ACCOUNT_ALREADY_REGISTERED(false),
    /** The guard refused the target account: user type, UPN pattern, number band, employeeId or protected id. */
    ACCOUNT_NOT_MANAGED(false),
    /** The account exists but sign-in is blocked (TAP refused). */
    ACCOUNT_DISABLED(false),
    /** Adoption found no object, or more than one, matching UPN and employeeId. */
    ADOPTION_NO_MATCH(false),
    /** The UPN to create or convert to is taken (Graph ObjectConflict, or a read found an unexpected object). */
    UPN_CONFLICT(false),
    /** Another object already has the member's UPN or employeeId; possible takeover attempt, alert. */
    MEMBER_NUMBER_TAKEN_IN_ENTRA(false),
    /** The group is missing, role-assignable, dynamic or outside the group administrative unit. */
    GROUP_NOT_MANAGEABLE(false),
    /** The configured licence has no free units or cannot be assigned. */
    LICENSE_UNAVAILABLE(false),

    // --- Graph
    /** 429; retried after {@code Retry-After}. */
    GRAPH_THROTTLED(true),
    /** 5xx, timeout or network error; retried with backoff. */
    GRAPH_UNAVAILABLE(true),
    /** 403; transient for 15 minutes after the account was created (administrative-unit rule delay), then permanent. */
    GRAPH_FORBIDDEN(true),
    /** 404 where the object had to exist. */
    GRAPH_NOT_FOUND(false),
    /** Any other 4xx. */
    GRAPH_REJECTED(false),
    /** The attempt limit was reached. */
    ATTEMPTS_EXHAUSTED(false),
    /** Unexpected error inside the Panel. */
    INTERNAL(false);

    private final boolean retryable;

    ProvisioningErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * Whether the worker retries a task that hit this error (until the attempt limit).
     */
    public boolean retryable() {
        return retryable;
    }
}
