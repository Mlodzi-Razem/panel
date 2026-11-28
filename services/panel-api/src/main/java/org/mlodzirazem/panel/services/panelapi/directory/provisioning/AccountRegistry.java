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
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountState.AccountKind;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountState.AccountOrigin;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The registry of the accounts the Panel manages ({@code directory_accounts}): created by the Panel or explicitly
 * adopted. Only these are ever written to in Entra, and the Entra object id is read from here, never from a request, a
 * user principal name or a search result (SEC-3, SEC-4).
 */
@Component
@RequiredArgsConstructor
class AccountRegistry {
    /**
     * @param memberId set for member accounts
     * @param caseId   set for trial accounts
     * @param number   the six digits of the user principal name
     * @param status   ACTIVE, DISABLED or DELETED
     */
    record Account(long id, @Nullable Long memberId, @Nullable Long caseId, AccountKind kind, AccountOrigin origin,
                   String number, String objectId, String status, Instant createdAt, Instant updatedAt) {
        boolean deleted() {
            return "DELETED".equals(status);
        }

        @Override
        public String toString() {
            return "Account[id=" + id + ']';
        }
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    Optional<Account> find(AccountSubject subject) {
        return switch (subject) {
            case AccountSubject.Member member -> query("member_id = :id", member.id());
            case AccountSubject.Candidate candidate -> query("case_id = :id", candidate.id());
        };
    }

    Optional<Account> byId(long id) {
        return query("id = :id", id);
    }

    Optional<Account> byObjectId(String objectId) {
        return jdbc.sql("SELECT * FROM directory_accounts WHERE entra_object_id = :id::uuid")
            .param("id", objectId).query(AccountRegistry::map).optional();
    }

    private Optional<Account> query(String where, long id) {
        return jdbc.sql("SELECT * FROM directory_accounts WHERE " + where).param("id", id).query(AccountRegistry::map).optional();
    }

    Account insert(AccountSubject subject, AccountKind kind, AccountOrigin origin, String number, String objectId) {
        Instant now = clock.instant();
        Long memberId = subject instanceof AccountSubject.Member m ? m.id() : null;
        Long caseId = subject instanceof AccountSubject.Candidate c ? c.id() : null;

        long id = jdbc.sql("INSERT INTO directory_accounts (member_id, case_id, kind, origin, account_number, entra_object_id, "
                + "status, created_at, updated_at) VALUES (:member, :case, :kind, :origin, :number, :oid::uuid, 'ACTIVE', :now, :now) "
                + "RETURNING id")
            .param("member", memberId).param("case", caseId).param("kind", kind.name()).param("origin", origin.name())
            .param("number", number).param("oid", UUID.fromString(objectId).toString()).param("now", java.sql.Timestamp.from(now))
            .query(Long.class).single();

        return byId(id).orElseThrow();
    }

    /**
     * D4: {@code updated_at} moves only when the status really changes (SEC-24 counts the days from it, so a replay must
     * not restart the seven days).
     */
    void setStatus(long id, String status) {
        jdbc.sql("UPDATE directory_accounts SET status = :status, updated_at = :now WHERE id = :id AND status <> :status")
            .param("status", status).param("now", java.sql.Timestamp.from(clock.instant())).param("id", id).update();
    }

    /**
     * The trial account becomes the member's account: the same Entra object, now of kind MEMBER, bound to the member and
     * no longer to the recruitment case.
     */
    void convertToMember(long id, long memberId, String number) {
        jdbc.sql("UPDATE directory_accounts SET kind = 'MEMBER', member_id = :member, case_id = NULL, account_number = :number, "
                + "updated_at = :now WHERE id = :id")
            .param("member", memberId).param("number", number).param("now", java.sql.Timestamp.from(clock.instant()))
            .param("id", id).update();
    }

    private static Account map(ResultSet rs, int row) throws SQLException {
        return new Account(
            rs.getLong("id"),
            (Long) rs.getObject("member_id"),
            (Long) rs.getObject("case_id"),
            AccountKind.valueOf(rs.getString("kind")),
            AccountOrigin.valueOf(rs.getString("origin")),
            rs.getString("account_number"),
            rs.getString("entra_object_id"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    }
}
