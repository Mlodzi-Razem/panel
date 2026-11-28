package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * A refused synchronous provisioning call. The message is the code only and there is never a cause: Graph and
 * JSON-parser exceptions quote response bodies, which may hold a Temporary Access Pass or personal data.
 */
public class ProvisioningException extends RuntimeException {
    private final ProvisioningErrorCode code;
    private final @Nullable Duration retryAfter;

    public ProvisioningException(ProvisioningErrorCode code) {
        this(code, null);
    }

    public ProvisioningException(ProvisioningErrorCode code, @Nullable Duration retryAfter) {
        super(code.name(), null, false, false);
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public ProvisioningErrorCode code() {
        return code;
    }

    /**
     * How long Graph asked to wait, for {@link ProvisioningErrorCode#GRAPH_THROTTLED}; otherwise null.
     */
    public @Nullable Duration retryAfter() {
        return retryAfter;
    }

    /**
     * Whether trying again later may succeed (the activation page shows "try again in a few minutes").
     */
    public boolean transientFailure() {
        return code.retryable();
    }
}
