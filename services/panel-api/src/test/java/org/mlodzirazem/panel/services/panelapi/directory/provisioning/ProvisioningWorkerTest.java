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

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProvisioningFinished;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountState;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountStatus;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.GroupMappingSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningException;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningOperation;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningRequest;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningTicket;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningActorSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The outbox, the worker and the Graph client against a fake Graph: success, idempotency, throttling, retries, final
 * failures, the guard, supersession, ordering, conversion and group operations.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProvisioningWorkerTest extends EnabledProvisioningTest {
    private static final String NOW_ISO = Instant.now().toString();
    private static final String GROUP = "55555555-5555-5555-5555-555555555555";

    private static String upnPath(String upn) {
        return "/v1.0/users/" + upn.replace("@", "(@|%40)").replace(".", "\\.");
    }

    private static void stubUserAbsent(String upn) {
        GRAPH.stubFor(get(urlPathMatching(upnPath(upn))).willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
            .withBody("{\"error\":{\"code\":\"Request_ResourceNotFound\"}}")));
    }

    private static void stubEmployeeIdHolders(String... holders) {
        String value = String.join(",", java.util.Arrays.stream(holders).map(h -> "{\"id\":\"" + h + "\"}").toList());
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users")).withQueryParam("$filter", containing("employeeId eq"))
            .willReturn(okJson("{\"value\":[" + value + "]}")));
    }

    private static void stubCreate(String oid) {
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_MEMBERS + "/members"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"id\":\"" + oid + "\"}")));
    }

    private static void stubUser(String oid, String body) {
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users/" + oid)).willReturn(okJson(body)));
    }

    private static void stubAssignLicense(String oid) {
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/users/" + oid + "/assignLicense")).willReturn(okJson("{}")));
    }

    private static void stubCreateFlow(int member, String oid) {
        String upn = String.format("%06d@" + DOMAIN, 100_000 + member);
        stubUserAbsent(upn);
        stubEmployeeIdHolders();
        stubCreate(oid);
        stubUser(oid, userJson(oid, upn, String.format("%06d", 100_000 + member), true, "Member", NOW_ISO));
        stubAssignLicense(oid);
    }

    @Test
    void createsAMemberAccountRegistersItAndAnnouncesTheResult() {
        stubCreateFlow(7, oid(7));
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(7));

        assertThat(provisioning.accountState(new AccountSubject.Member(7)).status()).isEqualTo(AccountStatus.PENDING);
        assertThat(worker.runOnce()).isEqualTo(1);

        assertThat(taskState(task)).isEqualTo("DONE");
        AccountState state = provisioning.accountState(new AccountSubject.Member(7));
        assertThat(state.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(state.accountNumber()).isEqualTo("100007");
        assertThat(state.kind()).isEqualTo(AccountState.AccountKind.MEMBER);
        assertThat(state.origin()).isEqualTo(AccountState.AccountOrigin.CREATED);

        String body = graphRequests().stream().filter(r -> r.getMethod().getName().equals("POST") && r.getUrl().endsWith("/members"))
            .findFirst().orElseThrow().getBodyAsString();
        assertThat(body).contains("\"employeeId\":\"100007\"", "\"usageLocation\":\"PL\"", "\"accountEnabled\":true",
            "\"userPrincipalName\":\"100007@" + DOMAIN + "\"", "\"@odata.type\":\"#microsoft.graph.user\"");
        assertThat(count("POST", "/assignLicense")).isEqualTo(1);
        GRAPH.verify(postRequestedFor(urlPathEqualTo("/v1.0/users/" + oid(7) + "/assignLicense")).withRequestBody(containing(SKU_MEMBER)));

        assertThat(events.committed).singleElement().satisfies(event -> {
            assertThat(event.succeeded()).isTrue();
            assertThat(event.taskId()).isEqualTo(task);
            assertThat(event.operation()).isEqualTo(ProvisioningOperation.CREATE_MEMBER_ACCOUNT);
            assertThat(event.accountId()).isNotNull();
        });
    }

    @Test
    void enqueueingTheSameThingAgainDoesNothingNew() {
        stubCreateFlow(8, oid(8));
        long first = enqueue(new ProvisioningRequest.CreateMemberAccount(8));
        ProvisioningTicket again = new TransactionTemplate(transactionManager).execute(s -> provisioning.enqueue(new ProvisioningRequest.CreateMemberAccount(8)));

        assertThat(again.dispatch()).isEqualTo(ProvisioningTicket.Dispatch.DEDUPLICATED);
        assertThat(again.taskId()).isEqualTo(first);

        worker.runOnce();
        ProvisioningTicket afterDone = new TransactionTemplate(transactionManager).execute(s -> provisioning.enqueue(new ProvisioningRequest.CreateMemberAccount(8)));
        worker.runOnce();

        assertThat(afterDone.dispatch()).isEqualTo(ProvisioningTicket.Dispatch.DEDUPLICATED);
        assertThat(afterDone.taskId()).isEqualTo(first);
        assertThat(afterDone.state().name()).isEqualTo("DONE");
        assertThat(count("POST", "/administrativeUnits/")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_provisioning_tasks", Integer.class)).isEqualTo(1);
    }

    @Test
    void aRolledBackTransactionTakesItsTaskWithIt() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            provisioning.enqueue(new ProvisioningRequest.CreateMemberAccount(9));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_provisioning_tasks", Integer.class)).isZero();
        assertThat(worker.runOnce()).isZero();
        assertThat(graphRequests()).isEmpty();
    }

    @Test
    void throttlingWaitsForRetryAfter() {
        GRAPH.stubFor(get(urlPathMatching(upnPath("100010@" + DOMAIN))).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(10));
        Instant before = Instant.now();

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("PENDING");
        assertThat(taskError(task)).isEqualTo("GRAPH_THROTTLED");
        assertThat(taskAttempts(task)).isEqualTo(1);
        Instant next = jdbc.queryForObject("SELECT next_attempt_at FROM directory_provisioning_tasks WHERE id = ?", Timestamp.class, task).toInstant();
        assertThat(next).isAfterOrEqualTo(before.plusSeconds(30));
        assertThat(worker.runOnce()).isZero();
    }

    @Test
    void aServerErrorIsRetriedAndThenSucceeds() {
        GRAPH.stubFor(get(urlPathMatching(upnPath("100011@" + DOMAIN))).willReturn(aResponse().withStatus(503)));
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(11));
        Instant before = Instant.now();

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("PENDING");
        assertThat(taskError(task)).isEqualTo("GRAPH_UNAVAILABLE");
        Instant next = jdbc.queryForObject("SELECT next_attempt_at FROM directory_provisioning_tasks WHERE id = ?", Timestamp.class, task).toInstant();
        assertThat(next).isAfter(before.plusSeconds(20));

        GRAPH.resetAll();
        stubToken("UserAuthMethod-TAP.ReadWrite.All");
        stubCreateFlow(11, oid(11));
        makeDue(task);
        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        assertThat(taskAttempts(task)).isEqualTo(2);
    }

    @Test
    void runningOutOfAttemptsEndsTheTaskForGood() {
        GRAPH.stubFor(get(urlPathMatching(upnPath("100012@" + DOMAIN))).willReturn(aResponse().withStatus(503)));
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(12));

        for (int i = 0; i < 3; i++) {
            worker.runOnce();
            makeDue(task);
        }

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("ATTEMPTS_EXHAUSTED");
        assertThat(taskAttempts(task)).isEqualTo(3);
        assertThat(worker.runOnce()).isZero();
        assertThat(provisioning.accountState(new AccountSubject.Member(12)).status()).isEqualTo(AccountStatus.FAILED);
        assertThat(events.committed).singleElement().satisfies(e -> {
            assertThat(e.succeeded()).isFalse();
            assertThat(e.errorCode()).isEqualTo(ProvisioningErrorCode.ATTEMPTS_EXHAUSTED);
        });
    }

    @Test
    void aPermanentErrorEndsInOneAttemptAndCanBeRetriedByAPerson() {
        stubUserAbsent("100013@" + DOMAIN);
        stubEmployeeIdHolders();
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_MEMBERS + "/members")).willReturn(aResponse().withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":{\"code\":\"Request_BadRequest\",\"message\":\"Another object with the same value for property userPrincipalName already exists.\","
                + "\"details\":[{\"code\":\"ObjectConflict\"}]}}")));
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(13));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("UPN_CONFLICT");
        assertThat(count("POST", "/administrativeUnits/")).isEqualTo(1);
        AccountState state = provisioning.accountState(new AccountSubject.Member(13));
        assertThat(state.status()).isEqualTo(AccountStatus.FAILED);
        assertThat(state.lastError()).isEqualTo(ProvisioningErrorCode.UPN_CONFLICT);

        assertThat(provisioning.retry(task).state().name()).isEqualTo("PENDING");
        assertThat(taskAttempts(task)).isZero();
    }

    @Test
    void retryAndReleaseNeedThePermissionOfTheirKindAndNeverApplyToOneself() {
        long accountTask = enqueue(new ProvisioningRequest.CreateMemberAccount(14));
        jdbc.update("UPDATE directory_provisioning_tasks SET state = 'FAILED', finished_at = now(), last_error_code = 'GRAPH_REJECTED' WHERE id = ?", accountTask);

        when(actors.current()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> provisioning.retry(accountTask)).isInstanceOfSatisfying(ProvisioningException.class,
            e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.ACTION_NOT_PERMITTED));

        when(actors.current()).thenReturn(Optional.of(new ProvisioningActorSource.Actor(2, false, true)));
        assertThatThrownBy(() -> provisioning.retry(accountTask)).isInstanceOfSatisfying(ProvisioningException.class,
            e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.ACTION_NOT_PERMITTED));

        when(actors.current()).thenReturn(Optional.of(new ProvisioningActorSource.Actor(14, true, true)));
        assertThatThrownBy(() -> provisioning.retry(accountTask)).isInstanceOfSatisfying(ProvisioningException.class,
            e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.ACTION_NOT_PERMITTED));

        when(actors.current()).thenReturn(Optional.of(new ProvisioningActorSource.Actor(2, true, false)));
        assertThatThrownBy(() -> provisioning.release(accountTask)).isInstanceOfSatisfying(ProvisioningException.class,
            e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.TASK_NOT_RETRYABLE));
        assertThat(provisioning.retry(accountTask).state().name()).isEqualTo("PENDING");
        assertThat(auditCount("directory :: job-retry")).isEqualTo(1);
        assertThat(auditPayloads("directory :: job-retry")).contains("\"taskId\"", "\"outcome\": \"SUCCESS\"").doesNotContain("@");
    }

    @Test
    void enablingAndAdoptingNeedThePermissionAndNotBeingTheSubject() {
        when(actors.current()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> enqueue(new ProvisioningRequest.EnableAccount(new AccountSubject.Member(15), 1)))
            .isInstanceOfSatisfying(ProvisioningException.class, e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.ACTION_NOT_PERMITTED));

        when(actors.current()).thenReturn(Optional.of(new ProvisioningActorSource.Actor(15, true, true)));
        assertThatThrownBy(() -> enqueue(new ProvisioningRequest.AdoptMemberAccount(15)))
            .isInstanceOfSatisfying(ProvisioningException.class, e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.ACTION_NOT_PERMITTED));

        when(actors.current()).thenReturn(Optional.of(new ProvisioningActorSource.Actor(16, false, true)));
        assertThatThrownBy(() -> enqueue(new ProvisioningRequest.AdoptMemberAccount(15)))
            .isInstanceOfSatisfying(ProvisioningException.class, e -> assertThat(e.code()).isEqualTo(ProvisioningErrorCode.ACTION_NOT_PERMITTED));

        when(actors.current()).thenReturn(Optional.of(new ProvisioningActorSource.Actor(16, true, false)));
        assertThat(enqueue(new ProvisioningRequest.AdoptMemberAccount(15))).isPositive();
    }

    @Test
    void aNewerOppositeIntentSupersedesAnOlderOneThatHasNotStarted() {
        AccountSubject member = new AccountSubject.Member(17);
        long disable = enqueue(new ProvisioningRequest.DisableAccount(member, 1));
        long enable = enqueue(new ProvisioningRequest.EnableAccount(member, 2));

        assertThat(taskState(disable)).isEqualTo("SUPERSEDED");
        assertThat(taskState(enable)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT superseded_by FROM directory_provisioning_tasks WHERE id = ?", Long.class, disable)).isEqualTo(enable);
    }

    @Test
    void tasksOfOneSubjectRunStrictlyInOrder() {
        GRAPH.stubFor(get(urlPathMatching(upnPath("100018@" + DOMAIN))).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "60")));
        long create = enqueue(new ProvisioningRequest.CreateMemberAccount(18));
        long disable = enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Member(18), 1));

        assertThat(worker.runOnce()).isEqualTo(1);
        makeDue(create);
        jdbc.update("UPDATE directory_provisioning_tasks SET next_attempt_at = now() - interval '1 second' WHERE id = ?", disable);

        // D6: after the 429 this replica claims nothing for the Retry-After, so the disable cannot overtake the create
        assertThat(worker.runOnce()).isZero();

        assertThat(taskAttempts(create)).isEqualTo(1);
        assertThat(taskState(disable)).isEqualTo("PENDING");
        assertThat(taskAttempts(disable)).isZero();
    }

    // --------------------------------------------------------------------------------------------- the guard

    private void guardCase(String upn, String employeeId, String userType, String objectId, int member, String number, ProvisioningErrorCode expected) {
        long account = registerAccount(new AccountSubject.Member(member), "MEMBER", "CREATED", number, objectId, "ACTIVE");
        stubUser(objectId, userJson(objectId, upn, employeeId, false, userType, NOW_ISO));
        GRAPH.stubFor(patch(urlPathEqualTo("/v1.0/users/" + objectId)).willReturn(aResponse().withStatus(204)));
        long task = enqueue(new ProvisioningRequest.EnableAccount(new AccountSubject.Member(member), 1));

        worker.runOnce();

        assertThat(account).isPositive();
        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo(expected.name());
        assertThat(count("PATCH", "/v1.0/users/")).isZero();
    }

    @Test
    void theGuardRefusesEveryObjectThatIsNotWhatTheRegistrySays() {
        guardCase("jan.kowalski@" + DOMAIN, null, "Member", oid(21), 21, "100021", ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        guardCase("100022@" + DOMAIN, "100999", "Member", oid(22), 22, "100022", ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        guardCase("100023@" + DOMAIN, "100023", "Guest", oid(23), 23, "100023", ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        guardCase("100024@" + DOMAIN, "100024", "Member", PROTECTED_OID, 24, "100024", ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        guardCase("955555@" + DOMAIN, "955555", "Member", oid(25), 25, "955555", ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        guardCase("100026@other.example", "100026", "Member", oid(26), 26, "100026", ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
        guardCase("100099@" + DOMAIN, "100099", "Member", oid(27), 27, "100027", ProvisioningErrorCode.ACCOUNT_NOT_MANAGED);
    }

    @Test
    void theGuardLetsAManagedAccountThrough() {
        String objectId = oid(28);
        registerAccount(new AccountSubject.Member(28), "MEMBER", "CREATED", "100028", objectId, "DISABLED");
        stubUser(objectId, userJson(objectId, "100028@" + DOMAIN, "100028", false, "Member", NOW_ISO));
        GRAPH.stubFor(patch(urlPathEqualTo("/v1.0/users/" + objectId)).willReturn(aResponse().withStatus(204)));
        long task = enqueue(new ProvisioningRequest.EnableAccount(new AccountSubject.Member(28), 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        GRAPH.verify(WireMock.patchRequestedFor(urlPathEqualTo("/v1.0/users/" + objectId)).withRequestBody(containing("\"accountEnabled\":true")));
        assertThat(provisioning.accountState(new AccountSubject.Member(28)).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anAccountOutsideTheRegistryIsNeverWrittenTo() {
        long task = enqueue(new ProvisioningRequest.EnableAccount(new AccountSubject.Member(29), 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("ACCOUNT_NOT_REGISTERED");
        assertThat(graphRequests()).isEmpty();
    }

    @Test
    void anExistingForeignObjectWithTheSameNameIsAConflictAndNothingIsCreated() {
        String upn = "100030@" + DOMAIN;
        stubUser(oid(30), userJson(oid(30), upn, "100030", true, "Member", "2020-01-01T00:00:00Z"));
        GRAPH.stubFor(get(urlPathMatching(upnPath(upn))).willReturn(okJson(userJson(oid(30), upn, "100030", true, "Member", "2020-01-01T00:00:00Z"))));
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(30));

        worker.runOnce();

        assertThat(taskError(task)).isEqualTo("UPN_CONFLICT");
        assertThat(count("POST", "/members")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_accounts", Integer.class)).isZero();
    }

    @Test
    void aCreationWhoseAnswerWasLostIsRecognisedAndNotRepeated() {
        String upn = "100031@" + DOMAIN;
        stubEmployeeIdHolders();
        String created = Instant.now().plusSeconds(5).toString();
        GRAPH.stubFor(get(urlPathMatching(upnPath(upn))).willReturn(okJson(userJson(oid(31), upn, "100031", true, "Member", created))));
        stubUser(oid(31), userJson(oid(31), upn, "100031", true, "Member", created));
        stubAssignLicense(oid(31));
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(31));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        assertThat(count("POST", "/members")).isZero();
        assertThat(provisioning.accountState(new AccountSubject.Member(31)).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void aMemberNumberHeldByAnotherObjectStopsTheCreation() {
        stubUserAbsent("100032@" + DOMAIN);
        stubEmployeeIdHolders(oid(999));
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(32));

        worker.runOnce();

        assertThat(taskError(task)).isEqualTo("MEMBER_NUMBER_TAKEN_IN_ENTRA");
        assertThat(count("POST", "/members")).isZero();
    }

    // --------------------------------------------------------------------------------------------- trial and conversion

    @Test
    void aTrialAccountIsCreatedWithoutEmployeeIdAndWithTheTrialLicence() {
        String upn = "990019@" + DOMAIN;
        stubUserAbsent(upn);
        stubCreate(oid(1019));
        stubUser(oid(1019), userJson(oid(1019), upn, null, true, "Member", NOW_ISO));
        stubAssignLicense(oid(1019));
        long task = enqueue(new ProvisioningRequest.CreateTrialAccount(19));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        String body = graphRequests().stream().filter(r -> r.getUrl().endsWith("/members")).findFirst().orElseThrow().getBodyAsString();
        assertThat(body).doesNotContain("employeeId");
        GRAPH.verify(postRequestedFor(urlPathEqualTo("/v1.0/users/" + oid(1019) + "/assignLicense")).withRequestBody(containing(SKU_TRIAL)));
        assertThat(provisioning.accountState(new AccountSubject.Candidate(19)).kind()).isEqualTo(AccountState.AccountKind.TRIAL);
        assertThat(count("GET", "$filter")).isZero();
    }

    @Test
    void theConversionWritesEmployeeIdLastAndMovesTheAccountToTheMember() {
        String objectId = oid(2040);
        registerAccount(new AccountSubject.Candidate(40), "TRIAL", "CREATED", "990040", objectId, "ACTIVE");
        stubUser(objectId, userJson(objectId, "990040@" + DOMAIN, null, true, "Member", NOW_ISO, SKU_TRIAL));
        stubEmployeeIdHolders();
        stubUserAbsent("100040@" + DOMAIN);
        GRAPH.stubFor(patch(urlPathEqualTo("/v1.0/users/" + objectId)).willReturn(aResponse().withStatus(204)));
        stubAssignLicense(objectId);
        long task = enqueue(new ProvisioningRequest.ConvertTrialToMember(40, 40));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        var writes = graphRequests().stream().filter(r -> !r.getMethod().getName().equals("GET")).toList();
        assertThat(writes).hasSize(3);
        assertThat(writes.get(0).getMethod().getName()).isEqualTo("PATCH");
        assertThat(writes.get(0).getBodyAsString()).contains("\"userPrincipalName\":\"100040@" + DOMAIN + "\"").doesNotContain("employeeId");
        assertThat(writes.get(1).getUrl()).endsWith("/assignLicense");
        assertThat(writes.get(1).getBodyAsString()).contains(SKU_MEMBER, SKU_TRIAL);
        assertThat(writes.get(2).getBodyAsString()).isEqualTo("{\"employeeId\":\"100040\"}");

        assertThat(jdbc.queryForMap("SELECT kind, member_id, case_id, account_number FROM directory_accounts"))
            .containsEntry("kind", "MEMBER").containsEntry("member_id", 40L).containsEntry("case_id", null).containsEntry("account_number", "100040");
        assertThat(provisioning.accountState(new AccountSubject.Candidate(40)).status()).isEqualTo(AccountStatus.NONE);
        assertThat(provisioning.accountState(new AccountSubject.Member(40)).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void aConversionIsResumedAfterTheNameWasChanged() {
        String objectId = oid(2041);
        registerAccount(new AccountSubject.Candidate(41), "TRIAL", "CREATED", "990041", objectId, "ACTIVE");
        stubUser(objectId, userJson(objectId, "100041@" + DOMAIN, null, true, "Member", NOW_ISO, SKU_MEMBER));
        GRAPH.stubFor(patch(urlPathEqualTo("/v1.0/users/" + objectId)).willReturn(aResponse().withStatus(204)));
        long task = enqueue(new ProvisioningRequest.ConvertTrialToMember(41, 41));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        assertThat(count("PATCH", objectId)).isEqualTo(1);
        assertThat(count("POST", "assignLicense")).isZero();
        GRAPH.verify(WireMock.patchRequestedFor(urlPathEqualTo("/v1.0/users/" + objectId)).withRequestBody(containing("\"employeeId\":\"100041\"")));
    }

    @Test
    void aConversionIsRefusedWhenTheMemberNumberBelongsToAnotherObject() {
        String objectId = oid(2042);
        registerAccount(new AccountSubject.Candidate(42), "TRIAL", "CREATED", "990042", objectId, "ACTIVE");
        stubUser(objectId, userJson(objectId, "990042@" + DOMAIN, null, true, "Member", NOW_ISO));
        stubEmployeeIdHolders(oid(7777));
        long task = enqueue(new ProvisioningRequest.ConvertTrialToMember(42, 42));

        worker.runOnce();

        assertThat(taskError(task)).isEqualTo("MEMBER_NUMBER_TAKEN_IN_ENTRA");
        assertThat(graphRequests().stream().filter(r -> !r.getMethod().getName().equals("GET"))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT kind FROM directory_accounts", String.class)).isEqualTo("TRIAL");
    }

    @Test
    void adoptingNeedsAnExistingObjectWhoseEmployeeIdIsTheNumber() {
        String upn = "100043@" + DOMAIN;
        GRAPH.stubFor(get(urlPathMatching(upnPath(upn))).willReturn(okJson(userJson(oid(43), upn, "100043", true, "Member", "2020-01-01T00:00:00Z"))));
        long task = enqueue(new ProvisioningRequest.AdoptMemberAccount(43));
        worker.runOnce();
        assertThat(taskState(task)).isEqualTo("DONE");
        assertThat(provisioning.accountState(new AccountSubject.Member(43)).origin()).isEqualTo(AccountState.AccountOrigin.ADOPTED);

        String other = "100044@" + DOMAIN;
        GRAPH.stubFor(get(urlPathMatching(upnPath(other))).willReturn(okJson(userJson(oid(44), other, "100999", true, "Member", "2020-01-01T00:00:00Z"))));
        long refused = enqueue(new ProvisioningRequest.AdoptMemberAccount(44));
        worker.runOnce();
        assertThat(taskError(refused)).isEqualTo("ADOPTION_NO_MATCH");

        stubUserAbsent("100045@" + DOMAIN);
        long missing = enqueue(new ProvisioningRequest.AdoptMemberAccount(45));
        worker.runOnce();
        assertThat(taskError(missing)).isEqualTo("ADOPTION_NO_MATCH");
        assertThat(graphRequests().stream().filter(r -> !r.getMethod().getName().equals("GET"))).isEmpty();
    }

    // --------------------------------------------------------------------------------------------- disable, delete

    @Test
    void disablingWritesOnceEndsTheSessionsAndReplaysAsAlreadyDone() {
        String objectId = oid(50);
        registerAccount(new AccountSubject.Member(50), "MEMBER", "CREATED", "100050", objectId, "ACTIVE");
        stubUser(objectId, userJson(objectId, "100050@" + DOMAIN, "100050", true, "Member", NOW_ISO));
        GRAPH.stubFor(patch(urlPathEqualTo("/v1.0/users/" + objectId)).willReturn(aResponse().withStatus(204)));
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/users/" + objectId + "/revokeSignInSessions")).willReturn(okJson("{\"value\":true}")));
        long task = enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Member(50), 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        GRAPH.verify(WireMock.patchRequestedFor(urlPathEqualTo("/v1.0/users/" + objectId)).withRequestBody(containing("\"accountEnabled\":false")));
        assertThat(count("POST", "revokeSignInSessions")).isEqualTo(1);
        assertThat(provisioning.accountState(new AccountSubject.Member(50)).status()).isEqualTo(AccountStatus.DISABLED);
        assertThat(auditPayloads("directory :: account-disable")).contains("\"outcome\": \"SUCCESS\"");
    }

    @Test
    void aTrialAccountIsDeletedOnlyAfterSevenDaysDisabled() {
        String objectId = oid(2051);
        registerAccount(new AccountSubject.Candidate(51), "TRIAL", "CREATED", "990051", objectId, "DISABLED");
        stubUser(objectId, userJson(objectId, "990051@" + DOMAIN, null, false, "Member", NOW_ISO));
        GRAPH.stubFor(delete(urlPathEqualTo("/v1.0/users/" + objectId)).willReturn(aResponse().withStatus(204)));
        long task = enqueue(new ProvisioningRequest.DeleteTrialAccount(51));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("PENDING");
        assertThat(taskAttempts(task)).isZero();
        assertThat(graphRequests()).isEmpty();

        jdbc.update("UPDATE directory_accounts SET updated_at = now() - interval '8 days'");
        makeDue(task);
        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        GRAPH.verify(deleteRequestedFor(urlPathEqualTo("/v1.0/users/" + objectId)));
        assertThat(provisioning.accountState(new AccountSubject.Candidate(51)).status()).isEqualTo(AccountStatus.NONE);
    }

    @Test
    void aMemberAccountIsNeverDeleted() {
        registerAccount(new AccountSubject.Candidate(52), "TRIAL", "CREATED", "990052", oid(2052), "DISABLED");
        jdbc.update("UPDATE directory_accounts SET kind = 'MEMBER', member_id = 52, case_id = NULL, account_number = '100052'");
        long task = enqueue(new ProvisioningRequest.DeleteTrialAccount(52));
        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(graphRequests()).isEmpty();
    }

    // --------------------------------------------------------------------------------------------- groups

    private String memberInGroup(int member) {
        String objectId = oid(member);
        registerAccount(new AccountSubject.Member(member), "MEMBER", "CREATED", String.format("%06d", 100_000 + member), objectId, "ACTIVE");
        stubUser(objectId, userJson(objectId, String.format("%06d@" + DOMAIN, 100_000 + member), String.format("%06d", 100_000 + member), true, "Member", NOW_ISO));
        when(mappings.mapping(anyLong())).thenReturn(Optional.of(new GroupMappingSource.GroupMapping(GROUP, true)));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\",\"isAssignableToRole\":false,\"membershipRule\":null}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_GROUPS + "/members/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\"}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP + "/owners")).willReturn(okJson("{\"value\":[]}")));
        return objectId;
    }

    @Test
    void aGroupMemberIsRemovedOnlyWithTheRefSuffix() {
        String objectId = memberInGroup(60);
        GRAPH.stubFor(delete(urlEqualTo("/v1.0/groups/" + GROUP + "/members/" + objectId + "/$ref")).willReturn(aResponse().withStatus(204)));
        long task = enqueue(new ProvisioningRequest.RemoveGroupMember(60, 1, 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        GRAPH.verify(1, deleteRequestedFor(urlEqualTo("/v1.0/groups/" + GROUP + "/members/" + objectId + "/$ref")));
        assertThat(graphRequests().stream().filter(r -> r.getMethod().getName().equals("DELETE")).map(r -> r.getUrl())).allMatch(u -> u.endsWith("/$ref"));
        assertThatThrownBy(() -> GraphDirectoryClient.removeMemberPath("not-a-uuid", objectId)).isInstanceOf(IllegalArgumentException.class);
        assertThat(GraphDirectoryClient.removeMemberPath(GROUP, objectId)).endsWith("/members/" + objectId + "/$ref");
    }

    @Test
    void anOwnerOfTheGroupIsNeverRemoved() {
        String objectId = memberInGroup(61);
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP + "/owners")).willReturn(okJson("{\"value\":[{\"id\":\"" + objectId + "\"}]}")));
        long task = enqueue(new ProvisioningRequest.RemoveGroupMember(61, 1, 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(count("DELETE", "/members/")).isZero();
    }

    @Test
    void removingSomebodyWhoseAccountThePanelDoesNotManageIsANoOp() {
        when(mappings.mapping(anyLong())).thenReturn(Optional.of(new GroupMappingSource.GroupMapping(GROUP, true)));
        long task = enqueue(new ProvisioningRequest.RemoveGroupMember(62, 1, 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        assertThat(graphRequests()).isEmpty();
    }

    @Test
    void addingToAGroupChecksTheGroupAndTreatsAnExistingMembershipAsDone() {
        String objectId = memberInGroup(63);
        GRAPH.stubFor(post(urlEqualTo("/v1.0/groups/" + GROUP + "/members/$ref")).willReturn(aResponse().withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":{\"code\":\"Request_BadRequest\",\"message\":\"One or more added object references already exist for the following modified properties: 'members'.\"}}")));
        long task = enqueue(new ProvisioningRequest.AddGroupMember(63, 1, 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        GRAPH.verify(1, postRequestedFor(urlEqualTo("/v1.0/groups/" + GROUP + "/members/$ref")).withRequestBody(containing(objectId)));
        assertThat(auditPayloads("directory :: group-add")).contains("ALREADY_DONE");
    }

    @Test
    void aGroupOutsideTheAdministrativeUnitOrRoleAssignableIsNotTouched() {
        memberInGroup(64);
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_GROUPS + "/members/" + GROUP)).willReturn(aResponse().withStatus(404)));
        long outside = enqueue(new ProvisioningRequest.AddGroupMember(64, 1, 1));
        worker.runOnce();
        assertThat(taskError(outside)).isEqualTo("GROUP_NOT_MANAGEABLE");

        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\",\"isAssignableToRole\":true}")));
        long roleGroup = enqueue(new ProvisioningRequest.AddGroupMember(64, 1, 2));
        worker.runOnce();
        assertThat(taskError(roleGroup)).isEqualTo("GROUP_NOT_MANAGEABLE");

        assertThat(count("POST", "/members/$ref")).isZero();
    }

    @Test
    void anAccountWithATrialNumberNeverJoinsAGroup() {
        memberInGroup(66);
        registerAccount(new AccountSubject.Member(65), "MEMBER", "CREATED", "990065", oid(65), "ACTIVE");
        stubUser(oid(65), userJson(oid(65), "990065@" + DOMAIN, "990065", true, "Member", NOW_ISO));
        long task = enqueue(new ProvisioningRequest.AddGroupMember(65, 1, 1));
        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("ACCOUNT_NOT_MANAGED");
        assertThat(count("POST", "/members/$ref")).isZero();
    }

    // --------------------------------------------------------------------------------------------- the audit and the logs

    @Test
    void theAuditRowHasIdsAndAnOutcomeOnly() {
        stubCreateFlow(70, oid(70));
        enqueue(new ProvisioningRequest.CreateMemberAccount(70));
        worker.runOnce();

        String payload = auditPayloads("directory :: account-create");
        assertThat(payload).contains("\"taskId\"", "\"subjectId\": 70", "\"outcome\": \"SUCCESS\"");
        assertThat(payload).doesNotContain("@", "100070", oid(70), "Given", "Surname");
    }

    @Test
    void theOutboxAndTheRegistryHoldOnlyTechnicalColumns() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'directory_provisioning_tasks'", String.class))
            .containsExactlyInAnyOrder("id", "created_at", "created_by_member_id", "operation", "subject_kind", "subject_id", "related_case_id",
                "mapping_id", "occurrence_id", "idempotency_key", "state", "attempts", "next_attempt_at", "ready_since", "released",
                "released_by_member_id", "lease_owner", "lease_until", "started_at", "finished_at", "last_error_code", "superseded_by");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'directory_accounts'", String.class))
            .containsExactlyInAnyOrder("id", "created_at", "updated_at", "member_id", "case_id", "kind", "origin", "account_number", "entra_object_id", "status");
    }

    @Test
    void aGraphErrorThatQuotesAPersonDoesNotReachTheLogOrTheDatabase(org.springframework.boot.test.system.CapturedOutput output) {
        // D10 (SEC-51): the shared context runs org.springframework.web.client at TRACE, and the Spring HTTP client then
        // prints the request body (the userPrincipalName of the account being created) and the URL (users/{upn}).
        // Production runs at WARN (application.yml), so this test lowers the level to WARN for its duration only.
        LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
        String httpClientLogger = "org.springframework.web.client";
        LogLevel previousLevel = logging.getLoggerConfiguration(httpClientLogger).getEffectiveLevel();
        logging.setLogLevel(httpClientLogger, LogLevel.WARN);
        try {
            String quoted = "jan.kowalski@" + DOMAIN;
            stubUserAbsent("100071@" + DOMAIN);
            stubEmployeeIdHolders();
            GRAPH.stubFor(post(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_MEMBERS + "/members")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"Request_Bad Request@x\",\"message\":\"The value " + quoted + " is invalid\"}}")));
            long task = enqueue(new ProvisioningRequest.CreateMemberAccount(71));

            worker.runOnce();

            assertThat(taskError(task)).isEqualTo("GRAPH_REJECTED");
            assertThat(output.getAll()).doesNotContain(quoted, "jan.kowalski");
            // D10: the UPN path segment (users/{upn}) and the UPN in the created-user body must not reach the log.
            assertThat(output.getAll()).doesNotContain("100071@");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_provisioning_tasks WHERE last_error_code LIKE '%jan%'", Integer.class)).isZero();
        } finally {
            logging.setLogLevel(httpClientLogger, previousLevel);
        }
    }
}
