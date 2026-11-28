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
import org.mlodzirazem.panel.services.panelapi.audit.AuditLog;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningRequest;
import org.mlodzirazem.panel.services.panelapi.directory.provisioning.ProvisioningTasks.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds (SEC-35, SEC-40, SEC-41), the permissions guard of the worker (SEC-2) and two workers on one database (SEC-32).
 */
class ProvisioningHoldsAndConcurrencyTest extends EnabledProvisioningTest {
    @Autowired
    ProvisioningGate gate;

    @Autowired
    ProvisioningTasks tasks;

    @Autowired
    ProvisioningExecutor executor;

    @Autowired
    AccountRegistry registry;

    @Autowired
    ProvisioningProperties properties;

    @Autowired
    AuditLog auditLog;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    Clock clock;

    private ProvisioningProperties with(boolean destructive, int perHour, Duration maxAge) {
        return new ProvisioningProperties(true, destructive, properties.domain(), "PL", properties.memberLicenseSku(), properties.trialLicenseSku(),
            properties.tapLifetime(), properties.membersAdministrativeUnitId(), properties.groupAdministrativeUnitId(), properties.protectedObjectIds(),
            false, properties.pollInterval(), 100, 10, properties.backoffInitial(), properties.backoffMax(), properties.forbiddenGrace(), maxAge,
            perHour, false, properties.forbiddenPermissions());
    }

