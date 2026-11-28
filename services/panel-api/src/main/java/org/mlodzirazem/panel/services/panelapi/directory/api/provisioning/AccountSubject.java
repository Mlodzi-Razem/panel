package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

/**
 * Whose account: a member ({@code members.id}, not the member number) or a candidate ({@code recruitment_cases.id},
 * whose trial account it is). Stored as {@code subject_type} + {@code subject_id}.
 */
public sealed interface AccountSubject {

    long id();

    record Member(long id) implements AccountSubject {
    }

    record Candidate(long id) implements AccountSubject {
    }
}
