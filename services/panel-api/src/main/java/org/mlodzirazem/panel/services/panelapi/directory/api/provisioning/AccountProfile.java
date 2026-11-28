package org.mlodzirazem.panel.services.panelapi.directory.api.provisioning;

import org.jspecify.annotations.Nullable;

/**
 * What the worker writes to Entra for one subject, read at execution time and kept in memory only.
 *
 * @param accountNumber the six digits of the user principal name: the member number ({@code %06d}) or the trial
 *                      number ({@code 990000–999999})
 * @param employeeId    the member number for members; null for trial accounts
 * @param givenName     preferred first name
 * @param surname       preferred last name
 * @param displayName   preferred full name
 * @param department    the district's name, when the subject has one
 * @param mail          the public address ({@code imie.nazwisko@…}) for members, when known; null for trials
 */
public record AccountProfile(
    String accountNumber,
    @Nullable String employeeId,
    String givenName,
    String surname,
    String displayName,
    @Nullable String department,
    @Nullable String mail
) {
    /**
     * Names and addresses never appear rendered.
     */
    @Override
    public String toString() {
        return "AccountProfile[accountNumber=" + accountNumber + ']';
    }
}
