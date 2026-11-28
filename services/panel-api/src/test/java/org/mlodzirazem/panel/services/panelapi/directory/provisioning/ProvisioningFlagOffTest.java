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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProfile;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProfileSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountState;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountStatus;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AddressAvailability;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningException;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningRequest;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTicket;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The switch is off (the default), although Entra is configured and a fake Graph is listening: tasks are recorded, and not
 * one HTTP request is made, the token endpoint included (SEC-35).
 */
@SpringBootTest(properties = {
    "panel.entra.tenant-id=tenant-dummy",
    "panel.entra.client-id=client-dummy",
    "panel.entra.client-secret=secret-dummy"
})
@PostgresTest
class ProvisioningFlagOffTest extends ProvisioningTestSupport {
    @Autowired
    ApplicationContext context;

    @Autowired
    GraphDirectoryClient client;

    @Autowired
    AccountProfileSource profileSource;

    private static void assertDisabled(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ProvisioningException.class,
            e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.PROVISIONING_DISABLED));
    }

    @Test
    void tasksAreRecordedButNeitherTheWorkerNorAnyGraphCallExists() {
        assertThat(context.getBeansOfType(ProvisioningWorker.class)).isEmpty();
        assertThat(provisioning.enabled()).isFalse();

        ProvisioningTicket ticket = new TransactionTemplate(transactionManager).execute(s -> provisioning.enqueue(new ProvisioningRequest.CreateMemberAccount(1)));

        assertThat(ticket.dispatch()).isEqualTo(ProvisioningTicket.Dispatch.ENQUEUED);
        assertThat(taskState(ticket.taskId())).isEqualTo("PENDING");
        AccountState state = provisioning.accountState(new AccountSubject.Member(1));
        assertThat(state.status()).isEqualTo(AccountStatus.PENDING);
        assertThat(provisioning.accountState(new AccountSubject.Member(2)).status()).isEqualTo(AccountStatus.NONE);
        assertThat(GRAPH.getAllServeEvents()).isEmpty();
    }

    @Test
    void everySynchronousMethodRefusesAndAddressAvailabilityIsUnknown() {
        long account = registerAccount(new AccountSubject.Member(3), "MEMBER", "CREATED", "100003", oid(3), "ACTIVE");

        assertDisabled(() -> provisioning.issueTemporaryAccessPass(account));
        assertDisabled(() -> provisioning.checkGroup("55555555-5555-5555-5555-555555555555"));
        assertDisabled(() -> provisioning.groupMembers("55555555-5555-5555-5555-555555555555"));
        assertThat(provisioning.addressAvailability("jan.kowalski@" + DOMAIN)).isEqualTo(AddressAvailability.UNKNOWN);
        assertThat(GRAPH.getAllServeEvents()).isEmpty();
    }

    @Test
    void theWriteClientItselfMakesNoRequestWithoutTheSwitch() {
        String user = oid(4);
        String group = "55555555-5555-5555-5555-555555555555";

        assertDisabled(() -> client.user(user));
        assertDisabled(() -> client.userByUpn("100004@" + DOMAIN));
        assertDisabled(() -> client.userIdsByEmployeeId("100004"));
        assertDisabled(() -> client.createUser(new GraphDirectoryClient.NewUser("100004@" + DOMAIN, "A B", "A", "B", null, "100004", null)));
        assertDisabled(() -> client.patchUser(user, Map.of("accountEnabled", false)));
        assertDisabled(() -> client.assignLicense(user, SKU_MEMBER, java.util.List.of()));
        assertDisabled(() -> client.deleteUser(user));
        assertDisabled(() -> client.addGroupMember(group, user));
        assertDisabled(() -> client.removeGroupMember(group, user));
        assertDisabled(() -> client.checkGroup(group));
        assertDisabled(() -> client.groupUserIds(group));
        assertDisabled(() -> client.issueTemporaryAccessPass(user));
        assertThat(client.addressAvailability("jan.kowalski@" + DOMAIN)).isEqualTo(AddressAvailability.UNKNOWN);

        assertThat(GRAPH.getAllServeEvents()).isEmpty();
    }

    @Test
    void theMemberProfileComesFromTheMembersTableWhenAskedFor() {
        TMember member = newMember("Jan", "Nowak", "mazowieckie");
        jdbc.update("UPDATE members SET member_id = '205' WHERE id = ?", member.getId());

        Optional<AccountProfile> profile = profileSource.profile(new AccountSubject.Member(member.getId()));

        assertThat(profile).isPresent();
        assertThat(profile.get().accountNumber()).isEqualTo("000205");
        assertThat(profile.get().employeeId()).isEqualTo("000205");
        assertThat(profile.get().givenName()).isEqualTo("Jan");
        assertThat(profile.get().surname()).isEqualTo("Nowak");
        assertThat(profile.get().department()).isNotBlank();
        assertThat(profile.get().mail()).endsWith("@example.org");
        assertThat(profile.get().toString()).doesNotContain("Jan", "Nowak");
        assertThat(profileSource.supports(AccountSubject.Candidate.class)).isFalse();

        jdbc.update("UPDATE members SET member_id = NULL WHERE id = ?", member.getId());
        assertThat(profileSource.profile(new AccountSubject.Member(member.getId()))).isEmpty();
        assertThat(profileSource.profile(new AccountSubject.Member(987654321L))).isEmpty();
    }
}
