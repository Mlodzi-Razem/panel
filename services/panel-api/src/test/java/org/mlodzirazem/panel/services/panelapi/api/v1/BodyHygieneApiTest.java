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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3 — hygiene of bodies and mandates: duplicate bodies of a limited kind are rejected, a mistaken
 * mandate is voided (never deleted, never confused with ending), a mistaken body can be deleted
 * only when nothing depends on it. The seeded structure is untouchable.
 */
class BodyHygieneApiTest extends ApiTestSupport {
    TMember secretary;
    TMember plain;

    @BeforeEach
    void setUp() {
        secretary = linked(newMember("Sam", "Secretary", "pomorskie"));
        grant(secretary, body("national-board"), "SECRETARY");
        plain = linked(newMember("Pat", "Plain", "slaskie"));
    }

    @Test
    void secondBodiesOfLimitedKindsAreRejectedWith409() {
        Res districtBoard = post(secretary, "/bodies", createRequest(BodyKind.DISTRICT_BOARD,
            "Drugi zarząd okręgu", "test-board-duplicate", district("mazowieckie").getId()));
        assertThat(districtBoard.status()).isEqualTo(409);
        assertThat(districtBoard.code()).isEqualTo("BODY_ALREADY_EXISTS");

        Res nationalBoard = post(secretary, "/bodies", createRequest(BodyKind.NATIONAL_BOARD,
            "Drugi zarząd krajowy", "test-national-duplicate", null));
        assertThat(nationalBoard.status()).isEqualTo(409);
        assertThat(nationalBoard.code()).isEqualTo("BODY_ALREADY_EXISTS");

        Res nationalCouncil = post(secretary, "/bodies", createRequest(BodyKind.NATIONAL_COUNCIL,
            "Druga rada krajowa", "test-council-duplicate", null));
        assertThat(nationalCouncil.status()).isEqualTo(409);
        assertThat(nationalCouncil.code()).isEqualTo("BODY_ALREADY_EXISTS");
        assertThat(auditCount("bodies :: create")).isZero();
    }

    @Test
    void districtKindsAreLimitedPerDistrictAndUnlimitedKindsAreNotLimited() {
        Res commissionerMazovia = post(secretary, "/bodies", createRequest(BodyKind.DISTRICT_COMMISSIONER,
            "Komisarz mazowiecki", "test-commissioner-maz", district("mazowieckie").getId()));
        assertThat(commissionerMazovia.status()).isEqualTo(201);

        Res secondCommissionerMazovia = post(secretary, "/bodies", createRequest(
            BodyKind.DISTRICT_COMMISSIONER, "Drugi komisarz mazowiecki", "test-commissioner-maz-2",
            district("mazowieckie").getId()));
        assertThat(secondCommissionerMazovia.status()).isEqualTo(409);
        assertThat(secondCommissionerMazovia.code()).isEqualTo("COMMISSIONER_BOARD_STILL_OPEN");

        Res commissionerSilesia = post(secretary, "/bodies", createRequest(BodyKind.DISTRICT_COMMISSIONER,
            "Komisarz śląski", "test-commissioner-sla", district("slaskie").getId()));
        assertThat(commissionerSilesia.status()).isEqualTo(201);

        assertThat(post(secretary, "/bodies", createRequest(BodyKind.TEAM, "Zespół jeden", "test-team-one", null))
                       .status()).isEqualTo(201);
        assertThat(post(secretary, "/bodies", createRequest(BodyKind.TEAM, "Zespół dwa", "test-team-two", null))
                       .status()).isEqualTo(201);
        assertThat(post(secretary, "/bodies", createRequest(BodyKind.LOCAL_CIRCLE, "Koło berlin", "test-circle-ber",
            district("zagranica").getId())).status()).isEqualTo(201);
        assertThat(post(secretary, "/bodies", createRequest(BodyKind.LOCAL_CIRCLE, "Koło paryż", "test-circle-par",
            district("zagranica").getId())).status()).isEqualTo(201);
    }

