package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The Panel's view of one subject's account, read from the registry and the outbox only (no Graph call). Group
 * operations do not affect it.
 *
 * <p>Precedence: an open account task → {@link AccountStatus#PENDING}; otherwise the latest finished account task
 * failed → {@link AccountStatus#FAILED}; otherwise the registered account → {@link AccountStatus#ACTIVE} /
 * {@link AccountStatus#DISABLED}; otherwise {@link AccountStatus#NONE}.
 *
 * @param status           the status
 * @param accountId        the registry id of the account (what activation links bind to), when registered
 * @param kind             TRIAL or MEMBER, when registered
 * @param origin           CREATED by the Panel or ADOPTED, when registered
 * @param accountNumber    the six digits of the user principal name ({@code 000205}, {@code 990001}), when registered
 * @param pendingOperation the open account operation, when {@link AccountStatus#PENDING}
 * @param lastError        the latest error or hold reason of an account task, when there is one
 * @param updatedAt        when this state last changed, when known
 */
public record AccountState(
    AccountStatus status,
    @Nullable Long accountId,
    @Nullable AccountKind kind,
    @Nullable AccountOrigin origin,
    @Nullable String accountNumber,
    @Nullable ProvisioningOperation pendingOperation,
    @Nullable ProvisioningErrorCode lastError,
    @Nullable Instant updatedAt
) {
    public static AccountState none() {
        return new AccountState(AccountStatus.NONE, null, null, null, null, null, null, null);
    }

    public enum AccountKind {
        TRIAL,
        MEMBER
    }

    public enum AccountOrigin {
        CREATED,
        ADOPTED
    }
}
