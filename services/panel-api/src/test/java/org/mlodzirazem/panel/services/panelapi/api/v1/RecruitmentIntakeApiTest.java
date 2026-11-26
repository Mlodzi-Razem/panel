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

package org.mlodzirazem.panel.services.panelapi.api.v1;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.IntakeProperties;
import org.mlodzirazem.panel.services.panelapi.recruitment.intake.RecruitmentIntakeQueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RecruitmentIntakeApiTest extends ApiTestSupport {
    private static final String FORM = "/public/recruitment-intake";

    @Autowired
    IntakeProperties properties;

    @Autowired
    RecruitmentIntakeQueueService queue;

    TMember zoMazovia;
    TMember zoSilesia;
    TMember reader;
    TMember national;
    TMember plain;

    private RestClient anon;

    @BeforeEach
    void setUp() {
        restoreDefaults();
        properties.setEnabled(true);
        properties.setMinFillSeconds(0);

        zoMazovia = linked(newMember("Ola", "Okregowa", "mazowieckie"));
        grant(zoMazovia, board("mazowieckie"), "COORDINATOR");
        zoSilesia = linked(newMember("Oskar", "Slaski", "slaskie"));
        grant(zoSilesia, board("slaskie"), "COORDINATOR");
        reader = linked(newMember("Rita", "Czytajaca", "mazowieckie"));
        grantDistrictReader(reader, "mazowieckie");
        national = linked(newMember("Krystyna", "Krajowa", "mazowieckie"));
        grant(national, body("national-board"), "SECRETARY");
        plain = linked(newMember("Pan", "Plain", "mazowieckie"));

        anon = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory())
            .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
            .build();
    }

    @AfterEach
    void restoreDefaults() {
        IntakeProperties defaults = new IntakeProperties();
        properties.setEnabled(defaults.isEnabled());
        properties.setMinFillSeconds(defaults.getMinFillSeconds());
        properties.setMaxPerHour(defaults.getMaxPerHour());
        properties.setMaxPerDay(defaults.getMaxPerDay());
        properties.setMaxPending(defaults.getMaxPending());
        properties.setMaxPerClientPerHour(defaults.getMaxPerClientPerHour());
    }

    // ---- the anonymous form -------------------------------------------------------------------------------

    @Test
    void theFormListsDistrictsAndIsOffByDefault() {
        Res open = anonymous(HttpMethod.GET, null, null);

        assertThat(open.status()).isEqualTo(200);
        assertThat(open.body().path("enabled").asBoolean()).isTrue();
        assertThat(open.body().path("districts").size()).isEqualTo(17);

        assertThat(new IntakeProperties().isEnabled()).isFalse();
        properties.setEnabled(false);

        assertThat(anonymous(HttpMethod.GET, null, null).body().path("enabled").asBoolean()).isFalse();
        Res refused = anonymous(HttpMethod.POST, valid("off@example.org"), null);
        assertThat(refused.status()).isEqualTo(404);
        assertThat(refused.code()).isEqualTo("INTAKE_DISABLED");
        assertThat(count("recruitment_intakes")).isZero();
    }

    @Test
    void aSubmissionWaitsInTheQueueAndIsNotACase(CapturedOutput output) {
        Map<String, Object> body = valid("zgloszenie.kanarek@example.org");
        body.put("firstName", "Kanarkowy");
        body.put("phone", "+48 600 700 800");

        submitted(body);

        assertThat(count("recruitment_intakes")).isOne();
        assertThat(count("recruitment_cases")).isZero();
        assertThat(jdbc.queryForObject("SELECT status || ' ' || consent_version FROM recruitment_intakes",
            String.class)).startsWith("PENDING intake-");

        // field names and codes only, in the history, the audit and the log
        String history = String.join("\n", jdbc.queryForList(
            "SELECT payload::text FROM recruitment_intake_events", String.class));
        assertThat(history).contains("firstName", "email");
        assertThat(history + auditPayloads("recruitment :: intake-received") + serverLog(output))
            .doesNotContain("kanarek", "Kanarkowy", "600 700 800");
        assertThat(auditCount("recruitment :: intake-received")).isOne();
    }

    @Test
    void theAnswerNeverTellsWhetherThePersonIsKnown(CapturedOutput output) {
        TMember member = newMember("Istniejacy", "Czlonek", "mazowieckie");
        post(zoMazovia, "/recruitment-cases", Map.of("districtBodyId", district("mazowieckie").getId(),
            "candidate", Map.of("candidateFirstName", "Ma", "candidateLastName", "Sprawe",
                "candidateEmail", "otwarta.sprawa@example.org")));
        long caseId = jdbc.queryForObject("SELECT id FROM recruitment_cases", Long.class);
        jdbc.update("INSERT INTO admission_bans (case_id, decision_signature, effective_from, effective_until, reason)"
            + " VALUES (?, 'ZK/1', current_date, current_date + 10, 'powod')", caseId);
        submitted(valid("powtorka@example.org"));

        List<String> answers = new ArrayList<>();

        for (String email : List.of("nowa.osoba@example.org", member.getDomainEmail(),
            "otwarta.sprawa@example.org", "powtorka@example.org", "POWTORKA@example.org")) {
            Res answer = anonymous(HttpMethod.POST, valid(email), null);
            answers.add(answer.status() + " " + answer.body());
        }

        assertThat(answers).containsOnly("202 {\"status\":\"RECEIVED\"}");
        // the repeat of a waiting address neither adds a submission nor overwrites it
        assertThat(count("recruitment_intakes")).isEqualTo(4);
        assertThat(serverLog(output)).doesNotContain("powtorka@example.org", "otwarta.sprawa@example.org");
    }

    @Test
    void aRepeatDoesNotOverwriteTheWaitingSubmission() {
        submitted(valid("raz@example.org"));
        Map<String, Object> other = valid("raz@example.org");
        other.put("firstName", "Podmieniony");

        submitted(other);

        assertThat(jdbc.queryForObject("SELECT first_name FROM recruitment_intakes", String.class)).isEqualTo("Zofia");
    }

    @Test
    void automatedSendersGetTheSameAnswerAndStoreNothing() {
        Map<String, Object> honeypot = valid("bot@example.org");
        honeypot.put("website", "http://spam.example");
        submitted(honeypot);

        Map<String, Object> tooFast = valid("szybki@example.org");
        properties.setMinFillSeconds(60);
        submitted(tooFast);

        assertThat(count("recruitment_intakes")).isZero();
        assertThat(count("recruitment_intake_slots")).isZero();
    }

    @Test
    void aForgedTokenOrAnOutdatedNoticeIsRefused() {
        Map<String, Object> forged = valid("a@example.org");
        forged.put("formToken", "123.forged");
        assertRefused(forged, "FORM_EXPIRED");

        Map<String, Object> outdated = valid("a@example.org");
        outdated.put("consentVersion", "older");
        assertRefused(outdated, "FORM_EXPIRED");
    }

    @Test
    void valuesAreValidatedAndNothingIsStoredOrLoggedWhenRefused(CapturedOutput output) {
        // the service decides ages by the Polish calendar day, which around midnight is not the UTC one
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));

        assertRefused(with("consent", false), "CONSENT_REQUIRED");
        assertRefused(with("firstName", " "), "FIELD_REQUIRED");
        assertRefused(with("email", "nie-adres-kanarek"), "INVALID_VALUE");
        assertRefused(with("phone", "kanarek"), "INVALID_VALUE");
        assertRefused(with("birthDate", "kanarek-data"), "INVALID_VALUE");
        assertRefused(with("birthDate", today.minusYears(16).plusDays(1).toString()), "ADMISSION_AGE");
        assertRefused(with("birthDate", LocalDate.of(today.getYear() - 27, 12, 31).toString()), "ADMISSION_AGE");
        assertRefused(with("districtBodyId", board("mazowieckie").getId()), "INVALID_VALUE");
        assertRefused(with("lastName", "x".repeat(101)), "INVALID_VALUE");
        Map<String, Object> noPlace = with("districtBodyId", null);
        assertRefused(noPlace, "FIELD_REQUIRED");

        assertThat(count("recruitment_intakes")).isZero();
        assertThat(serverLog(output)).doesNotContain("kanarek");

        // exactly 16 today is old enough; without a district the locality is kept
        submitted(with("birthDate", today.minusYears(16).toString()));
        Map<String, Object> unknown = valid("nie.wiem@example.org");
        unknown.put("districtBodyId", null);
        unknown.put("locality", "Radom");
        submitted(unknown);
        assertThat(count("recruitment_intakes")).isEqualTo(2);
    }

    @Test
    void anOversizedBodyIsRefused() {
        Res answer = anonymous(HttpMethod.POST, with("locality", "x".repeat(5000)), null);

        assertThat(answer.status()).isEqualTo(413);
        assertThat(count("recruitment_intakes")).isZero();
    }

    // ---- limits ---------------------------------------------------------------------------------------------

    @Test
    void theHourlyLimitCountsRepeatsLikeNewSubmissions() {
        properties.setMaxPerHour(3);
        submitted(valid("pierwsza@example.org"));
        submitted(valid("pierwsza@example.org"));
        submitted(valid("druga@example.org"));

        // the answer past the limit is the same for a new and for a waiting address
        Res fresh = anonymous(HttpMethod.POST, valid("trzecia@example.org"), null);
        Res repeat = anonymous(HttpMethod.POST, valid("pierwsza@example.org"), null);

        assertThat(fresh.status()).isEqualTo(429);
        assertThat(fresh.code()).isEqualTo("INTAKE_BUSY");
        assertThat(repeat.body()).isEqualTo(fresh.body());

        jdbc.update("UPDATE recruitment_intake_slots SET created_at = created_at - interval '61 minutes'");
        submitted(valid("trzecia@example.org"));
    }

    @Test
    void theDailyLimitOutlivesTheHour() {
        properties.setMaxPerDay(2);
        submitted(valid("a@example.org"));
        submitted(valid("b@example.org"));
        jdbc.update("UPDATE recruitment_intake_slots SET created_at = created_at - interval '2 hours'");

        assertThat(anonymous(HttpMethod.POST, valid("c@example.org"), null).status()).isEqualTo(429);
    }

    @Test
    void theQueueCapIsReleasedByHandlingASubmission() {
        properties.setMaxPending(2);
        submitted(valid("a@example.org"));
        submitted(valid("b@example.org"));

        assertThat(anonymous(HttpMethod.POST, valid("c@example.org"), null).status()).isEqualTo(429);

        assertThat(post(zoMazovia, "/recruitment-intakes/" + idOf("a@example.org") + "/discard",
            Map.of("reason", "OTHER")).status()).isEqualTo(204);
        submitted(valid("c@example.org"));
    }

    @Test
    void thePerClientLimitAppliesOnlyWhenAnAddressIsPassed() {
        properties.setMaxPerClientPerHour(2);
        String client = "203.0.113." + System.nanoTime() % 200;

        assertThat(anonymous(HttpMethod.POST, valid("a@example.org"), client).status()).isEqualTo(202);
        assertThat(anonymous(HttpMethod.POST, valid("b@example.org"), client).status()).isEqualTo(202);
        assertThat(anonymous(HttpMethod.POST, valid("c@example.org"), client).status()).isEqualTo(429);
        assertThat(anonymous(HttpMethod.POST, valid("c@example.org"), client + "9").status()).isEqualTo(202);
        assertThat(anonymous(HttpMethod.POST, valid("d@example.org"), null).status()).isEqualTo(202);

        // the address is never stored
        assertThat(auditPayloads("recruitment :: intake-received")).doesNotContain(client);
    }

    // ---- permissions ----------------------------------------------------------------------------------------

    @Test
    void theQueueIsClosedWithoutManageOrApprove() {
        long id = submittedId("a@example.org", "mazowieckie");

        assertThat(anonymousGet("/recruitment-intakes")).isEqualTo(401);
        assertThat(anonymousGet("/recruitment-intakes/" + id)).isEqualTo(401);

        for (TMember outsider : List.of(plain, reader)) {
            assertThat(get(outsider, "/recruitment-intakes").status()).isEqualTo(403);
            assertThat(get(outsider, "/recruitment-intakes/" + id).status()).isEqualTo(403);
            assertThat(post(outsider, "/recruitment-intakes/" + id + "/accept", Map.of()).status()).isEqualTo(403);
            assertThat(post(outsider, "/recruitment-intakes/" + id + "/discard", Map.of("reason", "SPAM"))
                .status()).isEqualTo(403);
        }

        assertThat(status(id)).isEqualTo("PENDING");
    }

    @Test
    void aDistrictSeesAndHandlesOnlyItsOwnSubmissions() {
        long mazovian = submittedId("a@example.org", "mazowieckie");
        long silesian = submittedId("b@example.org", "slaskie");
        long unassigned = submittedId("c@example.org", null);

        assertThat(listed(zoMazovia)).containsExactly(mazovian);
        assertThat(listed(zoSilesia)).containsExactly(silesian);

        for (long foreign : new long[]{silesian, unassigned}) {
            assertThat(get(zoMazovia, "/recruitment-intakes/" + foreign).status()).isEqualTo(404);
            assertThat(post(zoMazovia, "/recruitment-intakes/" + foreign + "/accept", Map.of()).status())
                .isEqualTo(404);
            assertThat(post(zoMazovia, "/recruitment-intakes/" + foreign + "/discard",
                Map.of("reason", "SPAM")).status()).isEqualTo(404);
            assertThat(post(zoMazovia, "/recruitment-intakes/" + foreign + "/district",
                Map.of("districtBodyId", district("mazowieckie").getId())).status()).isEqualTo(404);
            assertThat(status(foreign)).isEqualTo("PENDING");
        }
    }

    @Test
    void theListCarriesNoPersonalData() {
        submittedId("lista.kanarek@example.org", "mazowieckie");

        assertThat(get(national, "/recruitment-intakes").body().toString())
            .doesNotContain("kanarek", "Zofia", "Zgloszeniowa");
    }

    @Test
    void theNationalBoardSeesAllAndAssignsButCannotAccept() {
        long mazovian = submittedId("a@example.org", "mazowieckie");
        long unassigned = submittedId("c@example.org", null);

        assertThat(listed(national)).containsExactly(mazovian, unassigned);
        assertThat(get(national, "/recruitment-intakes/" + mazovian).body().path("actions").path("accept")
            .asBoolean()).isFalse();
        assertThat(post(national, "/recruitment-intakes/" + mazovian + "/accept", Map.of()).status())
            .isEqualTo(403);
        assertThat(post(national, "/recruitment-intakes/" + unassigned + "/accept", Map.of()).code())
            .isEqualTo("DISTRICT_REQUIRED");

        assertThat(post(national, "/recruitment-intakes/" + unassigned + "/district",
            Map.of("districtBodyId", board("slaskie").getId())).code()).isEqualTo("DISTRICT_REQUIRED");
        assertThat(post(national, "/recruitment-intakes/" + unassigned + "/district",
            Map.of("districtBodyId", district("slaskie").getId())).status()).isEqualTo(204);

        assertThat(listed(zoSilesia)).containsExactly(unassigned);
        assertThat(count("recruitment_cases")).isZero();
    }

    // ---- handling -------------------------------------------------------------------------------------------

    @Test
    void acceptingOpensAWebsiteCaseAndErasesTheSubmission(CapturedOutput output) {
        long id = submittedId("przyjeta.kanarek@example.org", "mazowieckie");

        Res accepted = post(zoMazovia, "/recruitment-intakes/" + id + "/accept", Map.of());

        assertThat(accepted.status()).isEqualTo(200);
        long caseId = accepted.body().path("caseId").asLong();
        assertThat(jdbc.queryForObject("SELECT source || ' ' || status || ' ' || candidate_email "
            + "FROM recruitment_cases WHERE id = ?", String.class, caseId))
            .isEqualTo("WEBSITE NEW przyjeta.kanarek@example.org");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_intakes WHERE id = ? AND case_id = ? "
            + "AND status = 'ACCEPTED' AND email IS NULL AND first_name IS NULL AND birth_date IS NULL "
            + "AND data_erased_at IS NOT NULL", Integer.class, id, caseId)).isOne();
        assertThat(events(id)).containsExactly("RECEIVED", "ACCEPTED", "DATA_ERASED");
        assertThat(post(zoMazovia, "/recruitment-intakes/" + id + "/accept", Map.of()).code())
            .isEqualTo("INTAKE_NOT_PENDING");
        assertThat(auditPayloads("recruitment :: intake-accept") + serverLog(output)).doesNotContain("kanarek");
    }

    @Test
    void anOpenCaseForTheSameEmailIsHintedAndBlocksAcceptance(CapturedOutput output) {
        post(zoMazovia, "/recruitment-cases", Map.of("districtBodyId", district("mazowieckie").getId(),
            "candidate", Map.of("candidateFirstName", "Ma", "candidateLastName", "Sprawe",
                "candidateEmail", "dubel.kanarek@example.org")));
        long id = submittedId("dubel.kanarek@example.org", "mazowieckie");
        long elsewhere = submittedId("inny@example.org", "slaskie");
        jdbc.update("UPDATE recruitment_intakes SET email = 'dubel.kanarek@example.org', status = 'DISCARDED', "
            + "discard_reason = 'OTHER', closed_at = now() WHERE id = ?", elsewhere);

        assertThat(get(zoMazovia, "/recruitment-intakes/" + id).body().path("openCaseWithSameEmail").asBoolean())
            .isTrue();
        // the Silesian board may not read the Mazovian case, so it gets no hint about it
        assertThat(get(zoSilesia, "/recruitment-intakes/" + elsewhere).body().path("openCaseWithSameEmail")
            .asBoolean()).isFalse();

        Res refused = post(zoMazovia, "/recruitment-intakes/" + id + "/accept", Map.of());

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("OPEN_CASE_EXISTS");
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(count("recruitment_cases")).isOne();
        assertThat(serverLog(output)).doesNotContain("dubel.kanarek");
    }

    @Test
    void spamAndWithdrawalAreErasedAtOnceOtherDiscardsKeepDataUntilErased() {
        long spam = submittedId("spam@example.org", "mazowieckie");
        long other = submittedId("inne@example.org", "mazowieckie");

        post(zoMazovia, "/recruitment-intakes/" + spam + "/discard", Map.of("reason", "SPAM"));
        post(zoMazovia, "/recruitment-intakes/" + other + "/discard", Map.of("reason", "NO_CONTACT"));

        assertThat(erased(spam)).isTrue();
        assertThat(erased(other)).isFalse();
        assertThat(post(zoSilesia, "/recruitment-intakes/" + other + "/erase", Map.of()).status()).isEqualTo(404);
        assertThat(post(zoMazovia, "/recruitment-intakes/" + other + "/erase", Map.of()).status()).isEqualTo(204);
        assertThat(erased(other)).isTrue();
        assertThat(events(other)).containsExactly("RECEIVED", "DISCARDED", "DATA_ERASED");
        assertThat(post(zoMazovia, "/recruitment-intakes/" + other + "/erase", Map.of()).code())
            .isEqualTo("INTAKE_NOT_ERASABLE");
    }

    @Test
    void retentionExpiresAndErasesOnlyWhatIsDue() {
        long stale = submittedId("stare@example.org", "mazowieckie");
        long recent = submittedId("swieze@example.org", "mazowieckie");
        long oldDiscard = submittedId("odrzucone.dawno@example.org", "mazowieckie");
        long newDiscard = submittedId("odrzucone.niedawno@example.org", "mazowieckie");
        post(zoMazovia, "/recruitment-intakes/" + oldDiscard + "/discard", Map.of("reason", "OTHER"));
        post(zoMazovia, "/recruitment-intakes/" + newDiscard + "/discard", Map.of("reason", "OTHER"));

        age(stale, "created_at", 61);
        age(recent, "created_at", 59);
        age(oldDiscard, "closed_at", 31);
        age(newDiscard, "closed_at", 29);

        // retention runs while the form is off
        properties.setEnabled(false);
        queue.applyRetention();
        queue.applyRetention();

        assertThat(status(stale)).isEqualTo("EXPIRED");
        assertThat(erased(stale)).isTrue();
        assertThat(events(stale)).containsExactly("RECEIVED", "EXPIRED", "DATA_ERASED");
        assertThat(status(recent)).isEqualTo("PENDING");
        assertThat(erased(recent)).isFalse();
        assertThat(erased(oldDiscard)).isTrue();
        assertThat(erased(newDiscard)).isFalse();
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    /**
     * The captured output without the lines the test itself writes: its HTTP client and the SQL it runs to arrange
     * data both log the values at DEBUG. Everything the server logs, at any level, stays.
     */
    private static String serverLog(CapturedOutput output) {
        return output.getAll().lines()
            .filter(line -> !line.contains("o.s.web.client.DefaultRestClient"))
            .filter(line -> !line.contains("Executing prepared SQL statement [UPDATE recruitment_intakes SET email"))
            // arranging a case goes through the existing staff endpoint, whose request record Spring prints at DEBUG
            .filter(line -> !line.contains("to [CreateCaseRequest["))
            .collect(Collectors.joining("\n"));
    }

    private Map<String, Object> valid(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Zofia");
        body.put("lastName", "Zgloszeniowa");
        body.put("email", email);
        body.put("birthDate", LocalDate.now().minusYears(19).toString());
        body.put("districtBodyId", district("mazowieckie").getId());
        body.put("consent", true);
        Res form = anonymous(HttpMethod.GET, null, null);
        body.put("consentVersion", form.body().path("consentVersion").asString());
        body.put("formToken", form.body().path("formToken").asString());
        return body;
    }

    private Map<String, Object> with(String key, Object value) {
        Map<String, Object> body = valid("walidacja@example.org");
        body.put(key, value);
        return body;
    }

    private void submitted(Map<String, Object> body) {
        Res answer = anonymous(HttpMethod.POST, body, null);
        assertThat(answer.status()).as(answer.body().toString()).isEqualTo(202);
    }

    private long submittedId(String email, String districtSlug) {
        Map<String, Object> body = valid(email);

        if (districtSlug == null) {
            body.put("districtBodyId", null);
            body.put("locality", "Radom");
        } else {
            body.put("districtBodyId", district(districtSlug).getId());
        }

        submitted(body);
        return idOf(email);
    }

    private void assertRefused(Map<String, Object> body, String code) {
        Res answer = anonymous(HttpMethod.POST, body, null);
        assertThat(answer.status()).as(code).isEqualTo(422);
        assertThat(answer.code()).isEqualTo(code);
        assertThat(answer.body().toString()).doesNotContain("kanarek");
    }

    private Res anonymous(HttpMethod method, Object body, String clientAddress) {
        var spec = anon.method(method).uri("http://localhost:" + port + FORM);

        if (clientAddress != null) {
            spec = spec.header("X-Panel-Client-Address", clientAddress);
        }

        if (body != null) {
            // a String body, so that the length is declared as panel-web declares it
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
        }

        var entity = spec.retrieve().toEntity(String.class);
        String text = entity.getBody();

        return new Res(entity.getStatusCode().value(),
            text == null || text.isBlank() ? json.createObjectNode() : json.readTree(text));
    }

    private int anonymousGet(String path) {
        return call(HttpMethod.GET, path, null, null).status();
    }

    private List<Long> listed(TMember as) {
        Res page = get(as, "/recruitment-intakes");
        assertThat(page.status()).isEqualTo(200);
        List<Long> ids = new ArrayList<>();
        page.body().path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private long idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM recruitment_intakes WHERE email = ?", Long.class, email);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM recruitment_intakes WHERE id = ?", String.class, id);
    }

    private boolean erased(long id) {
        return jdbc.queryForObject("SELECT data_erased_at IS NOT NULL AND email IS NULL AND first_name IS NULL "
            + "AND locality IS NULL FROM recruitment_intakes WHERE id = ?", Boolean.class, id);
    }

    private List<String> events(long id) {
        return jdbc.queryForList("SELECT event_type FROM recruitment_intake_events WHERE intake_id = ? ORDER BY id",
            String.class, id);
    }

    private void age(long id, String column, int days) {
        jdbc.update("UPDATE recruitment_intakes SET " + column + " = now() - make_interval(days => ?) WHERE id = ?",
            days, id);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
