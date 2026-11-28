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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.DeclarationForm;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.RecruitmentAssessmentTrack;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCase;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment.TRecruitmentCaseRepository;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.SyntheticPesel;
import org.mlodzirazem.panel.services.panelapi.recruitment.workflow.RecruitmentWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecruitmentApiTest extends ApiTestSupport {
    @Autowired
    RecruitmentWorkflowService workflow;

    @Autowired
    TRecruitmentCaseRepository caseRepository;

    TMember zoA;
    TMember zoB;
    TMember zk;
    TMember readerA;
    TMember plain;

    @BeforeEach
    void setUp() {
        zoA = linked(newMember("Zofia", "Okregowa", "mazowieckie"));
        grant(zoA, board("mazowieckie"), "COORDINATOR");
        zoB = linked(newMember("Zenon", "Slaski", "slaskie"));
        grant(zoB, board("slaskie"), "COORDINATOR");
        zk = linked(newMember("Karol", "Krajowy", "pomorskie"));
        grant(zk, body("national-board"), "SECRETARY");
        readerA = linked(newMember("Tola", "Czytelnik", "mazowieckie"));
        grantDistrictReader(readerA, "mazowieckie");
        plain = linked(newMember("Pat", "Plain", "slaskie"));
    }

    private Map<String, Object> candidate(String firstName, String lastName, String email) {
        return Map.of(
            "candidateFirstName", firstName,
            "candidateLastName", lastName,
            "candidateEmail", email
        );
    }

    private Map<String, Object> caseBody(long districtId, Map<String, Object> candidateFields) {
        return Map.of("districtBodyId", districtId, "candidate", candidateFields);
    }

    private long mazowieckie() {
        return district("mazowieckie").getId();
    }

    private long slaskie() {
        return district("slaskie").getId();
    }

    @Test
    void createIsManageOnlyAndDistrictScoped() {
        Map<String, Object> body = caseBody(mazowieckie(),
            candidate("Kandydat", "Kandydacki", "przyjeciowy@example.org"));

        Res mine = post(zoA, "/recruitment-cases", body);
        assertThat(mine.status()).isEqualTo(201);
        assertThat(mine.body().path("status").asString()).isEqualTo("NEW");
        assertThat(mine.body().path("source").asString()).isEqualTo("MANUAL");
        assertThat(mine.body().path("district").path("code").asString()).isEqualTo("district-mazowieckie");
        assertThat(mine.body().path("events").get(0).path("eventType").asString()).isEqualTo("CASE_CREATED");

        assertThat(post(zoA, "/recruitment-cases", caseBody(slaskie(),
            candidate("Obcy", "Cudzy", "cudzy@example.org"))).status()).isEqualTo(403);

        assertThat(post(readerA, "/recruitment-cases", body).status()).isEqualTo(403);
        assertThat(post(zk, "/recruitment-cases", body).status()).isEqualTo(403);
        assertThat(post(plain, "/recruitment-cases", body).status()).isEqualTo(403);
    }

    @Test
    void createValidatesThePayload() {
        assertThat(post(zoA, "/recruitment-cases",
                Map.of("candidate", candidate("Bez", "Okręgu", "x@example.org"))).status()).isEqualTo(400);
        assertThat(post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
                Map.of("candidateLastName", "Bezimiecka"))).status()).isEqualTo(422);
        assertThat(post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
                Map.of("unknownKey", "x", "candidateFirstName", "Ada", "candidateLastName", "W"))).status()).isEqualTo(400);
        assertThat(post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
                Map.of("candidateBirthDate", "not-a-date", "candidateFirstName", "Ada",
                    "candidateLastName", "W"))).status()).isEqualTo(400);
    }

    @Test
    void createValidatesTheAdmissionAgeOverHttp() {
        long id = mazowieckie();

        Res tooYoung = post(zoA, "/recruitment-cases", Map.of(
            "districtBodyId", id,
            "candidate", Map.of("candidateFirstName", "Ada", "candidateLastName", "Mloda",
                "candidateBirthDate", "2014-01-01")));
        assertThat(tooYoung.status()).isEqualTo(422);
        assertThat(tooYoung.code()).isEqualTo("ADMISSION_AGE");
    }

    @Test
    void duplicateOpenCaseIsAReadableConflict() {
        Map<String, Object> body = caseBody(mazowieckie(),
            candidate("Kandydat", "Kandydacki", "duplikat@example.org"));

        Res mine = post(zoA, "/recruitment-cases", body);
        assertThat(mine.status()).isEqualTo(201);
        long firstId = mine.body().path("id").asLong();

        Res duplicate = post(zoA, "/recruitment-cases", body);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.code()).isEqualTo("OPEN_CASE_EXISTS");

        post(zoA, "/recruitment-cases/" + firstId + "/transitions",
            Map.of("target", "REJECTED", "rejectionReason", "FALSE_ENTRY"));

        assertThat(post(zoA, "/recruitment-cases", body).status()).isEqualTo(201);
    }

    @Test
    void listIsScopedAndFilterable() {
        post(zoA, "/recruitment-cases", caseBody(mazowieckie(), candidate("Anna", "Mazowiecka", "a@example.org")));
        post(zoA, "/recruitment-cases", caseBody(mazowieckie(), candidate("Adam", "Mazowiecki", "b@example.org")));
        post(zoB, "/recruitment-cases", caseBody(slaskie(), candidate("Barbara", "Slaska", "c@example.org")));

        Res asA = get(zoA, "/recruitment-cases?size=100");
        assertThat(asA.status()).isEqualTo(200);
        assertThat(asA.body().path("totalItems").asLong()).isEqualTo(2);
        asA.body().path("items").forEach(n ->
            assertThat(n.path("district").path("code").asString()).isEqualTo("district-mazowieckie"));

        Res asZk = get(zk, "/recruitment-cases?size=100");
        assertThat(asZk.body().path("totalItems").asLong()).isEqualTo(3);

        assertThat(get(readerA, "/recruitment-cases?size=100").body().path("totalItems").asLong()).isEqualTo(2);
        assertThat(get(plain, "/recruitment-cases").status()).isEqualTo(403);

        long slaskieCase = get(zoB, "/recruitment-cases?size=100").body().path("items").get(0).path("id").asLong();
        post(zoB, "/recruitment-cases/" + slaskieCase + "/transitions",
            Map.of("target", "IN_PROGRESS"));

        Res openOnly = get(zoA, "/recruitment-cases?size=100&open=true");
        assertThat(openOnly.body().path("totalItems").asLong()).isEqualTo(2);

        Res closedOnly = get(zoA, "/recruitment-cases?size=100&open=false");
        assertThat(closedOnly.body().path("totalItems").asLong()).isEqualTo(0);

        Res processingSubset = get(zk, "/recruitment-cases?size=100&status=IN_PROGRESS");
        assertThat(processingSubset.body().path("totalItems").asLong()).isEqualTo(1);

        Res slaskieOnly = get(zk, "/recruitment-cases?size=100&district=" + slaskie());
        assertThat(slaskieOnly.body().path("totalItems").asLong()).isEqualTo(1);
    }

    @Test
    void detailNeedsReadInTheCaseDistrict() {
        long caseAId = post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
            candidate("Anna", "Mazowiecka", "widoczna@example.org"))).body().path("id").asLong();
        long caseBId = post(zoB, "/recruitment-cases", caseBody(slaskie(),
            candidate("Barbara", "Slaska", "cudza@example.org"))).body().path("id").asLong();

        Res own = get(zoA, "/recruitment-cases/" + caseAId);
        assertThat(own.status()).isEqualTo(200);
        assertThat(own.body().path("candidateEmail").asString()).isEqualTo("widoczna@example.org");
        assertThat(own.body().path("events")).hasSize(1);

        assertThat(get(zoA, "/recruitment-cases/" + caseBId).status()).isEqualTo(403);
        assertThat(get(readerA, "/recruitment-cases/" + caseAId).status()).isEqualTo(200);
        assertThat(get(zk, "/recruitment-cases/" + caseBId).status()).isEqualTo(200);
        assertThat(get(plain, "/recruitment-cases/" + caseAId).status()).isEqualTo(403);
        assertThat(get(zoA, "/recruitment-cases/999999").status()).isEqualTo(404);
    }

    @Test
    void caseLifecycleOverHttpWithTheM3BoundaryRefused() {
        long caseId = post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
            candidate("Celina", "Procesowa", "proces@example.org"))).body().path("id").asLong();

        Res start = post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "IN_PROGRESS"));
        assertThat(start.status()).isEqualTo(200);
        assertThat(start.body().path("status").asString()).isEqualTo("IN_PROGRESS");

        Res noContact = post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "NO_CONTACT"));
        assertThat(noContact.body().path("status").asString()).isEqualTo("NO_CONTACT");

        Res back = post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "IN_PROGRESS"));
        assertThat(back.body().path("status").asString()).isEqualTo("IN_PROGRESS");

        Res awaiting = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "AWAITING_DECLARATION"));
        assertThat(awaiting.body().path("status").asString()).isEqualTo("AWAITING_DECLARATION");

        Res m3Boundary = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "DECLARATION_RECEIVED"));
        assertThat(m3Boundary.status()).isEqualTo(409);
        assertThat(m3Boundary.code()).isEqualTo("DECLARATION_ARTIFACT_REQUIRED");

        Res noReason = post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "REJECTED"));
        assertThat(noReason.status()).isEqualTo(422);

        Res closed = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "REJECTED", "rejectionReason", "NO_DECLARATION"));
        assertThat(closed.body().path("status").asString()).isEqualTo("REJECTED");
        assertThat(closed.body().path("closedAt")).isNotNull();
        assertThat(closed.body().path("rejectionReason").asString()).isEqualTo("NO_DECLARATION");

        Res closedAgain = post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "IN_PROGRESS"));
        assertThat(closedAgain.status()).isEqualTo(409);
        assertThat(closedAgain.code()).isEqualTo("CASE_NOT_OPEN");

        assertThat(post(readerA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "IN_PROGRESS")).status()).isEqualTo(403);
        assertThat(post(zoB, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "IN_PROGRESS")).status()).isEqualTo(403);
    }

    @Test
    void notesAndRemindersAreRecordedWithoutSendingAnything() {
        long caseId = post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
            candidate("Notatna", "Kandydacka", "notatna@example.org"))).body().path("id").asLong();

        Res note = post(zoA, "/recruitment-cases/" + caseId + "/notes", Map.of("note", "Dzwonić w czwartek"));
        assertThat(note.status()).isEqualTo(200);
        assertThat(note.body().path("events")).hasSize(2);

        Res reminder = post(zoA, "/recruitment-cases/" + caseId + "/reminders", Map.of());
        assertThat(reminder.status()).isEqualTo(200);
        assertThat(reminder.body().path("events")).hasSize(3);
        assertThat(reminder.body().path("events").get(2).path("eventType").asString()).isEqualTo("REMINDER_SENT");

        assertThat(post(readerA, "/recruitment-cases/" + caseId + "/notes", Map.of("note", "x")).status()).isEqualTo(403);
    }

    @Test
    void updateCandidateDataOverHttp() {
        long caseId = post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
            candidate("Edyta", "Kandydacka", "edyta@example.org"))).body().path("id").asLong();

        Res patch = patch(zoA, "/recruitment-cases/" + caseId,
            Map.of("candidate", Map.of("candidatePhone", "+48 600 700 800", "candidateEmail", "edyta2@example.org")));
        assertThat(patch.status()).isEqualTo(200);
        assertThat(patch.body().path("candidateEmail").asString()).isEqualTo("edyta2@example.org");
        assertThat(patch.body().path("candidatePhone").asString()).isEqualTo("+48 600 700 800");

        Res cleared = patch(zoA, "/recruitment-cases/" + caseId,
            Map.of("candidate", Map.of("candidatePhone", "")));
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.body().has("candidatePhone")).isFalse();

        assertThat(patch(readerA, "/recruitment-cases/" + caseId,
            Map.of("candidate", Map.of("candidatePhone", "+48 111"))).status()).isEqualTo(403);
        assertThat(patch(zoB, "/recruitment-cases/" + caseId,
            Map.of("candidate", Map.of("candidatePhone", "+48 111"))).status()).isEqualTo(403);
    }

    @Test
    void listsPaginate() {
        for (int i = 0; i < 3; i++) {
            post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
                candidate("Uczestnik", "Nr" + i, "user" + i + "@example.org")));
        }

        Res page = get(zoA, "/recruitment-cases?page=0&size=2");
        assertThat(page.body().path("items")).hasSize(2);
        assertThat(page.body().path("totalPages").asInt()).isEqualTo(2);

        Res secondPage = get(zoA, "/recruitment-cases?page=1&size=2");
        assertThat(secondPage.body().path("items")).hasSize(1);
    }

    private List<String> kinds(Res response) {
        List<String> kinds = new ArrayList<>();
        response.body().path("decisions").forEach(d -> kinds.add(d.path("kind").asText()));
        return kinds;
    }

    private List<String> fieldValues(Res response, String field) {
        List<String> values = new ArrayList<>();
        response.body().path("transitions").forEach(node -> values.add(node.path(field).asText()));
        return values;
    }

    @Test
    void transitionsEndpointMirrorsTheRulesFromANewCase() {
        long caseId = post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
            candidate("Ola", "Opcjowa", "opcje@example.org"))).body().path("id").asLong();

        Res options = get(zoA, "/recruitment-cases/" + caseId + "/transitions");
        assertThat(options.status()).isEqualTo(200);
        assertThat(fieldValues(options, "target")).containsExactly("IN_PROGRESS", "NO_CONTACT", "REJECTED");
        options.body().path("transitions").forEach(node -> {
            assertThat(node.path("type").asText()).isEqualTo("STATUS_CHANGED");
            assertThat(node.path("allowed").asBoolean()).isTrue();
            assertThat(node.has("blockedReason")).isFalse();
        });
        List<String> reasons = new ArrayList<>();
        options.body().path("transitions").get(2).path("rejectionReasons")
            .forEach(node -> reasons.add(node.asText()));
        assertThat(reasons).containsExactly("FALSE_ENTRY");

        assertThat(get(readerA, "/recruitment-cases/" + caseId + "/transitions").status()).isEqualTo(200);
        assertThat(get(zoB, "/recruitment-cases/" + caseId + "/transitions").status()).isEqualTo(403);
        assertThat(get(plain, "/recruitment-cases/" + caseId + "/transitions").status()).isEqualTo(403);
        assertThat(get(zoA, "/recruitment-cases/999999/transitions").status()).isEqualTo(404);
    }

    @Test
    void transitionsEndpointRefusesTheM3BoundaryAndClosedCases() {
        long caseId = post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
            candidate("Ona", "NaGranicz", "m3granica@example.org"))).body().path("id").asLong();

        post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "IN_PROGRESS"));
        post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "AWAITING_DECLARATION"));

        Res atDeclaration = get(zoA, "/recruitment-cases/" + caseId + "/transitions");
        assertThat(fieldValues(atDeclaration, "target")).containsExactly("DECLARATION_RECEIVED", "REJECTED");
        assertThat(atDeclaration.body().path("transitions").get(0).path("allowed").asBoolean()).isFalse();
        assertThat(atDeclaration.body().path("transitions").get(0).path("blockedReason").asText())
            .isEqualTo("DECLARATION_ARTIFACT_REQUIRED");
        assertThat(post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "DECLARATION_RECEIVED")).status()).isEqualTo(409);
        assertThat(atDeclaration.body().path("transitions").get(1).path("allowed").asBoolean()).isTrue();
        List<String> reasons = new ArrayList<>();
        atDeclaration.body().path("transitions").get(1).path("rejectionReasons")
            .forEach(node -> reasons.add(node.asText()));
        assertThat(reasons).containsExactly("NO_DECLARATION");

        post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "REJECTED", "rejectionReason", "NO_DECLARATION"));

        Res closed = get(zoA, "/recruitment-cases/" + caseId + "/transitions");
        assertThat(closed.status()).isEqualTo(200);
        assertThat(fieldValues(closed, "target")).isEmpty();
        assertThat(post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "IN_PROGRESS")).code()).isEqualTo("CASE_NOT_OPEN");
    }

    /** Drives a fresh HTTP case to PROBATION with the M3 engine as a fixture (the M4a flow serves the declaration). */
    private long probationCase(String email) {
        long caseId = post(zoA, "/recruitment-cases", caseBody(mazowieckie(),
            candidate("Persefona", "Procesowa", email))).body().path("id").asLong();
        post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "IN_PROGRESS"));
        post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "AWAITING_DECLARATION"));
        workflow.recordDeclaration(caseId, zoA.getId(), DeclarationForm.SCAN,
            RecruitmentAssessmentTrack.STANDARD, LocalDate.now().minusDays(80));
        workflow.startProbation(caseId, zoA.getId());
        return caseId;
    }

    private long votingCase(String email) {
        long caseId = probationCase(email);
        post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZO_RECOMMENDED",
                "decidingBodyId", board("mazowieckie").getId(),
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/44/2026"));
        post(zoA, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "ZK_VOTING"));
        return caseId;
    }

    @Test
    void postDeclarationTransitionsRunOverHttpWithTheEngineRules() {
        long caseId = probationCase("postdekl@example.org");

        Res options = get(zoA, "/recruitment-cases/" + caseId + "/transitions");
        assertThat(fieldValues(options, "target"))
            .containsExactly("PROBATION_EXTENDED", "ZO_RECOMMENDED", "REJECTED");

        assertThat(post(readerA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZK_VOTING")).status()).isEqualTo(403);
        assertThat(post(zoB, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZK_VOTING")).status()).isEqualTo(403);

        Res recommended = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZO_RECOMMENDED",
                "decidingBodyId", board("mazowieckie").getId(),
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/45/2026",
                "note", "aktywna w projekcie"));
        assertThat(recommended.status()).isEqualTo(200);
        assertThat(recommended.body().path("status").asString()).isEqualTo("ZO_RECOMMENDED");
        assertThat(recommended.body().path("zoRecommendedOn").asString()).isEqualTo(LocalDate.now().toString());
        assertThat(recommended.body().path("admissionResolutionNotLaterOn").asString())
            .isEqualTo(LocalDate.now().plusDays(7).toString());
        assertThat(recommended.body().path("decisions").get(0).path("kind").asString())
            .isEqualTo("ZO_ASSESSMENT_POSITIVE");
        assertThat(recommended.body().path("decisions").get(0).path("decisionSignature").asString())
            .isEqualTo("ZO/MZ/45/2026");

        Res voting = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZK_VOTING"));
        assertThat(voting.status()).isEqualTo(200);
        assertThat(voting.body().path("status").asString()).isEqualTo("ZK_VOTING");

        Res asZkToo = post(zk, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "REJECTED",
                "rejectionReason", "VOTE_FAILED",
                "decidingBodyId", board("mazowieckie").getId(),
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/46/2026"));
        assertThat(asZkToo.status()).isEqualTo(200);
        assertThat(asZkToo.body().path("status").asString()).isEqualTo("REJECTED");
        assertThat(asZkToo.body().path("rejectionReason").asString()).isEqualTo("VOTE_FAILED");
        assertThat(asZkToo.body().path("closedAt").asString()).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void assessmentWindowIsEnforcedAndTheExtensionIsOnce() {
        long caseId = probationCase("wczesna@example.org");

        Res tooEarly = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZO_RECOMMENDED",
                "decidingBodyId", board("mazowieckie").getId(),
                "decidedOn", LocalDate.now().minusDays(120).toString(),
                "signature", "ZO/MZ/47/2026"));
        assertThat(tooEarly.status()).isEqualTo(422);
        assertThat(tooEarly.code()).isEqualTo("ASSESSMENT_TOO_EARLY");

        Res extended = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "PROBATION_EXTENDED",
                "decidingBodyId", board("mazowieckie").getId(),
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/48/2026"));
        assertThat(extended.status()).isEqualTo(200);
        assertThat(extended.body().path("status").asString()).isEqualTo("PROBATION_EXTENDED");
        assertThat(extended.body().path("extensionCount").asInt()).isEqualTo(1);
        assertThat(extended.body().path("secondAssessmentNotLaterOn").asString())
            .isEqualTo(LocalDate.now().plusDays(31).toString());

        // The once-only rule holds over REST through the map: PROBATION_EXTENDED has no self-edge,
        // so a second extension cannot even be requested (EXTENSION_ALREADY_USED stays an engine backstop).
        Res again = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "PROBATION_EXTENDED",
                "decidingBodyId", board("mazowieckie").getId(),
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/49/2026"));
        assertThat(again.status()).isEqualTo(422);
        assertThat(again.code()).isEqualTo("TRANSITION_NOT_ALLOWED");

        assertThat(post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZO_RECOMMENDED")).status()).isEqualTo(400);
        assertThat(post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZO_RECOMMENDED",
                "decidingBodyId", board("mazowieckie").getId(),
                "decidedOn", LocalDate.now().toString(),
                "signature", "   ")).status()).isEqualTo(400);

        Res foreignBoard = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZO_RECOMMENDED",
                "decidingBodyId", board("slaskie").getId(),
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/50/2026"));
        assertThat(foreignBoard.status()).isEqualTo(422);
        assertThat(foreignBoard.code()).isEqualTo("DECIDING_BODY_NOT_ZO");

        Res secondAssessment = post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ZO_RECOMMENDED",
                "decidingBodyId", board("mazowieckie").getId(),
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/51/2026"));
        assertThat(secondAssessment.status()).isEqualTo(200);
        assertThat(secondAssessment.body().path("status").asString()).isEqualTo("ZO_RECOMMENDED");
    }

    @Test
    void decisionsOverHttpAreRoleGatedAndUniqueAndDoNotFlipTheStatus() {
        long caseId = votingCase("decyzyjna@example.org");

        assertThat(post(zoA, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "PERIOD_EXTENSION",
                "decidedOn", LocalDate.now().toString(),
                "signature", "X")).status()).isEqualTo(400);

        Res zoAsZk = post(zoA, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "ZK_APPROVAL",
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZK/77/2026"));
        assertThat(zoAsZk.status()).isEqualTo(403);
        assertThat(zoAsZk.code()).isEqualTo("RECRUITMENT_APPROVE_REQUIRED");

        Res resolutionTooEarly = post(zoA, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "ZO_ADMISSION_RESOLUTION",
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/78/2026"));
        assertThat(resolutionTooEarly.status()).isEqualTo(409);
        assertThat(resolutionTooEarly.code()).isEqualTo("ZK_APPROVAL_REQUIRED");

        Res zkApproval = post(zk, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "ZK_APPROVAL",
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZK/77/2026"));
        assertThat(zkApproval.status()).isEqualTo(200);
        assertThat(zkApproval.body().path("status").asString()).isEqualTo("ZK_VOTING");
        List<String> kindsAfterZk = kinds(zkApproval);
        assertThat(kindsAfterZk).contains("ZK_APPROVAL");
        assertThat(zkApproval.body().path("decisions").get(kindsAfterZk.size() - 1).path("decidingBodyName").asString())
            .isEqualTo("Zarząd Krajowy");

        assertThat(post(zk, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "ZK_APPROVAL",
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZK/77/2026")).code()).isEqualTo("DECISION_ALREADY_RECORDED");

        Res zkResolution = post(zk, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "ZO_ADMISSION_RESOLUTION",
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/78/2026"));
        assertThat(zkResolution.status()).isEqualTo(403);

        Res resolution = post(zoA, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "ZO_ADMISSION_RESOLUTION",
                "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/78/2026"));
        assertThat(resolution.status()).isEqualTo(200);
        assertThat(resolution.body().path("status").asString()).isEqualTo("ZK_VOTING");

        assertThat(kinds(resolution))
            .contains("ZO_ASSESSMENT_POSITIVE", "ZK_APPROVAL", "ZO_ADMISSION_RESOLUTION");
    }

    @Test
    void acceptedTransitionAdmitsTheMemberAndReturnsTheNumber() {
        long caseId = votingCase("przyjmowana@example.org");

        // Without both decisions the engine refuses and the helper says so.
        Res early = post(zk, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "ACCEPTED"));
        assertThat(early.status()).isEqualTo(409);
        assertThat(early.code()).isEqualTo("BOTH_DECISIONS_REQUIRED");

        recordBothDecisions(caseId);
        giveAdmissionData(caseId);

        Res options = get(zk, "/recruitment-cases/" + caseId + "/transitions");
        assertThat(options.body().path("transitions").get(0).path("allowed").asBoolean()).isTrue();

        assertThat(post(zoA, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ACCEPTED")).status()).isEqualTo(403);
        assertThat(post(zoB, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ACCEPTED")).status()).isEqualTo(403);

        Res accepted = post(zk, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "ACCEPTED"));
        assertThat(accepted.status()).isEqualTo(200);
        assertThat(accepted.body().path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(accepted.body().path("acceptedOn").asString()).isEqualTo(LocalDate.now().toString());
        assertThat(accepted.body().path("closedAt").asString()).isEqualTo(LocalDate.now().toString());

        long memberId = accepted.body().path("acceptedMemberId").asLong();
        String number = accepted.body().path("acceptedMemberNumber").asString();
        assertThat(memberId).isPositive();
        assertThat(number).matches("\\d{6}");
        assertThat(Integer.parseInt(number)).isLessThan(950_000);
        assertThat(memberRepository.findById(memberId).orElseThrow().getMemberId()).isEqualTo(number);

        // The case now reads the same, and a closed case is read-only.
        Res again = get(zk, "/recruitment-cases/" + caseId);
        assertThat(again.body().path("acceptedMemberNumber").asString()).isEqualTo(number);
        assertThat(post(zk, "/recruitment-cases/" + caseId + "/transitions",
            Map.of("target", "ACCEPTED")).code()).isEqualTo("CASE_NOT_OPEN");
    }

    @Test
    void acceptedTransitionRefusesAnExistingMemberGenerically() {
        long caseId = votingCase("zdublowana@example.org");
        recordBothDecisions(caseId);
        giveAdmissionData(caseId);

        TRecruitmentCase candidateCase = caseRepository.findById(caseId).orElseThrow();
        TMember existing = newMember("Istniejacy", "Czlonek", "slaskie");
        existing.setFormalIdentifier(candidateCase.getCandidateFormalIdentifier());
        memberRepository.saveAndFlush(existing);

        Res refused = post(zk, "/recruitment-cases/" + caseId + "/transitions", Map.of("target", "ACCEPTED"));
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("MEMBER_ALREADY_EXISTS");
        assertThat(refused.body().toString()).doesNotContain("Istniejacy").doesNotContain("Czlonek");
        assertThat(get(zk, "/recruitment-cases/" + caseId).body().path("status").asString())
            .isEqualTo("ZK_VOTING");
    }

    private void recordBothDecisions(long caseId) {
        assertThat(post(zk, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "ZK_APPROVAL", "decidedOn", LocalDate.now().toString(),
                "signature", "ZK/80/2026")).status()).isEqualTo(200);
        assertThat(post(zoA, "/recruitment-cases/" + caseId + "/decisions",
            Map.of("kind", "ZO_ADMISSION_RESOLUTION", "decidedOn", LocalDate.now().toString(),
                "signature", "ZO/MZ/81/2026")).status()).isEqualTo(200);
    }

    /** What the e-declaration fills on the case: formal names and the (synthetic) identifier. */
    private void giveAdmissionData(long caseId) {
        TRecruitmentCase candidateCase = caseRepository.findById(caseId).orElseThrow();
        candidateCase.setCandidateFormalFirstName("Persefona");
        candidateCase.setCandidateFormalLastName("Procesowa");
        candidateCase.setCandidateFormalIdentifier(SyntheticPesel.of(LocalDate.of(2007, 4, 1), 4242));
        caseRepository.saveAndFlush(candidateCase);
    }

    @Test
    void acceptedFlipIsBlockedInTheHelperUntilBothDecisionsExist() {
        long caseId = votingCase("rezerva@example.org");

        Res options = get(zk, "/recruitment-cases/" + caseId + "/transitions");
        assertThat(fieldValues(options, "target")).containsExactly("ACCEPTED", "REJECTED");
        assertThat(options.body().path("transitions").get(0).path("allowed").asBoolean()).isFalse();
        assertThat(options.body().path("transitions").get(0).path("blockedReason").asText())
            .isEqualTo("BOTH_DECISIONS_REQUIRED");

        List<String> reasons = new ArrayList<>();
        options.body().path("transitions").get(1).path("rejectionReasons")
            .forEach(node -> reasons.add(node.asText()));
        assertThat(reasons).containsExactly("VOTE_FAILED", "ZK_BAN");
    }

    @Test
    void summaryAndDetailServeTheDisplayNameAndTheIntakeComparison() {
        Res created = post(zoA, "/recruitment-cases",
            caseBody(mazowieckie(), candidate("Testowa", "Pierwsza", "wyswietlanie@example.org")));
        long id = created.body().path("id").asLong();

        Res beforeDeclaration = get(zoA, "/recruitment-cases/" + id);
        assertThat(beforeDeclaration.body().path("candidateDisplayFirstName").asString()).isEqualTo("Testowa");
        assertThat(beforeDeclaration.body().path("candidateNameSource").asString()).isEqualTo("CASE");
        assertThat(beforeDeclaration.body().path("candidateNameComparison").path("match").asString())
            .isEqualTo("INCOMPLETE");

        TRecruitmentCase stored = caseRepository.findById(id).orElseThrow();
        stored.setCandidatePreferredFirstName("Druga");
        stored.setCandidatePreferredLastName("Pierwsza");
        caseRepository.saveAndFlush(stored);

        Res detail = get(zoA, "/recruitment-cases/" + id);
        assertThat(detail.body().path("candidateFirstName").asString()).isEqualTo("Testowa");
        assertThat(detail.body().path("candidateDisplayFirstName").asString()).isEqualTo("Druga");
        assertThat(detail.body().path("candidateDisplayLastName").asString()).isEqualTo("Pierwsza");
        assertThat(detail.body().path("candidateNameSource").asString()).isEqualTo("DECLARATION_PREFERRED");
        assertThat(detail.body().path("candidateNameComparison").path("match").asString()).isEqualTo("DIFFERENT");
        assertThat(detail.body().path("candidateNameComparison").path("caseName").path("stage").asString())
            .isEqualTo("CASE");
        assertThat(detail.body().path("candidateNameComparison").path("declarationPreferred").path("first").asString())
            .isEqualTo("Druga");

        Res list = get(zoA, "/recruitment-cases?size=100");
        JsonNode row = null;
        for (var node : list.body().path("items")) {
            if (node.path("id").asLong() == id) {
                row = node;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.path("candidateFirstName").asString()).isEqualTo("Testowa");
        assertThat(row.path("candidateDisplayFirstName").asString()).isEqualTo("Druga");
        assertThat(row.path("candidateNameSource").asString()).isEqualTo("DECLARATION_PREFERRED");
    }
}
