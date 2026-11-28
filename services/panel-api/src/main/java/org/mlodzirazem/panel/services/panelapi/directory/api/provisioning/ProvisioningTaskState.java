package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

/**
 * Lifecycle of an outbox task; the names are stored in the database.
 */
public enum ProvisioningTaskState {
    /** Waiting for its first or next attempt. */
    PENDING,
    /** Leased by a worker right now. */
    RUNNING,
    /** Waiting for a person: destructive operations switched off, hourly limit reached, or older than max-job-age when
     * provisioning was switched on; {@code errorCode} says which. Released with {@link MemberAccountProvisioning#release}. */
    HELD,
    /** Reached its target state in Entra. */
    DONE,
    /** Gave up (permanent error or attempt limit); {@code errorCode} says why. Resumed with {@link MemberAccountProvisioning#retry}. */
    FAILED,
    /** Cancelled before it started because a newer opposite task replaced it. */
    SUPERSEDED;

    /**
     * Not finished yet: pending, running or held.
     */
    public boolean open() {
        return this == PENDING || this == RUNNING || this == HELD;
    }
}
