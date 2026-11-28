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
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.PadesSignatures;
import org.mlodzirazem.panel.services.panelapi.recruitment.eform.SyntheticPesel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecruitmentEformApiTest extends ApiTestSupport {
    private static final char[] KEYSTORE_PASSWORD = "rekrut-fix".toCharArray();

    TMember zoA;
    TMember zoB;
    TMember plain;

    RestClient anon;

    @DynamicPropertySource
    static void declarationStorage(DynamicPropertyRegistry registry) throws IOException {
        Path storage = Files.createTempDirectory("declarations-api-test");
        registry.add("panel.api.recruitment.declaration.storage-dir", () -> storage.toString());
    }

    @BeforeEach
    void setUp() {
        zoA = linked(newMember("Ola", "Okregowa", "mazowieckie"));
        grant(zoA, board("mazowieckie"), "COORDINATOR");
        zoB = linked(newMember("Oskar", "Slaski", "slaskie"));
        grant(zoB, board("slaskie"), "COORDINATOR");
        plain = linked(newMember("Pan", "Plain", "mazowieckie"));

        anon = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory())
            .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
            .build();
    }

    private Map<String, Object> candidate() {
        return Map.of(
            "candidateFirstName", "Edek",
            "candidateLastName", "Elektroniczny",
            "candidateEmail", "edek@example.org"
        );
    }

    /** A case at the M2/M4a boundary: qualified by the intake and waiting for the declaration. */
    private long awaitingCase(String email) {
        Map<String, Object> body = Map.of(
            "districtBodyId", district("mazowieckie").getId(),
            "candidate", Map.of(
                "candidateFirstName", "Edek",
                "candidateLastName", "Elektroniczny",
                "candidateEmail", email
            )
        );

        long caseId = post(zoA, "/recruitment-cases", body).body().path("id").asLong();
        jdbc.update("UPDATE recruitment_cases SET status = 'AWAITING_DECLARATION' WHERE id = ?",
            caseId);
        return caseId;
    }

    @Test
    void linkResponseCarriesTheEmailDispatchWithoutTheAddress() {
        long withEmail = awaitingCase("kandydat@example.org");
        Res mailOff = createLink(201, zoA, withEmail);

        // mail is switched off by default: nothing is sent, the back office is told to pass the link on
        assertThat(mailOff.body().path("emailDispatch").asString()).isEqualTo("DISABLED");
        assertThat(mailOff.body().toString()).doesNotContain("kandydat@example.org");

        long withoutEmail = awaitingCase("brak@example.org");
        jdbc.update("UPDATE recruitment_cases SET candidate_email = NULL WHERE id = ?", withoutEmail);
        Res noEmail = createLink(201, zoA, withoutEmail);

        assertThat(noEmail.body().path("emailDispatch").asString()).isEqualTo("NO_EMAIL");
        assertThat(noEmail.body().path("formUrl").asString()).contains(noEmail.body().path("token").asString());
    }

    private Res createLink(int expectedStatus, TMember as, long caseId) {
        Res link = post(as, "/recruitment-cases/" + caseId + "/declaration-link", Map.of());
        assertThat(link.status()).isEqualTo(expectedStatus);
        return link;
    }

    private Map<String, Object> submitBody(String pesel) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
            "formalFirstName", "Edmunda",
            "formalLastName", "Elektroniczna",
            "preferredFirstName", "Edka",
            "preferredLastName", "Elektroniczniakowa",
            "pronouns", "ona",
            "personalId", pesel,
            "phone", "+48 600 200 100",
            "email", "edka.kontakt@example.org",
            "imageConsent", true,
            "specialCategoryConsent", true));
        body.put("infoDutyAcknowledged", true);
        return body;
    }

    @Test
    void linkIsManageOnlyAndReturnsTheTokenOnce() {
        long caseId = awaitingCase("edek-link@example.org");

        Res mine = createLink(201, zoA, caseId);
        assertThat(mine.body().path("token").asString()).isNotBlank();
        assertThat(mine.body().path("formUrl").asString()).contains(mine.body().path("token").asString());
        assertThat(mine.body().path("expiresAt").asString()).isNotBlank();

        assertThat(post(readerOfMazowieckie(), "/recruitment-cases/" + caseId
            + "/declaration-link", Map.of()).status()).isEqualTo(403);
        assertThat(post(zoB, "/recruitment-cases/" + caseId + "/declaration-link",
            Map.of()).status()).isEqualTo(403);
        assertThat(post(plain, "/recruitment-cases/" + caseId + "/declaration-link",
            Map.of()).status()).isEqualTo(403);

        Res repeat = createLink(201, zoA, caseId);
        assertThat(repeat.body().path("token").asString()).isNotEqualTo(mine.body().path("token").asString());
    }

    private TMember readerOfMazowieckie() {
        TMember reader = linked(newMember("Tola", "Czytelnik", "mazowieckie"));
        grantDistrictReader(reader, "mazowieckie");
        return reader;
    }

    @Test
    void anonymousCandidateWalksLandingSubmitPdfAndSignature() throws Exception {
        long caseId = awaitingCase("edek-anon@example.org");
        Res link = createLink(201, zoA, caseId);
        String token = link.body().path("token").asString();
        // the default panel-web address of a local run; deployments set PANEL_WEB_URL
        assertThat(link.body().path("formUrl").asString())
            .isEqualTo("http://localhost:3000/rekrutacja/deklaracja/" + token);
        String pesel = SyntheticPesel.of(java.time.LocalDate.of(2007, 4, 1), 21);

        Res landing = call(HttpMethod.GET, "/public/declaration-form/" + token, null, null);
        assertThat(landing.status()).isEqualTo(200);
        assertThat(landing.body().path("status").asString()).isEqualTo("CREATED");
        assertThat(landing.body().path("greeting").asString()).isEqualTo("Edek Elektroniczny");

        Res submit = call(HttpMethod.POST, "/public/declaration-form/" + token, null, submitBody(pesel));
        assertThat(submit.status()).isEqualTo(200);
        assertThat(submit.body().path("status").asString()).isEqualTo("FILLED");
        String pdfUrl = submit.body().path("pdfUrl").asString();
        assertThat(pdfUrl).isEqualTo("/public/declaration-form/" + token + "/pdf");

        ResponseEntity<byte[]> pdf = anon.get()
            .uri("http://localhost:" + port + pdfUrl)
            .retrieve().toEntity(byte[].class);

        assertThat(pdf.getStatusCode().value()).isEqualTo(200);
        assertThat(pdf.getHeaders().getContentType()).isNotNull();
        assertThat(pdf.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
            .isEqualTo("%PDF-");

        byte[] p12 = PadesSignatures.selfSignedP12("Edmunda Elektroniczna (test)", pesel,
            KEYSTORE_PASSWORD);
        byte[] signed = PadesSignatures.sign(pdf.getBody(), p12, KEYSTORE_PASSWORD);

        Res attached = multipart("/public/declaration-form/" + token + "/declaration", null, signed);
        assertThat(attached.status()).isEqualTo(200);
        assertThat(attached.body().path("status").asString()).isEqualTo("SIGNED");

        assertThat(jdbc.queryForObject(
            "SELECT status::text FROM recruitment_cases WHERE id = ?", String.class, caseId))
            .isEqualTo("DECLARATION_RECEIVED");
    }

    @Test
    void anonymousSurfaceSpeaksTheContractCodes() throws Exception {
        long caseId = awaitingCase("edek-kody@example.org");
        String token = createLink(201, zoA, caseId).body().path("token").asString();

        assertThat(call(HttpMethod.GET, "/public/declaration-form/zlfiony-token", null, null).code())
            .isEqualTo("TOKEN_INVALID");
        assertThat(call(HttpMethod.GET, "/public/declaration-form/zlfiony-token", null, null)
            .status()).isEqualTo(404);

        jdbc.update("UPDATE declaration_form_sessions SET expires_at = now() - interval '1 hour'"
            + " WHERE case_id = ?", caseId);
        assertThat(call(HttpMethod.GET, "/public/declaration-form/" + token, null, null))
            .extracting(Res::status, Res::code)
            .containsExactly(410, "TOKEN_EXPIRED");

        long closedCase = awaitingCase("edek-zamknieta@example.org");
        String closedToken = createLink(201, zoA, closedCase).body().path("token").asString();
        jdbc.update("UPDATE recruitment_cases SET status = 'REJECTED',"
            + " rejection_reason = 'NOT_FIT', closed_at = CURRENT_DATE WHERE id = ?",
            closedCase);
        assertThat(call(HttpMethod.GET, "/public/declaration-form/" + closedToken, null, null))
            .extracting(Res::status, Res::code)
            .containsExactly(409, "CASE_CLOSED");
    }

    @Test
    void submitRejectsIncompletePairsWithUnprocessableEntity() throws Exception {
        long caseId = awaitingCase("edek-pola@example.org");
        String token = createLink(201, zoA, caseId).body().path("token").asString();

        Map<String, Object> half = submitBody(SyntheticPesel.of(java.time.LocalDate.of(2007, 4, 1), 22));
        half = new java.util.LinkedHashMap<>(half);
        half.put("formalLastName", null);

        Res broken = call(HttpMethod.POST, "/public/declaration-form/" + token, null, half);
        assertThat(broken.status()).isEqualTo(422);
        assertThat(broken.code()).isEqualTo("NAMES_ARE_A_PAIR");
    }

    @Test
    void submitWithoutTheInformationDutyAcknowledgementIsUnprocessable() throws Exception {
        long caseId = awaitingCase("edek-obowiazek@example.org");
        String token = createLink(201, zoA, caseId).body().path("token").asString();
        Map<String, Object> unconfirmed = new java.util.LinkedHashMap<>(
            submitBody(SyntheticPesel.of(java.time.LocalDate.of(2007, 4, 1), 23)));
        unconfirmed.put("infoDutyAcknowledged", false);

        Res refused = call(HttpMethod.POST, "/public/declaration-form/" + token, null, unconfirmed);
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("FIELD_REQUIRED");
    }

    @Test
    void paperScanUploadsThroughTheBackOfficeOnly() {
        long caseId = awaitingCase("edek-skan@example.org");
        byte[] scan = "%PDF-1.4 skan deklaracji".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Map<String, String> data = scanData(SyntheticPesel.of(java.time.LocalDate.of(2007, 4, 1), 31));

        Res mine = multipart("/recruitment-cases/" + caseId + "/declaration-scan",
            token(sub(zoA), zoA.getDomainEmail()), scan, data);
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.body().path("status").asString()).isEqualTo("SIGNED");

        long anotherCase = awaitingCase("edek-drugi@example.org");
        assertThat(multipart("/recruitment-cases/" + anotherCase + "/declaration-scan",
            token(sub(plain), plain.getDomainEmail()), scan, data).status()).isEqualTo(403);

        long secondCase = awaitingCase("edek-trzeci@example.org");
        Map<String, String> otherData = scanData(SyntheticPesel.of(java.time.LocalDate.of(2007, 4, 1), 32));
        Res firstScan = multipart("/recruitment-cases/" + secondCase + "/declaration-scan",
            token(sub(zoA), zoA.getDomainEmail()), scan, otherData);
        assertThat(firstScan.status()).isEqualTo(200);
        assertThat(multipart("/recruitment-cases/" + secondCase + "/declaration-scan",
            token(sub(zoA), zoA.getDomainEmail()), scan, otherData).status()).isEqualTo(409);
    }

    private static Map<String, String> scanData(String pesel) {
        return Map.of(
            "formalFirstName", "Edmunda",
            "formalLastName", "Elektroniczna",
            "personalId", pesel,
            "specialCategoryConsent", "true",
            "imageConsent", "false");
    }

    @Test
    void scanWithoutDataIsRefusedAndTheDetailExposesTheFormalDataWithoutThePesel() {
        long caseId = awaitingCase("edek-skan-bez@example.org");
        byte[] scan = "%PDF-1.4 skan deklaracji".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String bearer = token(sub(zoA), zoA.getDomainEmail());

        Res missing = multipart("/recruitment-cases/" + caseId + "/declaration-scan", bearer, scan, Map.of());
        assertThat(missing.status()).isEqualTo(422);
        assertThat(missing.code()).isEqualTo("FIELD_REQUIRED");

        assertThat(get(zoA, "/recruitment-cases/" + caseId).body().path("formalIdentifierPresent").asBoolean())
            .isFalse();

        String pesel = SyntheticPesel.of(java.time.LocalDate.of(2007, 4, 1), 33);
        assertThat(multipart("/recruitment-cases/" + caseId + "/declaration-scan", bearer, scan,
            scanData(pesel)).status()).isEqualTo(200);

        Res detail = get(zoA, "/recruitment-cases/" + caseId);
        assertThat(detail.body().path("formalIdentifierPresent").asBoolean()).isTrue();
        assertThat(detail.body().path("candidateFormalFirstName").asString()).isEqualTo("Edmunda");
        assertThat(detail.body().toString()).doesNotContain(pesel);
    }

    @Test
    void correctionEndpointRecordsTheReasonInTheTimelineAndChecksThePermission() {
        long caseId = awaitingCase("edek-korekta@example.org");
        byte[] scan = "%PDF-1.4 skan deklaracji".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        multipart("/recruitment-cases/" + caseId + "/declaration-scan", token(sub(zoA), zoA.getDomainEmail()),
            scan, scanData(SyntheticPesel.of(java.time.LocalDate.of(2007, 4, 1), 34)));

        String path = "/recruitment-cases/" + caseId + "/candidate-data-corrections";

        assertThat(post(plain, path, Map.of("formalFirstName", "Edmund", "formalLastName", "Elektroniczny",
            "reason", "Literówka")).status()).isEqualTo(403);

        Res noReason = post(zoA, path, Map.of("formalFirstName", "Edmund", "formalLastName", "Elektroniczny"));
        assertThat(noReason.status()).isEqualTo(422);
        assertThat(noReason.code()).isEqualTo("CORRECTION_REQUIRES_REASON");

        Res ok = post(zoA, path, Map.of("formalFirstName", "Edmund", "formalLastName", "Elektroniczny",
            "reason", "Literówka w papierowej deklaracji"));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().path("candidateFormalFirstName").asString()).isEqualTo("Edmund");

        boolean recorded = false;

        for (var event : ok.body().path("events")) {
            if ("CANDIDATE_DATA_CORRECTED".equals(event.path("eventType").asString())) {
                recorded = true;
                assertThat(event.path("payload").path("reason").asString())
                    .isEqualTo("Literówka w papierowej deklaracji");
                assertThat(event.path("payload").path("fields").toString())
                    .contains("candidate_formal_first_name").doesNotContain("Edmund");
            }
        }

        assertThat(recorded).isTrue();
        assertThat(post(zoA, path, Map.of("formalFirstName", "Edmund", "formalLastName", "Elektroniczny",
            "reason", "Jeszcze raz")).code()).isEqualTo("NO_CHANGES");
    }

    private Res multipart(String path, String token, byte[] pdf) {
        return multipart(path, token, pdf, Map.of());
    }

    private Res multipart(String path, String token, byte[] pdf, Map<String, String> fields) {
        ByteArrayResource filePart = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "deklaracja.pdf";
            }
        };

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", filePart);
        fields.forEach(parts::add);

        var spec = anon.post().uri("http://localhost:" + port + path);

        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        ResponseEntity<String> entity = spec.contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve().toEntity(String.class);

        String text = entity.getBody();

        return new Res(entity.getStatusCode().value(),
            text == null || text.isBlank() ? json.createObjectNode() : json.readTree(text));
    }
}
