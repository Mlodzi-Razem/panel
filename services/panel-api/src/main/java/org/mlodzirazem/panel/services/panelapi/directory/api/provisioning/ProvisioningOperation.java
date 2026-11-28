package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import org.jspecify.annotations.Nullable;

/**
 * Operations of the provisioning outbox; the names are stored in the database and must not be renamed.
 */
public enum ProvisioningOperation {
    CREATE_TRIAL_ACCOUNT(false),
    CONVERT_TRIAL_TO_MEMBER(false),
    CREATE_MEMBER_ACCOUNT(false),
    ADOPT_MEMBER_ACCOUNT(false),
    DISABLE_ACCOUNT(true),
    ENABLE_ACCOUNT(false),
    DELETE_ACCOUNT(true),
    ADD_GROUP_MEMBER(false),
    REMOVE_GROUP_MEMBER(true);

    private final boolean destructive;

    ProvisioningOperation(boolean destructive) {
        this.destructive = destructive;
    }

    /**
     * Runs only with {@code destructive-enabled} and within the hourly limit; held otherwise.
     */
    public boolean destructive() {
        return destructive;
    }

    /**
     * Operations on group membership rather than on the account itself; they do not change {@link AccountState}.
     */
    public boolean groupOperation() {
        return this == ADD_GROUP_MEMBER || this == REMOVE_GROUP_MEMBER;
    }

    /**
     * The operation that undoes this one for the same subject (and mapping); a newer task supersedes a pending one.
     */
    public @Nullable ProvisioningOperation opposite() {
        return switch (this) {
            case DISABLE_ACCOUNT -> ENABLE_ACCOUNT;
            case ENABLE_ACCOUNT -> DISABLE_ACCOUNT;
            case ADD_GROUP_MEMBER -> REMOVE_GROUP_MEMBER;
            case REMOVE_GROUP_MEMBER -> ADD_GROUP_MEMBER;
            default -> null;
        };
    }
}
