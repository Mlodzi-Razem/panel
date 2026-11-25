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
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.user.IdentityProvider;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserScopedValues;
import org.mlodzirazem.panel.services.panelapi.directory.api.EntraUserLookup;
import org.mlodzirazem.panel.services.panelapi.directory.api.EntraUserLookup.EntraUser;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@PostgresTest
class CurrentMemberResolverTest extends DomainTestSupport {
    private static final String OID = "7d1f3a2b-0c4e-4f5a-9b8c-112233445566";

    @Autowired
    CurrentMemberResolver resolver;

    @MockitoBean
    EntraUserLookup entraUsers;

    @Test
    void resolvesByIdentityWhenAlreadyLinked() {
        TMember member = newMember("Lena", "Linked", "mazowieckie");
        identity(member, "GOOGLE", "sub-1");

        Optional<TMember> resolved = resolver.resolve(new UserAuthInfo("sub-1", "someone-else@example.org", null));

        assertThat(resolved).map(TMember::getId).contains(member.getId());
        assertThat(auditCount("members :: link-user")).isZero();
    }

    @Test
    void subjectsAreScopedToTheirProvider() {
        TMember member = newMember("Pia", "Provider", "mazowieckie");
        identity(member, "GOOGLE", OID);

        assertThat(resolver.resolve(entra(OID))).isEmpty();
    }

    @Test
    void linksByDomainEmailIgnoringCaseAndAuditsIt() {
        TMember member = newMember("Eva", "Mail", "mazowieckie");
        String email = member.getDomainEmail();

        Optional<TMember> resolved = resolver.resolve(new UserAuthInfo("sub-2", email.toUpperCase(), "Eva"));

        assertThat(resolved).map(TMember::getId).contains(member.getId());
        assertThat(subject(member, "GOOGLE")).isEqualTo("sub-2");
        assertThat(auditCount("members :: link-user")).isEqualTo(1);
        assertThat(auditPayloads("members :: link-user")).contains("identity").contains("GOOGLE")
                                                         .doesNotContain("sub-2").doesNotContain(email);

        assertThat(resolver.resolve(new UserAuthInfo("sub-2", null, null))).map(TMember::getId)
                                                                          .contains(member.getId());
        assertThat(auditCount("members :: link-user")).isEqualTo(1);
    }

