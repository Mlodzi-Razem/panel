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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import java.util.HashMap;
import static org.assertj.core.api.Assertions.assertThat;

class SuspensionsApiTest extends ApiTestSupport {
    TMember zoA;
    TMember zoB;
    TMember treasurerA;
    TMember inA;

    @BeforeEach
    void setUp() {
        zoA = linked(newMember("Zofia", "Okregowa", "mazowieckie"));
        grant(zoA, board("mazowieckie"), "COORDINATOR");
        zoB = linked(newMember("Zenon", "Slaski", "slaskie"));
        grant(zoB, board("slaskie"), "COORDINATOR");
        treasurerA = linked(newMember("Tina", "Cash", "mazowieckie"));
        grant(treasurerA, board("mazowieckie"), "TREASURER");
        inA = newMember("Anna", "Mazowiecka", "mazowieckie");
    }

    private Map<String, Object> suspendBody(long bodyId, List<String> confirmed) {
        return Map.of("kind", "ARREARS", "scope", "FULL", "suspendingBodyId", bodyId,
            "decisionSignature", "ZO/MZ/API/1", "start", LocalDate.now().toString(),
            "decisionDate", LocalDate.now().minusDays(1).toString(),
            "confirmedWarnings", confirmed);
    }

    @Test
    void onlyTheDistrictBoardOfTheMembersDistrictSuspends() {
        assertThat(post(zoB, "/members/" + inA.getId() + "/suspensions", suspendBody(board("slaskie").getId(), List.of()))
            .status()).isEqualTo(403);
        assertThat(post(treasurerA, "/members/" + inA.getId() + "/suspensions",
            suspendBody(board("mazowieckie").getId(), List.of())).status()).isEqualTo(403);
    }

    @Test
    void warningsAnswerWith409AndTheConfirmedRetryIsCreated() {
        String path = "/members/" + inA.getId() + "/suspensions";

        var first = post(zoA, path, suspendBody(board("mazowieckie").getId(), List.of()));
        assertThat(first.status()).isEqualTo(409);
        assertThat(first.code()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(first.body().path("check").path("warnings").size()).isPositive();
        assertThat(auditCount("suspensions :: suspend")).isZero();

        var created = post(zoA, path, suspendBody(board("mazowieckie").getId(), List.of("ARREARS_BELOW_THRESHOLD")));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("state").asString()).isEqualTo("ACTIVE");

        var overview = get(zoA, path);
        assertThat(overview.status()).isEqualTo(200);
        assertThat(overview.body().path("items").size()).isEqualTo(1);
    }

    @Test
    void endingMandatesNeedsBodiesManage() {
        long id = post(zoA, "/members/" + inA.getId() + "/suspensions",
            suspendBody(board("mazowieckie").getId(), List.of("ARREARS_BELOW_THRESHOLD"))).body().path("id").asLong();

        var res = post(zoA, "/members/" + inA.getId() + "/suspensions/" + id + "/end-mandates",
            Map.of("membershipIds", List.of(1), "decisionReference", "UCH/1"));
        assertThat(res.status()).isEqualTo(403);
        assertThat(res.code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void patchCannotSetTheSuspendedStatus() {
        var res = patch(zoA, "/members/" + inA.getId(), Map.of("fields", Map.of("status", "SUSPENDED")));
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.code()).isEqualTo("USE_SUSPENSION_OPERATION");
    }

    @Test
    void theSuspensionMustBelongToTheMemberInThePath() {
        long id = post(zoA, "/members/" + inA.getId() + "/suspensions",
            suspendBody(board("mazowieckie").getId(), List.of("ARREARS_BELOW_THRESHOLD"))).body().path("id").asLong();
        TMember other = newMember("Beata", "Other", "mazowieckie");

        assertThat(get(zoA, "/members/" + other.getId() + "/suspensions/" + id + "/position-loss").status()).isEqualTo(404);
    }

    @Test
    void decisionDateIsRequiredAndReturnedWithTheEntry() {
        String path = "/members/" + inA.getId() + "/suspensions";
        Map<String, Object> withoutDate = new HashMap<>(suspendBody(board("mazowieckie").getId(),
            List.of("ARREARS_BELOW_THRESHOLD")));
        withoutDate.remove("decisionDate");

        var refused = post(zoA, path, withoutDate);
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("DECISION_DATE_REQUIRED");

        var created = post(zoA, path, suspendBody(board("mazowieckie").getId(), List.of("ARREARS_BELOW_THRESHOLD")));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("decisionDate").asString()).isEqualTo(LocalDate.now().minusDays(1).toString());
    }
}
