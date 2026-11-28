package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One outbox task as reports and the back office see it. Contains no personal data and no Entra object ids.
 *
 * @param id            task id
 * @param operation     what the task does
 * @param subject       whose account
 * @param mappingId     the group mapping of a group operation, otherwise null
 * @param occurrenceId  the business fact behind a repeatable operation, otherwise null
 * @param state         lifecycle state
 * @param attempts      attempts made so far
 * @param nextAttemptAt when a pending task is due
 * @param errorCode     the last error or hold reason (kept while a retryable error is retried), otherwise null
 * @param createdAt     when the task was recorded
 * @param finishedAt    when it reached DONE, FAILED or SUPERSEDED, otherwise null
 */
public record ProvisioningTaskView(
    long id,
    ProvisioningOperation operation,
    AccountSubject subject,
    @Nullable Long mappingId,
    @Nullable Long occurrenceId,
    ProvisioningTaskState state,
    int attempts,
    Instant nextAttemptAt,
    @Nullable ProvisioningErrorCode errorCode,
    Instant createdAt,
    @Nullable Instant finishedAt
) {
}
