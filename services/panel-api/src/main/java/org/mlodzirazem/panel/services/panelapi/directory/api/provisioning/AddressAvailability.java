package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

/**
 * Answer of {@link MemberAccountProvisioning#addressAvailability}.
 */
public enum AddressAvailability {
    /** No user or group in the directory uses the address. */
    FREE,
    /** The address is a mail, user principal name or proxy address of some user or group. */
    TAKEN,
    /** Provisioning is off, Graph is not configured or the lookup failed; treat as not verified. */
    UNKNOWN
}
