package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountSubject;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningErrorCode;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.GroupMappingSource;
import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.ProvisioningRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Review of B2 (security review 2026-11-28, W1-W7 and D1-D15): lease renewal, visible holds, audit of the asker, failure
 * modes of Graph answers, the guard for trial accounts, the SEC-24 preconditions, the 403 window, and the link handling.
 * Runs against the fake Graph only; the dummy tenant and token of {@link ProvisioningTestSupport} are used.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProvisioningReviewB2Test extends EnabledProvisioningTest {
    private static final String NOW_ISO = Instant.now().toString();
    private static final String GROUP = "55555555-5555-5555-5555-555555555555";

    @Autowired
    ProvisioningTasks tasks;

    @Autowired
    ProvisioningGate gate;

    @Autowired
    ProvisioningProperties properties;

    // ------------------------------------------------------------------------------------------------- helpers

    private static String upnPath(String upn) {
        return "/v1.0/users/" + upn.replace("@", "(@|%40)").replace(".", "\\.");
    }

    private static String numberOf(int member) {
        return String.format("%06d", 100_000 + member);
    }

    private static void stubAbsentUpn(String upn) {
        GRAPH.stubFor(get(urlPathMatching(upnPath(upn))).willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
            .withBody("{\"error\":{\"code\":\"Request_ResourceNotFound\"}}")));
    }

    private static void stubNoEmployeeIdHolders() {
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users")).withQueryParam("$filter", containing("employeeId eq"))
            .willReturn(okJson("{\"value\":[]}")));
    }

    private static void stubUserBody(String oid, String body) {
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/users/" + oid)).willReturn(okJson(body)));
    }

    private static void stubLicence(String oid, int status) {
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/users/" + oid + "/assignLicense")).willReturn(aResponse().withStatus(status)
            .withHeader("Content-Type", "application/json").withBody(status == 200 ? "{}" : "{\"error\":{\"code\":\"Forbidden\"}}")));
    }

    /** Everything a member creation needs from Graph; the licence answer decides the outcome of the last step. */
    private String stubMemberCreation(int member, int licenceStatus) {
        String oid = oid(member);
        String number = numberOf(member);
        stubAbsentUpn(number + "@" + DOMAIN);
        stubNoEmployeeIdHolders();
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_MEMBERS + "/members"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"id\":\"" + oid + "\"}")));
        stubUserBody(oid, userJson(oid, number + "@" + DOMAIN, number, true, "Member", NOW_ISO));
        stubLicence(oid, licenceStatus);
        return oid;
    }

    private String memberInGroup(int member) {
        String oid = oid(member);
        String number = numberOf(member);
        registerAccount(new AccountSubject.Member(member), "MEMBER", "CREATED", number, oid, "ACTIVE");
        stubUserBody(oid, userJson(oid, number + "@" + DOMAIN, number, true, "Member", NOW_ISO));
        when(mappings.mapping(anyLong())).thenReturn(Optional.of(new GroupMappingSource.GroupMapping(GROUP, true)));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\",\"isAssignableToRole\":false,\"membershipRule\":null}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_GROUPS + "/members/" + GROUP)).willReturn(okJson("{\"id\":\"" + GROUP + "\"}")));
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP + "/owners")).willReturn(okJson("{\"value\":[]}")));
        return oid;
    }

    /** The owner id the worker writes into its leases (private to the worker; read for the test only). */
    private String workerOwner() throws ReflectiveOperationException {
        Field field = ProvisioningWorker.class.getDeclaredField("owner");
        field.setAccessible(true);
        return (String) field.get(worker);
    }

    /** A trial account with the given registry status, disabled long enough ago, and the given Graph state of the object. */
    private long deletionOf(long caseId, String status, boolean graphEnabled, String employeeId) {
        String oid = oid((int) caseId);
        String number = String.format("%06d", 990_000 + caseId);
        long account = registerAccount(new AccountSubject.Candidate(caseId), "TRIAL", "CREATED", number, oid, status);
        jdbc.update("UPDATE directory_accounts SET updated_at = now() - interval '30 days' WHERE id = ?", account);
        stubUserBody(oid, userJson(oid, number + "@" + DOMAIN, employeeId, graphEnabled, "Member", NOW_ISO));

        long task = enqueue(new ProvisioningRequest.DeleteTrialAccount(caseId));
        worker.runOnce();

        GRAPH.verify(0, deleteRequestedFor(urlPathEqualTo("/v1.0/users/" + oid)));
        return task;
    }

    // ------------------------------------------------------------------------------------------------- W1

    /** W1: a task whose lease ran out inside a batch is taken over by another replica and must not run twice. */
    @Test
    void aTaskWhoseLeaseExpiredInsideABatchIsNotRunByTheSlowWorker() throws ReflectiveOperationException {
        stubMemberCreation(5, 200);
        stubMemberCreation(6, 200);
        long first = enqueue(new ProvisioningRequest.CreateMemberAccount(5));
        long second = enqueue(new ProvisioningRequest.CreateMemberAccount(6));

        List<ProvisioningTasks.Task> batch = tasks.claim(workerOwner(), Instant.now(), ProvisioningWorker.LEASE, 10, properties);
        assertThat(batch).hasSize(2);

        jdbc.update("UPDATE directory_provisioning_tasks SET lease_until = now() - interval '1 minute' WHERE id = ?", second);
        List<ProvisioningTasks.Task> taken = tasks.claim("other-replica", Instant.now(), ProvisioningWorker.LEASE, 10, properties);
        assertThat(taken).extracting(ProvisioningTasks.Task::id).containsExactly(second);

        worker.runClaimed(batch);

        assertThat(taskState(first)).isEqualTo("DONE");
        assertThat(count("POST", "/members")).isEqualTo(1);
        GRAPH.verify(0, postRequestedFor(urlPathEqualTo("/v1.0/directory/administrativeUnits/" + AU_MEMBERS + "/members"))
            .withRequestBody(containing(numberOf(6) + "@")));
        assertThat(taskState(second)).isEqualTo("RUNNING");
    }

    // ------------------------------------------------------------------------------------------------- W2

    /** W2: a held task is visible in the log with its id and its hold code, and nothing else. */
    @Test
    void aHeldTaskLeavesAWarningWithItsIdAndCodeOnly(CapturedOutput output) {
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(40));
        jdbc.update("UPDATE directory_provisioning_tasks SET ready_since = now() - interval '30 days' WHERE id = ?", task);

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("HELD");
        assertThat(output.getOut()).contains("provisioning task " + task + " held: HOLD_STALE");
        assertThat(output.getOut()).doesNotContain(numberOf(40));
    }

    // ------------------------------------------------------------------------------------------------- W4

    /** W4: the person who asked for an ADOPT or an ENABLE is written to the audit at the request, with the ids only. */
    @Test
    void theRequestsToAdoptOrEnableAndTheSupersessionAreAudited() {
        enqueue(new ProvisioningRequest.AdoptMemberAccount(16));
        assertThat(auditPayloads("directory :: account-adopt-request")).contains("ENQUEUED").doesNotContain("@");

        enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Member(17), 1));
        enqueue(new ProvisioningRequest.EnableAccount(new AccountSubject.Member(17), 2));

        assertThat(auditPayloads("directory :: account-enable-request")).contains("ENQUEUED");
        assertThat(auditPayloads("directory :: job-supersede")).contains("SUPERSEDED");
    }

    // ------------------------------------------------------------------------------------------------- W6

    /** W6: a disable of an account outside the registry is a visible failure, never a silent skip. */
    @Test
    void disablingAnAccountOutsideTheRegistryFailsVisibly() {
        long task = enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Member(29), 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("ACCOUNT_NOT_REGISTERED");
        assertThat(graphRequests()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- W7

    /** W7 (SEC-3c): a trial account that Graph shows with an employeeId is refused by the guard. */
    @Test
    void aTrialAccountWithAnEmployeeIdIsNotManaged() {
        String oid = oid(3);
        registerAccount(new AccountSubject.Candidate(3), "TRIAL", "CREATED", "990003", oid, "ACTIVE");
        stubUserBody(oid, userJson(oid, "990003@" + DOMAIN, "990003", true, "Member", NOW_ISO));

        long task = enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Candidate(3), 1));
        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("ACCOUNT_NOT_MANAGED");
        assertThat(count("PATCH", "/v1.0/users/" + oid)).isZero();
    }

    /** W7 (SEC-24): an active trial account is never deleted. */
    @Test
    void aTrialAccountThatIsNotDisabledIsNotDeleted() {
        long task = deletionOf(4, "ACTIVE", false, null);

        assertThat(taskError(task)).isEqualTo("PRECONDITION_FAILED");
    }

    /** W7 (SEC-24): a registry-disabled trial account that Graph shows as enabled is not deleted. */
    @Test
    void aTrialAccountEnabledInEntraIsNotDeleted() {
        long task = deletionOf(5, "DISABLED", true, null);

        assertThat(taskError(task)).isEqualTo("PRECONDITION_FAILED");
    }

    /** W7 (SEC-24): a trial account that has received an employeeId in Entra is not deleted. */
    @Test
    void aTrialAccountWithAnEmployeeIdInEntraIsNotDeleted() {
        long task = deletionOf(6, "DISABLED", false, "990006");

        assertThat(taskError(task)).isEqualTo("ACCOUNT_NOT_MANAGED");
    }

    /**
     * W7 (SEC-33): a 403 on the licence is retried only while the account is inside the grace window, and final after it.
     */
    @Test
    void aForbiddenLicenceIsRetriedOnlyInsideTheGraceWindow() {
        String oid = stubMemberCreation(70, 403);
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(70));

        worker.runOnce();
        assertThat(taskState(task)).isEqualTo("PENDING");
        assertThat(taskError(task)).isEqualTo("GRAPH_FORBIDDEN");

        jdbc.update("UPDATE directory_accounts SET created_at = now() - interval '30 minutes' WHERE entra_object_id = ?::uuid", oid);
        makeDue(task);

        worker.runOnce();
        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("GRAPH_FORBIDDEN");
    }

    /** W7 (O4): a 400 about a group membership that is not "already exists" is a refusal, not a success. */
    @Test
    void aGroupRefusalThatIsNotAnExistingMembershipFailsVisibly() {
        String objectId = memberInGroup(63);
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/groups/" + GROUP + "/members/$ref")).willReturn(aResponse().withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":{\"code\":\"Request_BadRequest\",\"message\":\"Invalid value for the reference.\"}}")));
        long task = enqueue(new ProvisioningRequest.AddGroupMember(63, 1, 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("GRAPH_REJECTED");
        assertThat(auditPayloads("directory :: group-add")).doesNotContain("ALREADY_DONE");
        GRAPH.verify(1, postRequestedFor(urlPathEqualTo("/v1.0/groups/" + GROUP + "/members/$ref")).withRequestBody(containing(objectId)));
    }

    /**
     * W7 (SEC-6/SEC-43) and D15: a paging link of the group-owner lookup (the one the worker follows) that does not lead to
     * Graph, or is malformed, is refused and never followed.
     */
    @ParameterizedTest
    @ValueSource(strings = {"prefix", "userinfo", "protocol-relative", "malformed"})
    void aNextLinkOutsideGraphIsRefusedAndNeverFollowed(String variant) {
        String base = GRAPH.baseUrl();
        String link = switch (variant) {
            case "prefix" -> base + ".evil.example/v1.0/groups?$skiptoken=x";
            case "userinfo" -> base + "@evil.example/v1.0/groups";
            case "protocol-relative" -> "//evil.example/v1.0/groups";
            default -> base + "/v1.0/groups?$skiptoken=%zz";
        };
        String objectId = memberInGroup(81);
        GRAPH.stubFor(get(urlPathEqualTo("/v1.0/groups/" + GROUP + "/owners"))
            .willReturn(okJson("{\"value\":[],\"@odata.nextLink\":\"" + link + "\"}")));
        long task = enqueue(new ProvisioningRequest.RemoveGroupMember(81, 1, 1));

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("GRAPH_REJECTED");
        assertThat(graphRequests().stream().map(r -> r.getUrl()).filter(url -> url.contains("evil"))).isEmpty();
        assertThat(count("DELETE", objectId)).isZero();
    }

    // ------------------------------------------------------------------------------------------------- D1

    /** D1: the configured forbidden list adds to the built-in list; the self-empowering permissions are always there. */
    @Test
    void theForbiddenPermissionsAreAddedToTheBuiltInList() {
        assertThat(properties.forbiddenPermissions()).contains("User.ReadWrite.All", "AppRoleAssignment.ReadWrite.All",
            "UserAuthenticationMethod.ReadWrite.All", "Application.ReadWrite.All");
        assertThat(gate.forbiddenRoles(jwt("AppRoleAssignment.ReadWrite.All"))).containsExactly("AppRoleAssignment.ReadWrite.All");
        assertThat(gate.forbiddenRoles(jwt("Mail.Send", "UserAuthMethod-TAP.ReadWrite.All"))).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------- D3

    /** D3: an object made by hand under the member's UPN, without the member's employeeId, is never taken over. */
    @Test
    void anObjectMadeByHandUnderTheMemberUpnIsNotTakenOver() {
        String oid = oid(90);
        String upn = numberOf(90) + "@" + DOMAIN;
        GRAPH.stubFor(get(urlPathMatching(upnPath(upn))).willReturn(okJson(userJson(oid, upn, null, true, "Member", NOW_ISO))));

        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(90));
        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("UPN_CONFLICT");
    }

    // ------------------------------------------------------------------------------------------------- D4

    /** D4: a replayed disable (already disabled in Entra) still revokes the sessions and writes nothing else. */
    @Test
    void aReplayedDisableStillRevokesTheSessions() {
        String oid = oid(21);
        registerAccount(new AccountSubject.Member(21), "MEMBER", "CREATED", numberOf(21), oid, "DISABLED");
        stubUserBody(oid, userJson(oid, numberOf(21) + "@" + DOMAIN, numberOf(21), false, "Member", NOW_ISO, SKU_MEMBER));
        GRAPH.stubFor(post(urlPathEqualTo("/v1.0/users/" + oid + "/revokeSignInSessions")).willReturn(okJson("{\"value\":true}")));

        long task = enqueue(new ProvisioningRequest.DisableAccount(new AccountSubject.Member(21), 1));
        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("DONE");
        assertThat(count("PATCH", "/v1.0/users/" + oid)).isZero();
        assertThat(count("POST", "revokeSignInSessions")).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------- D5

    /** D5: a RUNNING task with an expired lease that already used every attempt is failed, not claimed again. */
    @Test
    void aTaskThatKilledItsWorkerOnEveryAttemptEndsFailed() {
        long task = enqueue(new ProvisioningRequest.CreateMemberAccount(91));
        jdbc.update("UPDATE directory_provisioning_tasks SET state = 'RUNNING', attempts = 3, lease_owner = 'dead-worker', "
            + "lease_until = now() - interval '1 minute', started_at = now() WHERE id = ?", task);

        worker.runOnce();

        assertThat(taskState(task)).isEqualTo("FAILED");
        assertThat(taskError(task)).isEqualTo("ATTEMPTS_EXHAUSTED");
        assertThat(graphRequests()).isEmpty();
        assertThat(auditPayloads("directory :: account-create")).contains("ATTEMPTS_EXHAUSTED").doesNotContain("@");
        assertThat(events.committed).singleElement().satisfies(event -> {
            assertThat(event.taskId()).isEqualTo(task);
            assertThat(event.succeeded()).isFalse();
            assertThat(event.errorCode()).isEqualTo(ProvisioningErrorCode.ATTEMPTS_EXHAUSTED);
        });
    }

    // ------------------------------------------------------------------------------------------------- D6

    /** D6: after a 429 this replica claims and runs nothing more for the Retry-After; the rest of the batch goes back. */
    @Test
    void aThrottledReplicaGivesTheRestOfItsBatchBackWithoutAnAttempt() {
        GRAPH.stubFor(get(urlPathMatching(upnPath(numberOf(31) + "@" + DOMAIN))).willReturn(aResponse().withStatus(429)
            .withHeader("Retry-After", "30").withHeader("Content-Type", "application/json")
            .withBody("{\"error\":{\"code\":\"TooManyRequests\"}}")));
        long first = enqueue(new ProvisioningRequest.CreateMemberAccount(31));
        long second = enqueue(new ProvisioningRequest.CreateMemberAccount(32));
        long third = enqueue(new ProvisioningRequest.CreateMemberAccount(33));

        worker.runOnce();

        assertThat(taskError(first)).isEqualTo("GRAPH_THROTTLED");
        for (long task : List.of(second, third)) {
            assertThat(taskState(task)).isEqualTo("PENDING");
            assertThat(taskAttempts(task)).isZero();
        }
        assertThat(count("GET", numberOf(32))).isZero();
        assertThat(count("GET", numberOf(33))).isZero();
        assertThat(worker.runOnce()).isZero();
    }
}
