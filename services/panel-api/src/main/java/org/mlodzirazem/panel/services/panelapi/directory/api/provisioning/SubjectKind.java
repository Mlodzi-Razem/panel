package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

/**
 * The kind of {@link AccountSubject} in a flat form that serialises without type information.
 */
public enum SubjectKind {
    MEMBER,
    CANDIDATE;

    public static SubjectKind of(AccountSubject subject) {
        return subject instanceof AccountSubject.Member ? MEMBER : CANDIDATE;
    }

    public AccountSubject subject(long id) {
        return this == MEMBER ? new AccountSubject.Member(id) : new AccountSubject.Candidate(id);
    }
}