    @Test
    void doesNotStealMemberLinkedToAnotherAccount() {
        TMember member = newMember("Tom", "Taken", "mazowieckie");
        identity(member, "GOOGLE", "sub-owner");

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
    void linksEntraAccountByEmployeeIdIgnoringLeadingZeros() {
        TMember member = newMember("Ola", "Entra", "mazowieckie");
        memberNumber(member, "205");
        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "000205", true, upn("000205", member))));

        assertThat(resolver.resolve(entra(OID))).map(TMember::getId).contains(member.getId());
        assertThat(subject(member, "ENTRA")).isEqualTo(OID);
        assertThat(auditPayloads("members :: link-user")).contains("ENTRA").doesNotContain(OID);

        assertThat(resolver.resolve(entra(OID))).map(TMember::getId).contains(member.getId());
        assertThat(auditCount("members :: link-user")).isEqualTo(1);
    }

    @Test
    void neverLinksTrialUnallocatedOrFixtureNumbers() {
        for (String number : List.of("990001", "975001", "950001")) {
            TMember member = newMember("Fay", "Band" + number, "mazowieckie");
            memberNumber(member, number);
            when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, number, true, upn(number, member))));

            assertThat(resolver.resolve(entra(OID))).as(number).isEmpty();
        }

        assertThat(auditCount("members :: link-user")).isZero();
    }

    @Test
    void theSignInNameMustBeTheMemberNumber() {
        TMember member = newMember("Kira", "Upn", "mazowieckie");
        memberNumber(member, "205");

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "000205", true,
            "jan.kowalski@" + domainOf(member))));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "205", true, upn("000206", member))));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "205", true, null)));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "205", true, upn("000205", member))));
        assertThat(resolver.resolve(entra(OID))).map(TMember::getId).contains(member.getId());
    }

    private static String upn(String localPart, TMember member) {
        return localPart + "@" + domainOf(member);
    }

    private static String domainOf(TMember member) {
        String email = member.getDomainEmail();
        return email.substring(email.indexOf('@') + 1);
    }

    @Test
    void disabledOrUnnumberedAccountsDoNotLinkEvenWithTheRightUpn() {
        TMember member = newMember("Ewa", "Negative", "mazowieckie");
        memberNumber(member, "208");
        String upn = upn("000208", member);

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "208", false, upn)));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, null, true, upn)));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "2 08", true, upn)));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        assertThat(auditCount("members :: link-user")).isZero();
    }

    @Test
    void aForeignDomainInTheUpnNeverLinks() {
        TMember member = newMember("Oto", "Foreign", "mazowieckie");
        memberNumber(member, "207");
        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "207", true, "000207@evil.example")));

        assertThat(resolver.resolve(entra(OID))).isEmpty();
        assertThat(auditCount("members :: link-user")).isZero();
    }

    @Test
    void entraLinkingKeepsAnExistingGoogleIdentity() {
        TMember member = newMember("Gia", "Both", "mazowieckie");
        memberNumber(member, "000301");
        identity(member, "GOOGLE", "sub-gia");
        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "301", true, upn("000301", member))));

        assertThat(resolver.resolve(entra(OID))).map(TMember::getId).contains(member.getId());
        assertThat(resolver.resolve(new UserAuthInfo("sub-gia", null, null))).map(TMember::getId)
                                                                            .contains(member.getId());
    }

    @Test
    void doesNotLinkDisabledAccountsOrAccountsWithoutAValidNumber() {
        TMember member = newMember("Dan", "Disabled", "mazowieckie");
        memberNumber(member, "777");

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "777", false, null)));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, null, true, null)));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "77 7", true, null)));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "000000", true, null)));
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        when(entraUsers.find(OID)).thenReturn(Optional.empty());
        assertThat(resolver.resolve(entra(OID))).isEmpty();

        assertThat(auditCount("members :: link-user")).isZero();
    }

    @Test
    void doesNotLinkASecondEntraAccountToTheSameMember() {
        TMember member = newMember("Sam", "Second", "mazowieckie");
        memberNumber(member, "412");
        identity(member, "ENTRA", "11111111-2222-3333-4444-555555555555");
        when(entraUsers.find(OID)).thenReturn(Optional.of(new EntraUser(OID, "412", true, upn("000412", member))));

        assertThat(resolver.resolve(entra(OID))).isEmpty();
    }

    @Test
    void concurrentFirstSignInsLinkTheMemberOnlyOnce() throws Exception {
        TMember member = newMember("Ula", "Race", "mazowieckie");
        memberNumber(member, "206");
        String upn = upn("000206", member);
        String first = "7d1f3a2b-0c4e-4f5a-9b8c-aaaaaaaaaaaa";
        String second = "7d1f3a2b-0c4e-4f5a-9b8c-bbbbbbbbbbbb";
        when(entraUsers.find(first)).thenReturn(Optional.of(new EntraUser(first, "206", true, upn)));
        when(entraUsers.find(second)).thenReturn(Optional.of(new EntraUser(second, "206", true, upn)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (String oid : List.of(first, second)) {
                Callable<Long> signIn = () -> {
                    start.await();
                    try {
                        return resolver.resolve(entra(oid)).map(TMember::getId).orElse(null);
                    } catch (ConflictException lost) {
                        // the losing request of a race gets a conflict, never a 500 (SEC-23)
                        return null;
                    }
                };
                futures.add(pool.submit(signIn));
            }
            start.countDown();

            long linked = 0;
            for (Future<Long> future : futures) {
                if (future.get(60, TimeUnit.SECONDS) != null) {
                    linked++;
                }
            }
            assertThat(linked).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM member_identities WHERE member_id = ? AND provider = 'ENTRA'::identity_provider",
            Integer.class, member.getId())).isEqualTo(1);
    }

    @Test
    void googleSignInNeverQueriesEntra() {
        TMember member = newMember("Gus", "Google", "mazowieckie");

        resolver.resolve(new UserAuthInfo("sub-gus", member.getDomainEmail(), null));

        verify(entraUsers, never()).find(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void currentUsesTheRequestScopedUser() {
        TMember member = newMember("Cal", "Current", "mazowieckie");
        UserAuthInfo info = new UserAuthInfo("sub-3", member.getDomainEmail(), null);

        assertThat(resolver.current()).isEmpty();

        Optional<TMember> current = ScopedValue.where(UserScopedValues.USER_AUTH_INFO, info).call(resolver::current);

        assertThat(current).map(TMember::getId).contains(member.getId());
    }

    private static UserAuthInfo entra(String oid) {
        return new UserAuthInfo(oid, null, null, IdentityProvider.ENTRA);
    }

    private void identity(TMember member, String provider, String subject) {
        jdbc.update("INSERT INTO member_identities (member_id, provider, subject) VALUES (?, ?::identity_provider, ?)",
            member.getId(), provider, subject);
    }

    private void memberNumber(TMember member, String number) {
        jdbc.update("UPDATE members SET member_id = ? WHERE id = ?", number, member.getId());
    }

    private String subject(TMember member, String provider) {
        return jdbc.queryForObject(
            "SELECT subject FROM member_identities WHERE member_id = ? AND provider = ?::identity_provider",
            String.class, member.getId(), provider);
    }
}
