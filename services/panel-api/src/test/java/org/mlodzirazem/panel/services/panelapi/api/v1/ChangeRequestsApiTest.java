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
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeRequestsApiTest extends ApiTestSupport {
    TMember member;
    TMember board;
    TMember otherBoard;

    @BeforeEach
    void setUp() {
        member = linked(newMember("Maria", "Wnioskujaca", "mazowieckie"));
        board = linked(newMember("Cora", "Board", "mazowieckie"));
        grant(board, board("mazowieckie"), "COORDINATOR");
        otherBoard = linked(newMember("Olek", "Other", "slaskie"));
        grant(otherBoard, board("slaskie"), "COORDINATOR");
    }

    @Test
    void requestApproveFlowAppliesValues() {
        Res created = post(member, "/me/change-requests", fields("addressLine", "ul. Nowa 2", "formalNameLast", "Zmieniona"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("status").asString()).isEqualTo("PENDING");
        long id = created.body().path("id").asLong();

        assertThat(post(member, "/me/change-requests", fields("addressLine", "again")).code()).isEqualTo("PENDING_EXISTS");
        assertThat(get(member, "/me").body().path("pendingChangeRequest").asBoolean()).isTrue();
        assertThat(get(member, "/me/change-requests").body()).hasSize(1);

        Res inbox = get(board, "/change-requests?status=PENDING");
        assertThat(inbox.body()).hasSize(1);
        assertThat(get(otherBoard, "/change-requests").body()).isEmpty();
        assertThat(get(member, "/change-requests").body()).isEmpty();

        Res detail = get(board, "/change-requests/" + id);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body().path("changes")).hasSize(2);
        assertThat(detail.body().path("changes").get(0).path("field").asString()).isEqualTo("formalNameLast");
        assertThat(detail.body().path("changes").get(1).path("proposed").asString()).isEqualTo("ul. Nowa 2");
        assertThat(get(otherBoard, "/change-requests/" + id).status()).isEqualTo(403);

        assertThat(post(otherBoard, "/change-requests/" + id + "/approve", Map.of()).status()).isEqualTo(403);

        Res approved = post(board, "/change-requests/" + id + "/approve", Map.of("note", "ok"));
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.body().path("status").asString()).isEqualTo("APPROVED");
        TMember reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getAddressLine()).isEqualTo("ul. Nowa 2");
        assertThat(reloaded.getFormalNameLast()).isEqualTo("Zmieniona");
        assertThat(get(member, "/me").body().path("pendingChangeRequest").asBoolean(true)).isFalse();
    }

    @Test
    void onlyFormalFieldsCanBeRequested() {
        Res res = post(member, "/me/change-requests", fields("pseudonym", "x"));
        assertThat(res.status()).isEqualTo(422);
        assertThat(res.code()).isEqualTo("NOT_FORMAL_FIELD");
    }

    @Test
    void nobodyDecidesTheirOwnRequest() {
        long id = post(board, "/me/change-requests", fields("addressLine", "mine")).body().path("id").asLong();

        Res res = post(board, "/change-requests/" + id + "/approve", null);

        assertThat(res.status()).isEqualTo(403);
        assertThat(res.code()).isEqualTo("SELF_DECISION_FORBIDDEN");
        assertThat(post(board, "/change-requests/" + id + "/reject", null).code()).isEqualTo("SELF_DECISION_FORBIDDEN");
        assertThat(memberRepository.findById(board.getId()).orElseThrow().getAddressLine()).isNull();
    }

    @Test
    void staleSnapshotIsReportedAndRejectionStillWorks() {
        long id = post(member, "/me/change-requests", fields("addressLine", "proposed")).body().path("id").asLong();
        memberService.update(member.getId(), Map.of(MemberField.ADDRESS_LINE, "changed meanwhile"));

        Res stale = post(board, "/change-requests/" + id + "/approve", Map.of());
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.code()).isEqualTo("STALE_SNAPSHOT");
        assertThat(stale.body().path("fields").get(0).asString()).isEqualTo("addressLine");

        assertThat(get(board, "/change-requests/" + id).body().path("changes").get(0).path("stale").asBoolean()).isTrue();

        Res rejected = post(board, "/change-requests/" + id + "/reject", Map.of("note", "stale"));
        assertThat(rejected.body().path("status").asString()).isEqualTo("REJECTED");
        assertThat(rejected.body().path("decisionNote").asString()).isEqualTo("stale");
    }

    @Test
    void requesterCancels() {
        long id = post(member, "/me/change-requests", fields("addressLine", "x")).body().path("id").asLong();

        assertThat(delete(board, "/me/change-requests/" + id).status()).isEqualTo(403);

        Res cancelled = delete(member, "/me/change-requests/" + id);
        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.body().path("status").asString()).isEqualTo("CANCELLED");
        assertThat(delete(member, "/me/change-requests/" + id).status()).isEqualTo(409);
    }
}
