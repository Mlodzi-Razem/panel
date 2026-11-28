package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import java.time.Instant;
import java.util.Arrays;

/**
 * A freshly issued Temporary Access Pass, held in memory only. It is deliberately not a record: it renders as
 * {@code TemporaryAccessPass[redacted]}, has identity equality and no accessor a JSON serialiser would pick up, so it
 * cannot end up in logs, JSON, audit payloads or exception messages by accident. Read the value with {@link #reveal()}
 * to put it into the activation page's response, then {@link #destroy()} it (or use try-with-resources).
 */
public final class TemporaryAccessPass implements AutoCloseable {
    private final char[] value;
    private final Instant expiresAt;
    private final boolean usableOnce;
    private boolean destroyed;

    public TemporaryAccessPass(char[] value, Instant expiresAt, boolean usableOnce) {
        this.value = value.clone();
        this.expiresAt = expiresAt;
        this.usableOnce = usableOnce;
    }

    /**
     * The pass itself.
     *
     * @throws IllegalStateException after {@link #destroy()}
     */
    public String reveal() {
        if (destroyed) {
            throw new IllegalStateException("The Temporary Access Pass was destroyed");
        }
        return new String(value);
    }

    /**
     * When the pass stops working.
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * Whether the pass works for one sign-in only.
     */
    public boolean usableOnce() {
        return usableOnce;
    }

    /**
     * Overwrites the value held by this object.
     */
    public void destroy() {
        Arrays.fill(value, '\0');
        destroyed = true;
    }

    @Override
    public void close() {
        destroy();
    }

    @Override
    public String toString() {
        return "TemporaryAccessPass[redacted]";
    }
}
