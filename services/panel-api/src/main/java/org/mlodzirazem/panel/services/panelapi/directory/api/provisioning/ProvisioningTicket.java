package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

/**
 * What {@link MemberAccountProvisioning#enqueue} did.
 *
 * @param dispatch the outcome
 * @param taskId   the new task or the existing one with the same key
 * @param state    that task's state right now
 */
public record ProvisioningTicket(Dispatch dispatch, long taskId, ProvisioningTaskState state) {

    public enum Dispatch {
        /** A new task was recorded; the worker sees it after commit. */
        ENQUEUED,
        /**
         * A task with the same idempotency key already exists; nothing new was recorded. The existing task may be in any
         * state, FAILED or SUPERSEDED too (D13): the key is unique for good, so a repeated intent needs a new occurrence id.
         */
        DEDUPLICATED
    }
}
