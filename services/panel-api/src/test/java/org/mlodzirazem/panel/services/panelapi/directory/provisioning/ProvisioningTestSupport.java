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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProfile;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.MemberAccountProvisioning;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningRequest;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * A fake Microsoft Graph (WireMock) in place of the tenant: the Entra properties of the test context point both the
 * authority and Graph at it, with dummy tenant, client and secret. Nothing here reaches a real tenant.
 */
abstract class ProvisioningTestSupport extends DomainTestSupport {
    static final String SKU_MEMBER = "11111111-1111-1111-1111-111111111111";
    static final String SKU_TRIAL = "22222222-2222-2222-2222-222222222222";
    static final String AU_MEMBERS = "33333333-3333-3333-3333-333333333333";
    static final String AU_GROUPS = "44444444-4444-4444-4444-444444444444";
    static final String PROTECTED_OID = "99999999-9999-9999-9999-999999999999";
    static final String DOMAIN = "mlodzirazem.org";

    static final WireMockServer GRAPH = new WireMockServer(options().dynamicPort().globalTemplating(true));

    static {
        GRAPH.start();
    }

    @DynamicPropertySource
    static void fakeGraph(DynamicPropertyRegistry registry) {
        registry.add("panel.entra.graph-url", GRAPH::baseUrl);
        registry.add("panel.entra.authority-url", GRAPH::baseUrl);
    }

    @Autowired
    protected MemberAccountProvisioning provisioning;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetGraphAndTables() {
        GRAPH.resetAll();
        stubToken("UserAuthMethod-TAP.ReadWrite.All");
        jdbc.execute("DELETE FROM directory_provisioning_tasks");
        jdbc.execute("DELETE FROM directory_accounts");
    }

    /** The app-only token: an unsigned JWT whose {@code roles} claim carries the given application roles. */
    static String jwt(String... roles) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"roles\":[" + String.join(",", java.util.Arrays.stream(roles).map(r -> "\"" + r + "\"").toList())
            + "]}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    static void stubToken(String... roles) {
        GRAPH.stubFor(post(urlPathEqualTo("/tenant-dummy/oauth2/v2.0/token"))
            .willReturn(okJson("{\"access_token\":\"" + jwt(roles) + "\",\"expires_in\":3600}")));
    }

    static String oid(int n) {
        return String.format("aaaaaaaa-0000-0000-0000-%012d", n);
    }

    static String userJson(String oid, String upn, String employeeId, boolean enabled, String userType, String created, String... skus) {
        String licenses = String.join(",", java.util.Arrays.stream(skus).map(s -> "{\"skuId\":\"" + s + "\"}").toList());
        return "{\"id\":\"" + oid + "\",\"userPrincipalName\":\"" + upn + "\","
            + (employeeId == null ? "" : "\"employeeId\":\"" + employeeId + "\",")
            + "\"accountEnabled\":" + enabled + ",\"userType\":\"" + userType + "\",\"createdDateTime\":\"" + created + "\","
            + "\"assignedLicenses\":[" + licenses + "]}";
    }

    /** What the Panel sent to Graph, token requests excluded, oldest first. */
    static List<LoggedRequest> graphRequests() {
        return GRAPH.getAllServeEvents().stream().map(e -> e.getRequest())
            .filter(r -> !r.getUrl().contains("/oauth2/")).sorted(java.util.Comparator.comparing(LoggedRequest::getLoggedDate)).toList();
    }

    static long count(String method, String urlPart) {
        return graphRequests().stream().filter(r -> r.getMethod().getName().equals(method) && r.getUrl().contains(urlPart)).count();
    }

    protected long enqueue(ProvisioningRequest request) {
        Long id = new TransactionTemplate(transactionManager).execute(status -> provisioning.enqueue(request).taskId());
        return id == null ? -1 : id;
    }

    protected void makeDue(long taskId) {
        jdbc.update("UPDATE directory_provisioning_tasks SET next_attempt_at = now() - interval '1 second' WHERE id = ?", taskId);
    }

    protected String taskState(long taskId) {
        return jdbc.queryForObject("SELECT state FROM directory_provisioning_tasks WHERE id = ?", String.class, taskId);
    }

    protected String taskError(long taskId) {
        return jdbc.queryForObject("SELECT last_error_code FROM directory_provisioning_tasks WHERE id = ?", String.class, taskId);
    }

    protected int taskAttempts(long taskId) {
        return jdbc.queryForObject("SELECT attempts FROM directory_provisioning_tasks WHERE id = ?", Integer.class, taskId);
    }

    /** Registers an account directly, as if an earlier task had created it. */
    protected long registerAccount(AccountSubject subject, String kind, String origin, String number, String objectId, String status) {
        Long memberId = subject instanceof AccountSubject.Member m ? m.id() : null;
        Long caseId = subject instanceof AccountSubject.Candidate c ? c.id() : null;

        return jdbc.queryForObject("INSERT INTO directory_accounts (member_id, case_id, kind, origin, account_number, entra_object_id, status) "
            + "VALUES (?, ?, ?, ?, ?, ?::uuid, ?) RETURNING id", Long.class, memberId, caseId, kind, origin, number, objectId, status);
    }

    /** The profile the stubbed source gives: member n is 100000+n, a trial case n is 990000+n. */
    static AccountProfile profileOf(AccountSubject subject) {
        boolean member = subject instanceof AccountSubject.Member;
        String number = String.format("%06d", (member ? 100_000 : 990_000) + subject.id());

        return new AccountProfile(number, member ? number : null, "Given" + subject.id(), "Surname" + subject.id(),
            "Given" + subject.id() + " Surname" + subject.id(), "Mazowieckie", member ? "given" + subject.id() + "@" + DOMAIN : null);
    }
}
