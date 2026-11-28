package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import java.util.Optional;

/**
 * Supplies {@link AccountProfile}s to the provisioning worker at execution time, so the outbox never stores personal
 * data. B2 implements it for {@link AccountSubject.Member} (members and their district affiliation); the recruitment
 * module (B3) implements it for {@link AccountSubject.Candidate} (trial number, preferred names, district of the case).
 * Exactly one bean must support each subject kind.
 */
public interface AccountProfileSource {

    /**
     * Whether this source answers for subjects of the given kind.
     */
    boolean supports(Class<? extends AccountSubject> subjectKind);

    /**
     * The profile as it is right now; empty when the subject does not exist (any more) or has no data to provision
     * (for example an anonymised case). Called inside the worker's read-only transaction, never inside a Graph call.
     */
    Optional<AccountProfile> profile(AccountSubject subject);
}
