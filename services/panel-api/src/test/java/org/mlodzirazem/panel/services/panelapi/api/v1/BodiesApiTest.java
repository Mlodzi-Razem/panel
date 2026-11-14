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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BodiesApiTest extends ApiTestSupport {
    TMember secretary;
    TMember zkMember;
    TMember plain;

    @BeforeEach
    void setUp() {
        secretary = linked(newMember("Sam", "Secretary", "pomorskie"));
        grant(secretary, body("national-board"), "SECRETARY");
        zkMember = newMember("Zbyszek", "Krajowy", "slaskie");
        grant(zkMember, body("national-board"), "VICE_CHAIR");
        plain = linked(newMember("Pat", "Plain", "slaskie"));
    }

    @Test
    void everyLinkedUserSeesBodiesDistrictsAndComposition() {
        Res list = get(plain, "/bodies");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().size()).isGreaterThan(20);

        Res detail = get(plain, "/bodies/" + body("national-board").getId());
        assertThat(detail.body().path("body").path("kind").asString()).isEqualTo("NATIONAL_BOARD");
        assertThat(detail.body().path("composition")).hasSize(2);
        assertThat(detail.body().path("composition").toString()).contains("Sam Secretary").doesNotContain("formal");
        assertThat(detail.body().path("memberTypes").size()).isGreaterThan(2);

        assertThat(get(plain, "/bodies/templates").body().size()).isGreaterThan(5);
        assertThat(get(plain, "/bodies/templates").body().get(0).path("memberTypes")).isNotNull();

        Res districts = get(secretary, "/districts");
        assertThat(districts.body()).hasSize(17);
        assertThat(districts.body().get(0).path("canAct").asBoolean()).isTrue();
        assertThat(get(plain, "/districts").body().get(0).path("canAct").asBoolean(true)).isFalse();
    }

    @Test
    void conflictNeedsConfirmationThenEndsTheEarlierMandate() {
        long boardId = board("mazowieckie").getId();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", zkMember.getId());
        request.put("memberTypeId", memberType(BodyKind.DISTRICT_BOARD, "COORDINATOR").getId());
        String path = "/bodies/" + boardId + "/memberships";

        Res check = post(secretary, path + "/check", request);
        assertThat(check.status()).isEqualTo(200);
        assertThat(check.body().path("conflicts")).hasSize(1);

        Res refused = post(secretary, path, request);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(refused.body().path("check").path("conflicts")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE valid_to IS NOT NULL", Integer.class))
            .isZero();

        request.put("confirmed", true);
        Res assigned = post(secretary, path, request);
        assertThat(assigned.status()).isEqualTo(201);
        assertThat(assigned.body().path("endedMemberships")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE valid_to IS NOT NULL", Integer.class))
            .isEqualTo(1);

        assertThat(get(plain, "/bodies/" + boardId).body().path("composition").toString()).contains("Zbyszek Krajowy");
        assertThat(get(secretary, path).body()).hasSize(1);

        long membershipId = assigned.body().path("membership").path("id").asLong();
        Res ended = delete(secretary, path + "/" + membershipId + "?endDate=" + LocalDate.now().plusDays(1));
        assertThat(ended.status()).isEqualTo(200);
        assertThat(delete(secretary, "/bodies/" + body("national-board").getId() + "/memberships/" + membershipId)
            .status()).isEqualTo(404);
    }

    @Test
    void violationsAreUnprocessableAndManagersOnly() {
        long boardId = board("mazowieckie").getId();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", plain.getId());
        request.put("memberTypeId", memberType(BodyKind.DISTRICT_BOARD, "COORDINATOR").getId());
        request.put("validFrom", LocalDate.now().plusYears(5).toString());
        request.put("validTo", LocalDate.now().plusYears(4).toString());

        Res res = post(secretary, "/bodies/" + boardId + "/memberships", request);
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.code()).isEqualTo("INVALID_PERIOD");

        assertThat(post(plain, "/bodies/" + boardId + "/memberships", request).status()).isEqualTo(403);
        assertThat(post(plain, "/bodies", Map.of("templateId", 1, "name", "x")).status()).isEqualTo(403);
    }

    @Test
    void nationalBoardCreatesAndRenamesBodies() {
        long templateId = template(BodyKind.TEAM).getId();

        Res created = post(secretary, "/bodies", Map.of("templateId", templateId, "name", "Test team"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("name").asString()).isEqualTo("Test team");

        Res renamed = patch(secretary, "/bodies/" + created.body().path("id").asLong(), Map.of("name", "Renamed"));
        assertThat(renamed.body().path("name").asString()).isEqualTo("Renamed");
        assertThat(post(secretary, "/bodies", Map.of("name", "x")).code()).isEqualTo("FIELD_REQUIRED");
    }
}
