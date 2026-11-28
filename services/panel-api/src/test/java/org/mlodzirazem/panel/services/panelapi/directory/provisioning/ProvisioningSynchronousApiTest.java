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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AddressAvailability;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.DirectoryGroupMember;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.GroupCheck;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningException;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.TemporaryAccessPass;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The synchronous methods: the Temporary Access Pass (the canary test of SEC-10), group checks and listings, and the
 * address availability.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProvisioningSynchronousApiTest extends EnabledProvisioningTest {
    private static final String CANARY = "CANARY-TAP-7f3a9c";
    private static final String GROUP = "55555555-5555-5555-5555-555555555555";

    private long accountWithUser(int member, boolean enabled) {
        long account = registerAccount(new AccountSubject.Member(member), "MEMBER", "CREATED", String.format("%06d", 100_000 + member), oid(member),
            enabled ? "ACTIVE" : "DISABLED");
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users/" + oid(member))).willReturn(okJson(
            userJson(oid(member), String.format("%06d@" + DOMAIN, 100_000 + member), String.format("%06d", 100_000 + member), enabled, "Member", Instant.now().toString()))));
        return account;
    }

    private static void stubTap(int member, int status, String body) {
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/users/" + oid(member) + "/authentication/temporaryAccessPassMethods"))
            .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private void assertNoCanaryAnywhere(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(CANARY);

        List<String[]> columns = jdbc.query("SELECT c.table_name, c.column_name FROM information_schema.columns c "
                + "JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name "
                + "WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE' "
                + "AND c.data_type IN ('text', 'character varying', 'character', 'jsonb', 'json')",
            (rs, row) -> new String[]{rs.getString(1), rs.getString(2)});

        assertThat(columns).isNotEmpty();

        for (String[] column : columns) {
            Integer hits = jdbc.queryForObject("SELECT count(*) FROM \"" + column[0] + "\" WHERE \"" + column[1] + "\"::text LIKE '%CANARY%'", Integer.class);
            assertThat(hits).as(column[0] + "." + column[1]).isZero();
        }
    }

    @Test
    void theTemporaryAccessPassExistsOnlyInMemory(CapturedOutput output) {
        long account = accountWithUser(80, true);
        stubTap(80, 201, "{\"id\":\"tap-id\",\"temporaryAccessPass\":\"" + CANARY + "\",\"lifetimeInMinutes\":60,\"isUsableOnce\":true}");

        try (TemporaryAccessPass pass = provisioning.issueTemporaryAccessPass(account)) {
            assertThat(pass.reveal()).isEqualTo(CANARY);
            assertThat(pass.usableOnce()).isTrue();
            assertThat(pass.toString()).doesNotContain(CANARY).contains("redacted");
        }

        GRAPH.verify(1, postRequestedFor(urlPathEqualTo("/v1.0/users/" + oid(80) + "/authentication/temporaryAccessPassMethods"))
            .withRequestBody(containing("\"isUsableOnce\":true")).withRequestBody(containing("\"lifetimeInMinutes\":60")));
        assertNoCanaryAnywhere(output);
    }

    @Test
    void aBrokenOrFailingTapAnswerLeaksNothingInTheExceptionOrTheLog(CapturedOutput output) {
        long account = accountWithUser(81, true);

        stubTap(81, 201, "{\"temporaryAccessPass\":\"" + CANARY + "\", broken");
        assertThatThrownBy(() -> provisioning.issueTemporaryAccessPass(account)).satisfies(e -> assertCleanChain(e));

        stubTap(81, 400, "{\"error\":{\"code\":\"Request_BadRequest\",\"message\":\"" + CANARY + "\"}}");
        assertThatThrownBy(() -> provisioning.issueTemporaryAccessPass(account)).satisfies(e -> assertCleanChain(e));

        stubTap(81, 500, CANARY);
        assertThatThrownBy(() -> provisioning.issueTemporaryAccessPass(account)).satisfies(e -> assertCleanChain(e));

        assertNoCanaryAnywhere(output);
    }

    private static void assertCleanChain(Throwable error) {
        assertThat(error).isInstanceOf(ProvisioningException.class);

        for (Throwable t = error; t != null; t = t.getCause()) {
            assertThat(String.valueOf(t.getMessage())).doesNotContain(CANARY);
            assertThat(t.toString()).doesNotContain(CANARY);
        }

        assertThat(error.getCause()).isNull();
    }

    @Test
    void aTapIsRefusedForADisabledOrUnknownAccountAndWithoutAGraphCall() {
        long disabled = accountWithUser(82, false);
        assertThatThrownBy(() -> provisioning.issueTemporaryAccessPass(disabled)).isInstanceOfSatisfying(ProvisioningException.class,
            e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.ACCOUNT_DISABLED));
        assertThatThrownBy(() -> provisioning.issueTemporaryAccessPass(987654)).isInstanceOfSatisfying(ProvisioningException.class,
            e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.ACCOUNT_NOT_REGISTERED));
        assertThat(count("POST", "temporaryAccessPassMethods")).isZero();
    }

    @Test
    void aThrottledTapReportsRetryAfter() {
        long account = accountWithUser(83, true);
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/users/" + oid(83) + "/authentication/temporaryAccessPassMethods"))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "12")));

        assertThatThrownBy(() -> provisioning.issueTemporaryAccessPass(account)).isInstanceOfSatisfying(ProvisioningException.class, e -> {
            assertThat(e.code()).isEqualTo(ProvisioningErrorCode.GRAPH_THROTTLED);
            assertThat(e.retryAfter()).hasSeconds(12);
        });
    }

    @Test
    void checkingAGroupAnswersEachProperty() {
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP)).willReturn(aResponse().withStatus(404)));
        assertThat(provisioning.checkGroup(GROUP)).isEqualTo(GroupCheck.NOT_FOUND);

        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\",\"isAssignableToRole\":true}")));
        assertThat(provisioning.checkGroup(GROUP)).isEqualTo(GroupCheck.ROLE_ASSIGNABLE);

        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\",\"membershipRule\":\"user.department -eq 'x'\"}")));
        assertThat(provisioning.checkGroup(GROUP)).isEqualTo(GroupCheck.DYNAMIC);

        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\"}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_GROUPS + "/members/" + GROUP)).willReturn(aResponse().withStatus(404)));
        assertThat(provisioning.checkGroup(GROUP)).isEqualTo(GroupCheck.OUTSIDE_ADMINISTRATIVE_UNIT);

        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_GROUPS + "/members/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\"}")));
        assertThat(provisioning.checkGroup(GROUP)).isEqualTo(GroupCheck.MANAGEABLE);

        assertThatThrownBy(() -> provisioning.checkGroup("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void groupMembersFollowsPagesMatchesTheRegistryAndNeverLeavesTheGraphHost() {
        accountWithUser(84, true);
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\"}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_GROUPS + "/members/" + GROUP)).willReturn(okJson("{}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP + "/members/microsoft.graph.user"))
            .willReturn(okJson("{\"value\":[{\"id\":\"" + oid(84) + "\"}],\"@odata.nextLink\":\"" + GRAPH.baseUrl() + "/v1.0/page2\"}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/page2")).willReturn(okJson("{\"value\":[{\"id\":\"" + oid(85) + "\"}]}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP + "/owners")).willReturn(okJson("{\"value\":[{\"id\":\"" + oid(85) + "\"}]}")));

        List<DirectoryGroupMember> members = provisioning.groupMembers(GROUP);

        assertThat(members).hasSize(2);
        assertThat(members.get(0).objectId()).isEqualTo(oid(84));
        assertThat(members.get(0).memberId()).isEqualTo(84L);
        assertThat(members.get(0).member()).isTrue();
        assertThat(members.get(0).owner()).isFalse();
        assertThat(members.get(1).accountId()).isNull();
        assertThat(members.get(1).owner()).isTrue();
        assertThat(members.get(1).member()).isTrue();
        assertThat(graphRequests().stream().filter(r -> r.getUrl().contains("microsoft.graph.user")).findFirst().orElseThrow().getUrl())
            .contains("$select=id");

        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP + "/members/microsoft.graph.user"))
            .willReturn(okJson("{\"value\":[],\"@odata.nextLink\":\"http://evil.example/v1.0/page2\"}")));
        assertThatThrownBy(() -> provisioning.groupMembers(GROUP)).isInstanceOfSatisfying(ProvisioningException.class,
            e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.GRAPH_REJECTED));
    }

    @Test
    void addressAvailabilityIsFreeTakenOrUnknownAndNeverThrows(CapturedOutput output) {
        String address = "jan.kowalski@" + DOMAIN;
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users")).willReturn(okJson("{\"value\":[]}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups")).willReturn(okJson("{\"value\":[]}")));
        assertThat(provisioning.addressAvailability(address)).isEqualTo(AddressAvailability.FREE);

        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups")).willReturn(okJson("{\"value\":[{\"id\":\"x\"}]}")));
        assertThat(provisioning.addressAvailability(address)).isEqualTo(AddressAvailability.TAKEN);

        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups")).willReturn(aResponse().withStatus(503)));
        assertThat(provisioning.addressAvailability(address)).isEqualTo(AddressAvailability.UNKNOWN);
        assertThat(provisioning.addressAvailability("x' or 1 eq 1 or mail eq 'y@a.b")).isEqualTo(AddressAvailability.UNKNOWN);
        assertThat(output.getAll()).doesNotContain("jan.kowalski");
    }
}