    @Test
    void seededBodiesCannotBeDeleted() {
        Res national = delete(secretary, "/bodies/" + body("national-board").getId());
        assertThat(national.status()).isEqualTo(409);
        assertThat(national.code()).isEqualTo("BODY_IS_SEEDED");

        Res districtBoard = delete(secretary, "/bodies/" + board("slaskie").getId());
        assertThat(districtBoard.status()).isEqualTo(409);
        assertThat(districtBoard.code()).isEqualTo("BODY_IS_SEEDED");
        assertThat(auditCount("bodies :: delete")).isZero();
    }

    @Test
    void voidingAMandateNeedsAReasonAndTheRightBody() {
        long teamId = createTeam("Zespół testowy", "test-team-void");
        long membershipId = assignLeader(teamId, plain);

        Res noReason = post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void", Map.of());
        assertThat(noReason.status()).isEqualTo(422);
        assertThat(noReason.code()).isEqualTo("VOID_REASON_REQUIRED");

        Res blankReason = post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "   "));
        assertThat(blankReason.status()).isEqualTo(422);
        assertThat(blankReason.code()).isEqualTo("VOID_REASON_REQUIRED");

        Res tooLong = post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "x".repeat(501)));
        assertThat(tooLong.status()).isEqualTo(422);
        assertThat(tooLong.code()).isEqualTo("VOID_REASON_TOO_LONG");

        long otherTeamId = createTeam("Inny zespół", "test-team-other");
        Res wrongBody = post(secretary, "/bodies/" + otherTeamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "wrong body"));
        assertThat(wrongBody.status()).isEqualTo(404);

        assertThat(jdbc.queryForObject("SELECT status::text FROM body_memberships WHERE id = ?", String.class,
            membershipId)).isEqualTo("ACTIVE");
    }

    @Test
    void voidingAMandateRemovesItFromFunctionsAndKeepsTheRowAndTheAudit() {
        long teamId = createTeam("Zespół unieważnienia", "test-team-voided");
        long membershipId = assignLeader(teamId, plain);

        Res voided = post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "wpis omyłkowy", "decisionReference", "ZO/MZ/02/2026"));
        assertThat(voided.status()).isEqualTo(200);

        // The row stays — status VOIDED, the history untouched; the mandate never closed though.
        assertThat(jdbc.queryForMap("SELECT status::text AS status, void_reason, void_decision_reference,"
            + " decision_reference, valid_to FROM body_memberships WHERE id = ?", membershipId))
            .containsEntry("status", "VOIDED")
            .containsEntry("void_reason", "wpis omyłkowy")
            .containsEntry("void_decision_reference", "ZO/MZ/02/2026")
            .containsEntry("decision_reference", null)
            .containsEntry("valid_to", null);

        // Gone from the composition, the body's memberships and the member's functions…
        assertThat(get(secretary, "/bodies/" + teamId).body().path("composition")).hasSize(0);
        assertThat(get(secretary, "/bodies/" + teamId + "/memberships").body()).hasSize(0);
        assertThat(get(secretary, "/members/" + plain.getId()).body().path("bodyFunctions")).hasSize(0);

        // …but the audit entry exists, with field names only — never the reason text.
        assertThat(auditCount("bodies :: membership-void")).isEqualTo(1);
        assertThat(auditPayloads("bodies :: membership-void"))
            .contains("\"fields\"")
            .doesNotContain("wpis omyłkowy");

        // A voided mandate is never touched again: neither voided twice nor ended.
        Res again = post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "drugi raz"));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("MEMBERSHIP_ALREADY_VOIDED");

        Res ended = delete(secretary, "/bodies/" + teamId + "/memberships/" + membershipId);
        assertThat(ended.status()).isEqualTo(409);
        assertThat(ended.code()).isEqualTo("MEMBERSHIP_ALREADY_VOIDED");
    }

    @Test
    void theTemplatesSayWhatTheServerWouldRefuseToCreate() {
        Res before = get(secretary, "/bodies/templates");

        assertThat(templateOption(before, BodyKind.NATIONAL_BOARD).path("creatable").asBoolean(true)).isFalse();
        assertThat(templateOption(before, BodyKind.NATIONAL_COUNCIL).path("creatable").asBoolean(true)).isFalse();
        assertThat(templateOption(before, BodyKind.TEAM).path("creatable").asBoolean(false)).isTrue();
        // every district is seeded with its board
        assertThat(templateOption(before, BodyKind.DISTRICT_BOARD).path("creatable").asBoolean(true)).isFalse();
        assertThat(templateOption(before, BodyKind.DISTRICT_COMMISSIONER).path("unavailableParentIds")).isEmpty();

        assertThat(post(secretary, "/bodies", createRequest(BodyKind.DISTRICT_COMMISSIONER, "Komisarz lista",
            "test-commissioner-options", district("mazowieckie").getId())).status()).isEqualTo(201);

        JsonNode commissioner = templateOption(get(secretary, "/bodies/templates"), BodyKind.DISTRICT_COMMISSIONER);
        assertThat(commissioner.path("creatable").asBoolean(false)).isTrue();
        assertThat(commissioner.path("unavailableParentIds")).extracting(JsonNode::asLong)
            .containsExactly(district("mazowieckie").getId());
    }

    private JsonNode templateOption(Res templates, BodyKind kind) {
        for (JsonNode node : templates.body()) {
            if (node.path("kind").asString().equals(kind.name())) {
                return node;
            }
        }

        throw new AssertionError("No template " + kind);
    }

    @Test
    void theVoidedFunctionCanBeAssignedAgain() {
        long teamId = createTeam("Zespół ponowny", "test-team-reassign");
        long membershipId = assignLeader(teamId, plain);
        assertThat(post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "wpis omyłkowy")).status()).isEqualTo(200);

        Res reassigned = post(secretary, "/bodies/" + teamId + "/memberships",
            Map.of("memberId", plain.getId(), "memberTypeId", memberType(BodyKind.TEAM, "LEADER").getId()));

        assertThat(reassigned.status()).isEqualTo(201);
        assertThat(get(secretary, "/bodies/" + teamId).body().path("composition")).hasSize(1);
    }

    @Test
    void voidingTheLastBodiesManagerIsGuarded() {
        TMember zkMember = linked(newMember("Zbyszek", "Krajowy", "slaskie"));
        grant(zkMember, body("national-board"), "MEMBER");
        String path = "/bodies/" + body("national-board").getId() + "/memberships/";

        long zkSeat = jdbc.queryForObject("SELECT id FROM body_memberships WHERE member_id = ?", Long.class,
            zkMember.getId());
        long ownSeat = jdbc.queryForObject("SELECT id FROM body_memberships WHERE member_id = ?", Long.class,
            secretary.getId());

        assertThat(post(secretary, path + zkSeat + "/void", Map.of("reason", "wpis omyłkowy")).status())
            .isEqualTo(200);

        Res refused = post(secretary, path + ownSeat + "/void", Map.of("reason", "wpis omyłkowy"));
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("LAST_BODIES_MANAGER");
        assertThat(jdbc.queryForObject("SELECT status::text FROM body_memberships WHERE id = ?", String.class,
            ownSeat)).isEqualTo("ACTIVE");
    }

    @Test
    void anEndedMandateCanBeVoidedToo() {
        long teamId = createTeam("Zespół zamknięty", "test-team-ended");
        grant(plain, body("test-team-ended"), "LEADER", LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(1));

        long membershipId = jdbc.queryForObject(
            "SELECT id FROM body_memberships WHERE body_id = ?", Long.class, teamId);

        Res voided = post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "caly wpis byl omylkowy"));

        assertThat(voided.status()).isEqualTo(200);
        assertThat(jdbc.queryForMap(
            "SELECT status::text AS status, to_char(valid_to, 'YYYY-MM-DD') AS validTo FROM body_memberships WHERE id = ?",
            membershipId))
            .containsEntry("status", "VOIDED")
            .containsEntry("validTo", LocalDate.now().minusMonths(1).toString());
    }

    @Test
    void deletingABodyIsGuardedByMandatesAndChildren() {
        long teamId = createTeam("Zespół pilnowany", "test-team-guarded");
        grant(plain, body("test-team-guarded"), "LEADER", LocalDate.now().minusMonths(1), null);

        Res withMembership = delete(secretary, "/bodies/" + teamId);
        assertThat(withMembership.status()).isEqualTo(409);
        assertThat(withMembership.code()).isEqualTo("BODY_HAS_MEMBERSHIPS");

        long membershipId = jdbc.queryForObject(
            "SELECT id FROM body_memberships WHERE body_id = ?", Long.class, teamId);
        // Ended is not voided: a mandate that happened is history and still blocks the delete.
        assertThat(delete(secretary, "/bodies/" + teamId + "/memberships/" + membershipId).status()).isEqualTo(200);
        assertThat(delete(secretary, "/bodies/" + teamId).code()).isEqualTo("BODY_HAS_MEMBERSHIPS");

        assertThat(post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "wpis omylkowy")).status()).isEqualTo(200);

        jdbc.update("INSERT INTO bodies (body_template_id, parent_body_id, name, code)"
            + " VALUES (?, ?, 'Zespół podrzędny', 'test-team-child')", template(BodyKind.TEAM).getId(), teamId);
        Res withChild = delete(secretary, "/bodies/" + teamId);
        assertThat(withChild.status()).isEqualTo(409);
        assertThat(withChild.code()).isEqualTo("BODY_HAS_CHILD_BODIES");

        jdbc.update("DELETE FROM bodies WHERE code = 'test-team-child'");
    }

    @Test
    void voidingKeepsTheAppointmentReferenceOfTheMandate() {
        long teamId = createTeam("Zespół sygnatury", "test-team-signature");
        long membershipId = assignLeader(teamId, plain);
        jdbc.update("UPDATE body_memberships SET decision_reference = 'ZK/01/2026' WHERE id = ?", membershipId);

        assertThat(post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "wpis omyłkowy", "decisionReference", "ZO/MZ/02/2026")).status()).isEqualTo(200);

        assertThat(jdbc.queryForMap("SELECT decision_reference, void_decision_reference FROM body_memberships"
            + " WHERE id = ?", membershipId))
            .containsEntry("decision_reference", "ZK/01/2026")
            .containsEntry("void_decision_reference", "ZO/MZ/02/2026");
    }

    @Test
    void deletingABodyWithAVoidedMandateIsRefusedAndKeepsTheRow() {
        long teamId = createTeam("Zespół usuwany", "test-team-removed");
        long membershipId = assignLeader(teamId, plain);
        assertThat(post(secretary, "/bodies/" + teamId + "/memberships/" + membershipId + "/void",
            Map.of("reason", "wpis omyłkowy")).status()).isEqualTo(200);

        Res deleted = delete(secretary, "/bodies/" + teamId);

        assertThat(deleted.status()).isEqualTo(409);
        assertThat(deleted.code()).isEqualTo("BODY_HAS_MEMBERSHIPS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bodies WHERE id = ?", Integer.class, teamId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM body_memberships WHERE id = ? AND status = 'VOIDED'",
            Integer.class, membershipId)).isEqualTo(1);
        assertThat(auditCount("bodies :: membership-void")).isEqualTo(1);
        assertThat(auditCount("bodies :: delete")).isZero();
    }

    private Map<String, Object> createRequest(BodyKind kind, String name, String code, Long parentBodyId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", template(kind).getId());
        request.put("name", name);
        request.put("code", code);
        if (parentBodyId != null) {
            request.put("parentBodyId", parentBodyId);
        }
        return request;
    }

    private long createTeam(String name, String code) {
        Res created = post(secretary, "/bodies", createRequest(BodyKind.TEAM, name, code, null));
        assertThat(created.status()).isEqualTo(201);
        return created.body().path("id").asLong();
    }

    private long assignLeader(long bodyId, TMember member) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("memberId", member.getId());
        request.put("memberTypeId", memberType(BodyKind.TEAM, "LEADER").getId());

        Res assigned = post(secretary, "/bodies/" + bodyId + "/memberships", request);
        assertThat(assigned.status()).isEqualTo(201);
        return assigned.body().path("membership").path("id").asLong();
    }
}
