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
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipActsApiTest extends ApiTestSupport {
    private final String today = LocalDate.now().toString();

    TMember zoA;
    TMember zk;
    TMember readerA;
    TMember inA;

    @BeforeEach
    void setUp() {
        zoA = linked(newMember("Zofia", "Okregowa", "mazowieckie"));
        grant(zoA, board("mazowieckie"), "COORDINATOR");
        zk = linked(newMember("Karol", "Krajowy", "pomorskie"));
        grant(zk, body("national-board"), "SECRETARY");
        readerA = linked(newMember("Tola", "Czytelnik", "mazowieckie"));
        grantDistrictReader(readerA, "mazowieckie");
        inA = newMember("Anna", "Mazowiecka", "mazowieckie");
        memberService.update(inA.getId(), Map.of(MemberField.FORMAL_IDENTIFIER, "90010112345"));
    }

    @Test
    void supportingGrantIsRecordedAndNeedsManageInTheTargetDistrict() {
        Map<String, Object> body = newPersonBody("Wspierajaca", LocalDate.now().minusYears(20).toString());
        body.put("primaryDistrictId", district("mazowieckie").getId());

        var created = post(zoA, "/members/supporting", body);
        assertThat(created.status()).isEqualTo(201);

        long id = created.body().path("id").asLong();
        var history = get(zoA, "/members/" + id + "/membership-acts");
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body().get(0).path("kind").asString()).isEqualTo("SUPPORTING_GRANT");
        assertThat(history.body().get(0).path("decisionReference").asString()).isEqualTo("UCHW-2026-1");

        body.put("primaryDistrictId", district("slaskie").getId());
        assertThat(post(zoA, "/members/supporting", body).status()).isEqualTo(403);
    }

    @Test
    void honoraryGrantNeedsTheNationalPermission() {
        Map<String, Object> body = newPersonBody("Honorowa", LocalDate.now().minusYears(60).toString());
        body.put("primaryDistrictId", district("mazowieckie").getId());

        assertThat(post(zoA, "/members/honorary", body).status()).isEqualTo(403);
        assertThat(post(zk, "/members/honorary", body).status()).isEqualTo(201);
    }

    @Test
    void removalReinstatementAndTheGeneralPatchFollowTheStatute() {
        String removal = "/members/" + inA.getId() + "/removal";
        assertThat(post(zoA, removal, Map.of("ground", "RESIGNATION", "removedOn", today,
            "decisionReference", "UCHW-2026-3")).status()).isEqualTo(200);

        var again = post(zoA, removal, Map.of("ground", "RESIGNATION", "removedOn", today,
            "decisionReference", "UCHW-2026-4"));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("ALREADY_REMOVED");

        var unknown = post(zoA, removal, Map.of("ground", "NOPE", "removedOn", today,
            "decisionReference", "UCHW-2026-5"));
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.code()).isEqualTo("INVALID_VALUE");

        var patch = patch(zoA, "/members/" + inA.getId(), fields("status", "ACTIVE"));
        assertThat(patch.status()).isEqualTo(409);
        assertThat(patch.code()).isEqualTo("MEMBERSHIP_ACT_REQUIRED");

        String reinstatement = "/members/" + inA.getId() + "/reinstatement";
        var noReason = post(zoA, reinstatement, Map.of("effectiveOn", today, "decisionReference", "UCHW-2026-6"));
        assertThat(noReason.status()).isEqualTo(400);
        assertThat(noReason.code()).isEqualTo("FIELD_REQUIRED");

        assertThat(post(zoA, reinstatement, Map.of("effectiveOn", today, "decisionReference", "UCHW-2026-7",
            "reason", "pomyłka w uchwale")).status()).isEqualTo(200);

        var history = get(zoA, "/members/" + inA.getId() + "/membership-acts");
        assertThat(history.body()).hasSize(2);
        assertThat(history.body().get(0).path("kind").asString()).isEqualTo("REINSTATEMENT");
        assertThat(history.body().get(1).path("removalGround").asString()).isEqualTo("RESIGNATION");

        assertThat(get(readerA, "/members/" + inA.getId() + "/membership-acts").status()).isEqualTo(403);
    }

    @Test
    void membershipTypeChangeIsAnActAndThePlainPatchIsRefused() {
        String path = "/members/" + inA.getId() + "/membership-type";
        assertThat(post(readerA, path, Map.of("membershipType", "SUPPORTING", "effectiveOn", today,
            "decisionReference", "UCHW-2026-8", "reason", "zmiana rodzaju")).status()).isEqualTo(403);

        assertThat(post(zoA, path, Map.of("membershipType", "SUPPORTING", "effectiveOn", today,
            "decisionReference", "UCHW-2026-8", "reason", "zmiana rodzaju")).status()).isEqualTo(200);

        var history = get(zoA, "/members/" + inA.getId() + "/membership-acts");
        assertThat(history.body().get(0).path("kind").asString()).isEqualTo("MEMBERSHIP_TYPE_CHANGE");
        assertThat(history.body().get(0).path("membershipTypeAfter").asString()).isEqualTo("SUPPORTING");

        var patch = patch(zoA, "/members/" + inA.getId(), fields("membershipType", "ORDINARY"));
        assertThat(patch.status()).isEqualTo(409);
        assertThat(patch.code()).isEqualTo("MEMBERSHIP_ACT_REQUIRED");
    }

    private static Map<String, Object> newPersonBody(String name, String birthDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.putAll(fields("preferredNameFirst", name, "preferredNameLast", "Osoba", "formalNameFirst", name,
            "formalNameLast", "Osoba", "birthDate", birthDate));
        body.put("grantedOn", LocalDate.now().toString());
        body.put("decisionReference", "UCHW-2026-1");
        return body;
    }
}
