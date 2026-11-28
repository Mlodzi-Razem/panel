package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import org.jspecify.annotations.Nullable;

/**
 * Published when a task reaches {@link ProvisioningTaskState#DONE} or {@link ProvisioningTaskState#FAILED}, inside the
 * worker transaction that records it; listen with {@code @TransactionalEventListener} (after commit) or
 * {@code @ApplicationModuleListener}. B3 uses it to send the activation e-mail once a trial or member account exists.
 * Contains no personal data and no Entra object ids.
 *
 * <p>The subject is carried as two plain fields ({@code subjectKind}, {@code subjectId}) rather than as the sealed
 * {@link AccountSubject}: the event is persisted as JSON by Spring Modulith (event publication registry) and must be
 * deserialised after a restart, which a sealed interface without type information cannot do. Use {@link #subject()}
 * to get the {@link AccountSubject} back.</p>
 *
 * @param taskId       the task
 * @param operation    what it did
 * @param subjectKind  whose account: a member or a candidate
 * @param subjectId    the member id or the case id, according to {@code subjectKind}
 * @param succeeded    DONE (true) or FAILED (false)
 * @param errorCode    why it failed, otherwise null
 * @param accountId    the registry id of the account involved, when there is one
 * @param mappingId    the group mapping of a group operation, otherwise null
 * @param occurrenceId the business fact behind a repeatable operation, otherwise null
 */
public record AccountProvisioningFinished(
    long taskId,
    ProvisioningOperation operation,
    SubjectKind subjectKind,
    long subjectId,
    boolean succeeded,
    @Nullable ProvisioningErrorCode errorCode,
    @Nullable Long accountId,
    @Nullable Long mappingId,
    @Nullable Long occurrenceId
) {
    /** Whose account this is, as the sealed type. Not a bean property, so it is not serialised. */
    public AccountSubject subject() {
        return subjectKind.subject(subjectId);
    }
}
