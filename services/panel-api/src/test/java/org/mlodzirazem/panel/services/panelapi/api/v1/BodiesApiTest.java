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
    void zkMemberAssignedToAZoBoardWhileHoldingZkIsRejectedWith422() {
        long boardId = board("mazowieckie").getId();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", zkMember.getId());
        request.put("memberTypeId", memberType(BodyKind.DISTRICT_BOARD, "COORDINATOR").getId());
        String path = "/bodies/" + boardId + "/memberships";

        Res check = post(secretary, path + "/check", request);
        assertThat(check.status()).isEqualTo(200);
        assertThat(check.body().path("violations")).hasSize(1);
        assertThat(check.body().path("violations").get(0).path("code").asText()).isEqualTo("INCOMPATIBLE_MEMBERSHIP");
        assertThat(check.body().path("violations").get(0).path("conflictingBodyName").asText())
            .isEqualTo(body("national-board").getName());
        assertThat(check.body().path("violations").get(0).path("conflictingMembershipId").asLong())
            .isGreaterThan(0);

        Res refused = post(secretary, path, request);
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("INCOMPATIBLE_MEMBERSHIP");
        assertThat(refused.body().path("check").path("violations")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE valid_to IS NOT NULL", Integer.class))
            .isZero();

        request.put("confirmed", true);
        assertThat(post(secretary, path, request).status()).isEqualTo(422);

        assertThat(get(secretary, "/bodies/" + boardId + "/memberships").body()).isEmpty();
    }

    @Test
    void bodiesManagersCannotAssignFunctionsToThemselves() {
        long boardId = board("mazowieckie").getId();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", zkMember.getId());
        request.put("memberTypeId", memberType(BodyKind.DISTRICT_BOARD, "COORDINATOR").getId());

        Res res = post(zkMember, "/bodies/" + boardId + "/memberships", request);

        assertThat(res.status()).isEqualTo(422);
        assertThat(res.code()).isEqualTo("SELF_ASSIGNMENT_NOT_ALLOWED");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM body_memberships WHERE member_id = ? AND body_id = ?", Integer.class,
            zkMember.getId(), boardId)).isZero();
    }

    @Test
    void endingAMembershipInPastRequiresTheDecisionReference() {
        long spokespersonId = body("press-spokesperson").getId();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", plain.getId());
        request.put("memberTypeId", memberType(BodyKind.PRESS_SPOKESPERSON, "SPOKESPERSON").getId());
        request.put("validFrom", LocalDate.now().minusDays(10).toString());
        String path = "/bodies/" + spokespersonId + "/memberships";

        Res assigned = post(secretary, path, request);
        assertThat(assigned.status()).isEqualTo(201);

        long membershipId = assigned.body().path("membership").path("id").asLong();

        Res retro = delete(secretary, path + "/" + membershipId + "?endDate=" + LocalDate.now().minusDays(5));
        assertThat(retro.status()).isEqualTo(422);
        assertThat(retro.code()).isEqualTo("RETROACTIVE_CORRECTION_REQUIRES_REASON");

        Res corrected = delete(
            secretary, path + "/" + membershipId + "?endDate=" + LocalDate.now().minusDays(5)
                + "&decisionReference=ZK/03/2026");
        assertThat(corrected.status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT decision_reference FROM body_memberships WHERE id = ?", String.class,
            membershipId)).isEqualTo("ZK/03/2026");
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

    @Test
    void endingTheLastNationalBoardSeatIsUnprocessable() {
        String path = "/bodies/" + body("national-board").getId() + "/memberships/";
        long zkSeat = jdbc.queryForObject("SELECT id FROM body_memberships WHERE member_id = ?", Long.class,
            zkMember.getId());
        long ownSeat = jdbc.queryForObject("SELECT id FROM body_memberships WHERE member_id = ?", Long.class,
            secretary.getId());

        assertThat(delete(secretary, path + zkSeat).status()).isEqualTo(200);

        Res refused = delete(secretary, path + ownSeat);
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("LAST_BODIES_MANAGER");
        assertThat(jdbc.queryForObject("SELECT valid_to FROM body_memberships WHERE id = ?", java.time.LocalDate.class,
            ownSeat)).isNull();
    }
}
