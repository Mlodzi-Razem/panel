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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecruitmentApiTest extends ApiTestSupport {
    TMember zoA;
    TMember zoB;
    TMember zk;
    TMember treasurerA;
    TMember plain;

    @BeforeEach
    void setUp() {
        zoA = linked(newMember("Zofia", "Okregowa", "mazowieckie"));
        grant(zoA, board("mazowieckie"), "COORDINATOR");
        zoB = linked(newMember("Zenon", "Slaski", "slaskie"));
        grant(zoB, board("slaskie"), "COORDINATOR");
        zk = linked(newMember("Karol", "Krajowy", "pomorskie"));
        grant(zk, body("national-board"), "SECRETARY");
        treasurerA = linked(newMember("Tola", "Skarbnik", "mazowieckie"));
        grant(treasurerA, body("district-mazowieckie-treasurer"), "TREASURER");
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

        assertThat(post(treasurerA, "/recruitment-cases", body).status()).isEqualTo(403);
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

        assertThat(get(treasurerA, "/recruitment-cases?size=100").body().path("totalItems").asLong()).isEqualTo(2);
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
        assertThat(get(treasurerA, "/recruitment-cases/" + caseAId).status()).isEqualTo(200);
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
        assertThat(m3Boundary.status()).isEqualTo(422);
        assertThat(m3Boundary.code()).isEqualTo("TRANSITION_NOT_ALLOWED");

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

        assertThat(post(treasurerA, "/recruitment-cases/" + caseId + "/transitions",
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

        assertThat(post(treasurerA, "/recruitment-cases/" + caseId + "/notes", Map.of("note", "x")).status()).isEqualTo(403);
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

        assertThat(patch(treasurerA, "/recruitment-cases/" + caseId,
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
}
