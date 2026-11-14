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

package org.mlodzirazem.panel.services.panelapi.members;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserScopedValues;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@PostgresTest
class CurrentMemberResolverTest extends DomainTestSupport {
    @Autowired
    CurrentMemberResolver resolver;

    @Test
    void resolvesBySubWhenAlreadyLinked() {
        TMember member = newMember("Lena", "Linked", "mazowieckie");
        jdbc.update("UPDATE members SET directory_user_id = 'sub-1' WHERE id = ?", member.getId());

        Optional<TMember> resolved = resolver.resolve(new UserAuthInfo("sub-1", "someone-else@example.org", null));

        assertThat(resolved).map(TMember::getId).contains(member.getId());
        assertThat(auditCount("members :: link-user")).isZero();
    }

    @Test
    void linksByDomainEmailIgnoringCaseAndAuditsIt() {
        TMember member = newMember("Eva", "Mail", "mazowieckie");
        String email = member.getDomainEmail();

        Optional<TMember> resolved = resolver.resolve(new UserAuthInfo("sub-2", email.toUpperCase(), "Eva"));

        assertThat(resolved).map(TMember::getId).contains(member.getId());
        assertThat(jdbc.queryForObject("SELECT directory_user_id FROM members WHERE id = ?", String.class, member.getId()))
            .isEqualTo("sub-2");
        assertThat(auditCount("members :: link-user")).isEqualTo(1);
        assertThat(auditPayloads("members :: link-user")).contains("directoryUserId").doesNotContain("sub-2")
                                                         .doesNotContain(email);

        assertThat(resolver.resolve(new UserAuthInfo("sub-2", null, null))).map(TMember::getId)
                                                                          .contains(member.getId());
        assertThat(auditCount("members :: link-user")).isEqualTo(1);
    }

    @Test
    void doesNotStealMemberLinkedToAnotherAccount() {
        TMember member = newMember("Tom", "Taken", "mazowieckie");
        jdbc.update("UPDATE members SET directory_user_id = 'sub-owner' WHERE id = ?", member.getId());

        assertThat(resolver.resolve(new UserAuthInfo("sub-thief", member.getDomainEmail(), null))).isEmpty();
    }

    @Test
    void returnsEmptyWithoutMatch() {
        newMember("Nia", "Nobody", "mazowieckie");

        assertThat(resolver.resolve(new UserAuthInfo("sub-x", "unknown@example.org", null))).isEmpty();
        assertThat(resolver.resolve(new UserAuthInfo("sub-y", null, null))).isEmpty();
        assertThat(resolver.resolve(new UserAuthInfo("sub-z", "  ", null))).isEmpty();
    }

    @Test
    void currentUsesTheRequestScopedUser() {
        TMember member = newMember("Cal", "Current", "mazowieckie");
        UserAuthInfo info = new UserAuthInfo("sub-3", member.getDomainEmail(), null);

        assertThat(resolver.current()).isEmpty();

        Optional<TMember> current = ScopedValue.where(UserScopedValues.USER_AUTH_INFO, info).call(resolver::current);

        assertThat(current).map(TMember::getId).contains(member.getId());
    }
}
