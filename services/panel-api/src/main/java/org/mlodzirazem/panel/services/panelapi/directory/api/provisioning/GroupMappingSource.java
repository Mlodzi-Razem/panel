package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import java.util.Optional;

/**
 * Resolves a body-to-group mapping id (owned by B4, append-only history) to its Entra group at execution time, so the
 * outbox stores the bigint mapping id rather than a group object id (SEC-30). Implemented by B4; exactly one bean.
 */
public interface GroupMappingSource {

    /**
     * The mapping row as it is now, including closed rows (removals still use them); empty when the id is unknown.
     */
    Optional<GroupMapping> mapping(long mappingId);

    /**
     * @param groupObjectId the Entra object id of the group (UUID, lower-case)
     * @param active        whether the mapping is currently valid; additions require an active mapping
     */
    record GroupMapping(String groupObjectId, boolean active) {
    }
}
