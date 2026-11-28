package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

/**
 * Answer of {@link MemberAccountProvisioning#checkGroup} (SEC-6). Only {@link #MANAGEABLE} may be mapped.
 */
public enum GroupCheck {
    /** Exists, not role-assignable, not dynamic, in the group administrative unit. */
    MANAGEABLE,
    /** Graph answered 404. */
    NOT_FOUND,
    /** {@code isAssignableToRole=true}. */
    ROLE_ASSIGNABLE,
    /** Has a {@code membershipRule} (dynamic membership). */
    DYNAMIC,
    /** Not a member of the configured group administrative unit (or none is configured). */
    OUTSIDE_ADMINISTRATIVE_UNIT
}