    @Test
    void twentyFiveDisablesWithALimitOfTenRunTenAndHoldFifteen() {
        for (int member = 1; member <= 25; member++) {
            enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Member(member), 1));
        }

        List<Task> running = tasks.claim("test", Instant.now(), Duration.ofMinutes(5), 100, with(true, 10, Duration.ofDays(7)));

        assertThat(running).hasSize(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_provisioning_tasks WHERE state = 'HELD' AND last_error_code = 'HOLD_DESTRUCTIVE_LIMIT'", Integer.class))
            .isEqualTo(15);
        assertThat(graphRequests()).isEmpty();
    }

    @Test
    void aReleasedTaskRunsPastTheLimitButStillCounts() {
        long first = enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Member(2), 1));
        tasks.claim("test", Instant.now(), Duration.ofMinutes(5), 100, with(true, 0, Duration.ofDays(7)));
        assertThat(taskState(first)).isEqualTo("HELD");
        assertThat(taskError(first)).isEqualTo("HOLD_DESTRUCTIVE_LIMIT");

        provisioning.release(first);
        List<Task> running = tasks.claim("test", Instant.now(), Duration.ofMinutes(5), 100, with(true, 0, Duration.ofDays(7)));

        assertThat(running).extracting(Task::id).containsExactly(first);
    }

    @Test
    void destructiveTasksWaitWhileTheirFlagIsOff() {
        long task = enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Member(1), 1));
        long safe = enqueue(new ProvisioningRequest.CreateMemberAccount(2));

        List<Task> running = tasks.claim("test", Instant.now(), Duration.ofMinutes(5), 100, with(false, 10, Duration.ofDays(7)));

        assertThat(running).extracting(Task::id).containsExactly(safe);
        assertThat(taskState(task)).isEqualTo("HELD");
        assertThat(taskError(task)).isEqualTo("HOLD_DESTRUCTIVE_DISABLED");
    }

    @Test
    void aTaskOlderThanTheMaximumAgeIsHeldUntilAPersonReleasesIt() {
        stubCreateFlowFor(3);
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(3));
        jdbc.update("UPDATE directory_provisioning_tasks SET ready_since = now() - interval '8 days' WHERE id = ?", task);

        assertThat(worker.runOnce()).isZero();
        assertThat(taskState(task)).isEqualTo("HELD");
        assertThat(taskError(task)).isEqualTo("HOLD_STALE");
        assertThat(graphRequests()).isEmpty();

        provisioning.release(task);
        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(taskState(task)).isEqualTo("DONE");
    }

    private static void stubCreateFlowFor(int member) {
        String number = String.format("%06d", 100_000 + member);
        GRAPH.stubFor(get(urlPathMatching("/v1.0/users/" + number + "(@|%40).*")).willReturn(aResponse().withStatus(404)));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users")).withQueryParam("$filter", containing("employeeId eq")).willReturn(okJson("{\"value\":[]}")));
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_MEMBERS + "/members"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + oid(member) + "\"}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users/" + oid(member))).willReturn(okJson(userJson(oid(member), number + "@" + DOMAIN, number, true, "Member", Instant.now().toString()))));
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/users/" + oid(member) + "/assignLicense")).willReturn(okJson("{}")));
    }

    @Test
    void aTokenWithATenantWidePermissionStopsTheWorkerBeforeAnyGraphCall() {
        // a token of its own: the shared application token caches its JWT for an hour
        org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken broad = new org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken() {
            @Override
            public boolean configured() {
                return true;
            }

            @Override
            public String graphUrl() {
                return GRAPH.baseUrl();
            }

            @Override
            public String bearer() {
                return jwt("User.ReadWrite.All", "UserAuthMethod-TAP.ReadWrite.All");
            }
        };
        ProvisioningGate fresh = new ProvisioningGate(properties, broad, jsonMapperBean, clock);
        ProvisioningWorker stopped = new ProvisioningWorker(fresh, tasks, executor, registry, properties, auditLog, publisher, transactionManager, clock);
        enqueue(new ProvisioningRequest.CreateMemberAccount(4));

        assertThat(fresh.enabled()).isFalse();
        assertThat(stopped.runOnce()).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM directory_provisioning_tasks", String.class)).isEqualTo("PENDING");
        assertThat(graphRequests()).isEmpty();
    }

    @Autowired
    tools.jackson.databind.json.JsonMapper jsonMapperBean;

    @Test
    void roleAssignmentPermissionsAreForbiddenToo() {
        assertThat(gate.forbiddenRoles(jwt("RoleManagement.ReadWrite.Directory", "UserAuthMethod-TAP.ReadWrite.All"))).containsExactly("RoleManagement.ReadWrite.Directory");
        assertThat(gate.forbiddenRoles(jwt("UserAuthMethod-TAP.ReadWrite.All", "User.Read.All"))).isEmpty();
        assertThat(gate.forbiddenRoles("not-a-jwt")).isNull();
    }

    @Test
    void twoWorkersOnOneDatabaseRunEveryTaskExactlyOnce() throws Exception {
        int members = 20;

        for (int member = 1; member <= members; member++) {
            enqueue(new ProvisioningRequest.CreateMemberAccount(member));
        }

        GRAPH.stubFor(get(urlPathMatching("/v1.0/users/1000\\d\\d(@|%40).*")).willReturn(aResponse().withStatus(404)));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users")).withQueryParam("$filter", containing("employeeId eq")).willReturn(okJson("{\"value\":[]}")));
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_MEMBERS + "/members"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"aaaaaaaa-0000-0000-0000-{{randomValue length=12 type='NUMERIC'}}\"}")));
        GRAPH.stubFor(get(urlPathMatching("/v1.0/users/aaaaaaaa-0000-0000-0000-\\d{12}")).willReturn(okJson(
            "{\"id\":\"{{request.pathSegments.[2]}}\",\"userPrincipalName\":\"x\",\"accountEnabled\":true,\"userType\":\"Member\",\"assignedLicenses\":[]}")));
        GRAPH.stubFor(post(urlPathMatching("/v1.0/users/aaaaaaaa-0000-0000-0000-\\d{12}/assignLicense")).willReturn(okJson("{}")));

        ProvisioningWorker second = new ProvisioningWorker(gate, tasks, executor, registry, properties, auditLog, publisher, transactionManager, clock);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger ran = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (ProvisioningWorker w : List.of(worker, second)) {
            futures.add(pool.submit(() -> {
                int n;

                do {
                    n = w.runOnce();
                    ran.addAndGet(n);
                } while (n > 0);
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        pool.shutdown();

        assertThat(ran.get()).isEqualTo(members);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_provisioning_tasks WHERE state = 'DONE' AND attempts = 1", Integer.class)).isEqualTo(members);
        assertThat(count("POST", "/administrativeUnits/")).isEqualTo(members);
        assertThat(graphRequests().stream().filter(r -> r.getUrl().endsWith("/members")).map(r -> r.getBodyAsString().replaceAll(".*\"userPrincipalName\":\"([^\"]+)\".*", "$1")).distinct())
            .hasSize(members);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_accounts", Integer.class)).isEqualTo(members);
    }

    @Test
    void aWorkerThatLostItsLeaseDoesNotOverwriteTheOutcome() {
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(5));
        List<Task> claimed = tasks.claim("slow-worker", Instant.now(), Duration.ofMinutes(5), 10, properties);
        assertThat(claimed).hasSize(1);

        jdbc.update("UPDATE directory_provisioning_tasks SET lease_until = now() - interval '1 minute' WHERE id = ?", task);
        List<Task> taken = tasks.claim("fast-worker", Instant.now(), Duration.ofMinutes(5), 10, properties);
        assertThat(taken).hasSize(1);

        assertThat(tasks.finish(task, "slow-worker", org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTaskState.DONE, null, Instant.now())).isFalse();
        assertThat(tasks.finish(task, "fast-worker", org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTaskState.DONE, null, Instant.now())).isTrue();
        assertThat(taskAttempts(task)).isEqualTo(2);
    }
}
