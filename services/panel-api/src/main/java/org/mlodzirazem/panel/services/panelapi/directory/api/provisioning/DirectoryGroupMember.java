package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import org.jspecify.annotations.Nullable;

/**
 * A direct user member or owner of a group, matched against the registry. Graph is asked for object ids only. The
 * object id must not go into reports or audit; use {@code memberId} there.
 *
 * @param objectId  the user's Entra object id, lower-case
 * @param accountId the registry id when the user is a registered account, otherwise null
 * @param memberId  the member ({@code members.id}) of a registered MEMBER account, otherwise null
 * @param member    whether the user is a (direct) member of the group
 * @param owner     whether the user is an owner of the group; owners are never removed by the Panel
 */
public record DirectoryGroupMember(
    String objectId,
    @Nullable Long accountId,
    @Nullable Long memberId,
    boolean member,
    boolean owner
) {
}
