/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeesApiTest extends ApiTestSupport {
    private static final String SECRET_REASON = "poufne-uzasadnienie-ulgi";
    private static final String SECRET_NOTE = "poufna-notatka-skarbnika";
    private static final YearMonth NOW = YearMonth.now(Clock.systemUTC());

    private TMember treasurer;
    private TMember secretary;
    private TMember auditor;
    private TMember coordinator;
    private TMember otherCoordinator;
    private TMember districtTreasurer;
    private TMember plain;
    private TMember payer;
    private TMember outsider;

    @BeforeEach
    void people() {
        treasurer = person("Fee", "Treasurer", "slaskie");
        grant(treasurer, body("national-board"), "TREASURER");
        secretary = person("Fee", "Secretary", "slaskie");
        grant(secretary, body("national-board"), "SECRETARY");
        auditor = person("Fee", "Auditor", "slaskie");
        grant(auditor, body("audit-commission"), "MEMBER");
        coordinator = person("Dis", "Coordinator", "mazowieckie");
        grant(coordinator, board("mazowieckie"), "COORDINATOR");
        otherCoordinator = person("Other", "Coordinator", "slaskie");
        grant(otherCoordinator, board("slaskie"), "COORDINATOR");
        districtTreasurer = person("Dis", "Treasurer", "mazowieckie");
        grant(districtTreasurer, board("mazowieckie"), "TREASURER");
        plain = person("Plain", "Member", "mazowieckie");
        payer = person("Pay", "Er", "mazowieckie");
        outsider = person("Out", "Sider", "slaskie");
        jdbc.update("UPDATE members SET member_id = '000205', member_since = ? WHERE id = ?",
            NOW.atDay(1), payer.getId());
    }

    private TMember person(String first, String last, String district) {
        return linked(newMember(first, last, district));
    }

    private long createTier() {
        Res tier = post(treasurer, "/fees/tiers", Map.of("code", "standard", "name", "Standardowa"));
        assertThat(tier.status()).isEqualTo(201);
        return tier.body().path("id").asLong();
    }

    private long rate(long tierId) {
        Res rate = post(treasurer, "/fees/tiers/" + tierId + "/rates",
            Map.of("monthlyAmount", "30", "validFrom", NOW.toString(), "resolutionReference", "U/1"));
        assertThat(rate.status()).isEqualTo(201);
        return rate.body().path("id").asLong();
    }

    private Res grantExemption(TMember member) {
        return post(treasurer, "/fees/members/" + member.getId() + "/adjustments", Map.of(
            "kind", "DEFERRAL", "from", NOW.toString(), "reasonCategory", "HEALTH",
            "justification", SECRET_REASON));
    }

    private Res pay(TMember actor, TMember member) {
        return post(actor, "/fees/members/" + member.getId() + "/payments", Map.of(
            "amount", "10", "paidOn", LocalDate.now(Clock.systemUTC()).toString(), "source", "CASH",
            "note", SECRET_NOTE));
    }

    @Test
    void everyWriteAndEverythingWithBankDataNeedsFeesManage() {
        long tierId = createTier();
        long payerId = payer.getId();

        List<Request> managerOnly = List.of(
            new Request(HttpMethod.POST, "/fees/tiers", Map.of("code", "x", "name", "X")),
            new Request(HttpMethod.PATCH, "/fees/tiers/" + tierId, Map.of("name", "Y")),
            new Request(HttpMethod.POST, "/fees/tiers/" + tierId + "/rates",
                Map.of("monthlyAmount", "1", "validFrom", NOW.toString(), "resolutionReference", "U")),
            new Request(HttpMethod.POST, "/fees/members/" + payerId + "/tier",
                Map.of("tierId", tierId, "validFrom", NOW.toString())),
            new Request(HttpMethod.POST, "/fees/members/" + payerId + "/adjustments",
                Map.of("kind", "EXEMPTION", "from", NOW.toString(), "reasonCategory", "OTHER", "justification", "x")),
            new Request(HttpMethod.POST, "/fees/adjustments/1/revoke", Map.of()),
            new Request(HttpMethod.POST, "/fees/members/" + payerId + "/payments",
                Map.of("amount", "1", "paidOn", NOW.atDay(1).toString(), "source", "CASH")),
            new Request(HttpMethod.POST, "/fees/payments/1/reverse", Map.of("reason", "x")),
            new Request(HttpMethod.GET, "/fees/members/" + payerId + "/ibans", null),
            new Request(HttpMethod.POST, "/fees/members/" + payerId + "/ibans",
                Map.of("iban", "PL61109010140000071219812874")),
            new Request(HttpMethod.POST, "/fees/ibans/forget", Map.of("iban", "PL61109010140000071219812874")),
            new Request(HttpMethod.GET, "/fees/statements", null),
            new Request(HttpMethod.GET, "/fees/transactions", null),
            new Request(HttpMethod.GET, "/fees/transactions/1", null),
            new Request(HttpMethod.POST, "/fees/transactions/1/assign", Map.of("splits", List.of())),
            new Request(HttpMethod.POST, "/fees/transactions/1/ignore", Map.of()),
            new Request(HttpMethod.POST, "/fees/transactions/1/reopen", Map.of())
        );

        for (TMember caller : List.of(secretary, auditor, coordinator, districtTreasurer, plain, payer)) {
            for (Request request : managerOnly) {
                Res res = call(request.method(), request.path(),
                    token(sub(caller), caller.getDomainEmail()), request.body());

                assertThat(res.status()).as("%s %s %s", caller.getPreferredNameLast(), request.method(),
                    request.path()).isEqualTo(403);
            }

            for (String path : List.of("/fees/statements", "/fees/statements/preview")) {
                assertThat(upload(caller, path, "x".getBytes(StandardCharsets.UTF_8)).status()).as(path)
                    .isEqualTo(403);
            }
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fee_tiers", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fee_payments", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bank_statement_imports", Integer.class)).isZero();
    }

    private record Request(HttpMethod method, String path, Object body) {
    }

    @Test
    void readingNeedsFeesReadAndDistrictRolesStayInTheirDistrict() {
        long tierId = createTier();
        rate(tierId);
        long slaskie = district("slaskie").getId();
        long mazowieckie = district("mazowieckie").getId();

        for (String path : List.of("/fees/overview", "/fees/arrears", "/fees/tiers", "/fees/tiers/" + tierId + "/rates",
            "/fees/members/" + payer.getId())) {
            assertThat(get(plain, path).status()).as(path).isEqualTo(403);

            for (TMember reader : List.of(treasurer, secretary, auditor, coordinator, districtTreasurer)) {
                assertThat(get(reader, path).status()).as(path).isEqualTo(200);
            }
        }

        assertThat(get(coordinator, "/fees/members/" + outsider.getId()).status()).isEqualTo(403);
        assertThat(get(districtTreasurer, "/fees/members/" + outsider.getId()).status()).isEqualTo(403);
        assertThat(get(otherCoordinator, "/fees/members/" + payer.getId()).status()).isEqualTo(403);
        assertThat(get(otherCoordinator, "/fees/members/" + outsider.getId()).status()).isEqualTo(200);
        assertThat(get(secretary, "/fees/members/" + outsider.getId()).status()).isEqualTo(200);
        assertThat(get(coordinator, "/fees/overview?districtId=" + slaskie).status()).isEqualTo(403);
        assertThat(get(coordinator, "/fees/arrears?districtId=" + slaskie).status()).isEqualTo(403);
        assertThat(get(coordinator, "/fees/overview?districtId=" + mazowieckie).status()).isEqualTo(200);

        // the only member with a due belongs to Mazowieckie
        assertThat(currentMonthMembers(coordinator)).isOne();
        assertThat(currentMonthMembers(otherCoordinator)).isZero();
        assertThat(currentMonthMembers(treasurer)).isOne();
    }

    private int currentMonthMembers(TMember caller) {
        JsonNode months = get(caller, "/fees/overview?months=1").body().path("months");
        return months.get(0).path("members").asInt();
    }

    @Test
    void arrearsAreScopedByDistrict() {
        long tierId = createTier();
        for (TMember member : List.of(payer, outsider)) {
            jdbc.update("INSERT INTO fee_dues (member_id, due_month, amount, tier_id) VALUES (?, ?, 30, ?)",
                member.getId(), NOW.minusMonths(8).atDay(1), tierId);
        }

        assertThat(memberIds(get(coordinator, "/fees/arrears?longerThan=3"))).containsExactly(payer.getId());
        assertThat(memberIds(get(otherCoordinator, "/fees/arrears?longerThan=3"))).containsExactly(outsider.getId());
        assertThat(memberIds(get(auditor, "/fees/arrears?longerThan=3")))
            .containsExactlyInAnyOrder(payer.getId(), outsider.getId());
    }

    private static List<Long> memberIds(Res res) {
        List<Long> ids = new ArrayList<>();
        res.body().forEach(row -> ids.add(row.path("memberId").asLong()));
        return ids;
    }

    @Test
    void reasonsAndBookkeepingAreOnlyForManagersAndReasonsAlsoForTheMember() {
        rate(createTier());
        assertThat(grantExemption(payer).status()).isEqualTo(201);
        assertThat(pay(treasurer, payer).status()).isEqualTo(201);

        for (TMember reader : List.of(secretary, auditor, coordinator, districtTreasurer)) {
            Res card = get(reader, "/fees/members/" + payer.getId());
            JsonNode adjustment = card.body().path("adjustments").get(0);

            assertThat(card.body().path("reasonsIncluded").asBoolean()).isFalse();
            assertThat(adjustment.path("kind").asString()).isEqualTo("DEFERRAL");
            assertThat(adjustment.has("reasonCategory")).isFalse();
            assertThat(adjustment.has("justification")).isFalse();
            assertThat(card.body().path("payments").get(0).has("note")).isFalse();
            assertThat(card.body().toString()).doesNotContain(SECRET_REASON, SECRET_NOTE, "HEALTH");
        }

        JsonNode managed = get(treasurer, "/fees/members/" + payer.getId()).body();
        assertThat(managed.path("adjustments").get(0).path("justification").asString()).isEqualTo(SECRET_REASON);
        assertThat(managed.path("payments").get(0).path("note").asString()).isEqualTo(SECRET_NOTE);

        JsonNode mine = get(payer, "/me/fees").body();
        assertThat(mine.path("history").path("adjustments").get(0).path("justification").asString())
            .isEqualTo(SECRET_REASON);
        assertThat(mine.toString()).doesNotContain(SECRET_NOTE);

        JsonNode somebodyElse = get(plain, "/me/fees").body();
        assertThat(somebodyElse.toString()).doesNotContain(SECRET_REASON, SECRET_NOTE);

        assertThat(auditPayloads("fees :: adjustment-grant")).doesNotContain(SECRET_REASON, "HEALTH");
    }

    @Test
    void aMemberSeesHowToPay() {
        Res mine = get(payer, "/me/fees");

        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.body().path("reference").asString()).isEqualTo("000205");
        assertThat(mine.body().path("payTo").path("title").asString())
            .isEqualTo("000205 SKŁADKA CZŁONKOWSKA STOWARZYSZENIE MŁODZI RAZEM");
    }

    @Test
    void pastRatesAndOwnFeesAreRefused() {
        long tierId = createTier();
        Res past = post(treasurer, "/fees/tiers/" + tierId + "/rates", Map.of("monthlyAmount", "30",
            "validFrom", NOW.minusMonths(1).toString(), "resolutionReference", "U/1"));

        assertThat(past.status()).isEqualTo(422);
        assertThat(past.code()).isEqualTo("RATE_MONTH_IN_PAST");
        assertThat(pay(treasurer, treasurer).code()).isEqualTo("SELF_FEES_NOT_ALLOWED");
        assertThat(grantExemption(treasurer).code()).isEqualTo("SELF_FEES_NOT_ALLOWED");
        assertThat(post(treasurer, "/fees/members/" + payer.getId() + "/payments", Map.of("amount", "1",
            "paidOn", NOW.atDay(1).toString(), "source", "BANK")).status()).isEqualTo(400);
    }

    @Test
    void aStatementIsPreviewedImportedAndItsPaymentsAreUndoneThroughTheTransfer() throws IOException {
        rate(createTier());
        byte[] statement = new ClassPathResource("bankimport/camt053-v02.xml")
            .getContentAsString(StandardCharsets.UTF_8)
            .replace("<Ustrd>Skladka MR-12-3</Ustrd><Ustrd>za marzec</Ustrd>",
                "<Ustrd>000205 SKŁADKA CZŁONKOWSKA STOWARZYSZENIE MŁODZI RAZEM</Ustrd>")
            .getBytes(StandardCharsets.UTF_8);

        Res preview = upload(treasurer, "/fees/statements/preview", statement);

        assertThat(preview.status()).isEqualTo(200);
        assertThat(preview.body().path("totals").path("matchedByReference").asInt()).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fee_payments", Integer.class)).isZero();

        assertThat(upload(treasurer, "/fees/statements", statement).status()).isEqualTo(201);
        assertThat(upload(treasurer, "/fees/statements", statement).code()).isEqualTo("STATEMENT_ALREADY_IMPORTED");
        assertThat(get(treasurer, "/fees/statements").body().path("totalItems").asInt()).isOne();

        JsonNode matched = get(treasurer, "/fees/transactions?status=MATCHED").body().path("items").get(0);
        JsonNode payment = matched.path("payments").get(0);

        assertThat(payment.path("member").path("id").asLong()).isEqualTo(payer.getId());
        assertThat(post(treasurer, "/fees/payments/" + payment.path("paymentId").asLong() + "/reverse",
            Map.of("reason", "x")).code()).isEqualTo("PAYMENT_FROM_BANK_TRANSACTION");

        Res reopened = post(treasurer, "/fees/transactions/" + matched.path("id").asLong() + "/reopen",
            Map.of("reason", "wrong match"));

        assertThat(reopened.body().path("status").asString()).isEqualTo("UNMATCHED");

        String assign = "/fees/transactions/" + matched.path("id").asLong() + "/assign";
        String amount = matched.path("amount").asString();

        assertThat(post(treasurer, assign, Map.of("splits",
            List.of(Map.of("memberId", treasurer.getId(), "amount", amount)))).code())
            .isEqualTo("SELF_FEES_NOT_ALLOWED");
        assertThat(post(treasurer, assign, Map.of("splits",
            List.of(Map.of("memberId", payer.getId(), "amount", amount)))).body().path("status").asString())
            .isEqualTo("MATCHED");
    }

    private Res upload(TMember as, String path, byte[] content) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "statement.xml";
            }
        });

        var entity = RestClient.builder()
                               .requestFactory(new JdkClientHttpRequestFactory())
                               .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                               .build()
                               .post()
                               .uri("http://localhost:" + port + path)
                               .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(sub(as), as.getDomainEmail()))
                               .contentType(MediaType.MULTIPART_FORM_DATA)
                               .body(parts)
                               .retrieve()
                               .toEntity(String.class);
        String text = entity.getBody();

        return new Res(entity.getStatusCode().value(),
            text == null || text.isBlank() ? json.createObjectNode() : json.readTree(text));
    }
}
