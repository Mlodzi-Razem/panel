package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

/**
 * Account status shown next to a member or a recruitment case.
 */
public enum AccountStatus {
    /** No registered account and no open account task (also after a trial account was deleted or converted). */
    NONE,
    /** An account task for this subject is open (pending, running or held), also while provisioning is switched off. */
    PENDING,
    /** The registered account may sign in. */
    ACTIVE,
    /** The registered account exists and sign-in is blocked. */
    DISABLED,
    /** The latest account task failed; {@link AccountState#lastError()} says why. */
    FAILED
}
