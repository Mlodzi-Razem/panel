/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProfile;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProfileSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The profile of a member for the worker: member number, preferred names, domain address and the primary district,
 * read when a task executes. A member without a numeric member number has no profile (it cannot be provisioned).
 */
@Component
@RequiredArgsConstructor
class MemberAccountProfileSource implements AccountProfileSource {
    private final JdbcClient jdbc;

    @Override
    public boolean supports(Class<? extends AccountSubject> subjectKind) {
        return AccountSubject.Member.class.isAssignableFrom(subjectKind);
    }

    @Override
    public Optional<AccountProfile> profile(AccountSubject subject) {
        if (!(subject instanceof AccountSubject.Member member)) {
            return Optional.empty();
        }

        return jdbc.sql("SELECT m.member_id, m.preferred_name_first, m.preferred_name_last, m.domain_email, "
                + "(SELECT b.name FROM member_district_affiliations a JOIN bodies b ON b.id = a.district_body_id "
                + " WHERE a.member_id = m.id AND a.kind = 'PRIMARY' AND a.valid_to IS NULL LIMIT 1) AS district "
                + "FROM members m WHERE m.id = :id")
            .param("id", member.id())
            .query((rs, row) -> {
                String raw = rs.getString("member_id");

                if (raw == null || !raw.strip().matches("\\d{1,6}")) {
                    return Optional.<AccountProfile>empty();
                }

                String number = String.format("%06d", Integer.parseInt(raw.strip()));
                String first = rs.getString("preferred_name_first");
                String last = rs.getString("preferred_name_last");

                return Optional.of(new AccountProfile(number, number, first, last, first + " " + last, rs.getString("district"),
                    rs.getString("domain_email")));
            })
            .optional()
            .flatMap(profile -> profile);
    }
}
