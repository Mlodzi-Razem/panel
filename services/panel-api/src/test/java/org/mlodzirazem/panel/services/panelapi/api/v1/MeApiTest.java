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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeApiTest extends ApiTestSupport {
    @Test
    void unauthenticatedRequestsStayUnauthorized() {
        assertThat(call(org.springframework.http.HttpMethod.GET, "/me", null, null).status()).isEqualTo(401);
        assertThat(call(org.springframework.http.HttpMethod.GET, "/members", null, null).status()).isEqualTo(401);
    }

    @Test
    void unlinkedUserSeesOnlyTheUnlinkedMarker() {
        String token = token("nobody-sub", "nobody@nowhere.org");

        Res me = call(org.springframework.http.HttpMethod.GET, "/me", token, null);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().path("linked").asBoolean(true)).isFalse();
        assertThat(me.body().size()).isEqualTo(1);

        Res members = call(org.springframework.http.HttpMethod.GET, "/members", token, null);
        assertThat(members.status()).isEqualTo(403);
        assertThat(members.code()).isEqualTo("NOT_LINKED");
        assertThat(call(org.springframework.http.HttpMethod.GET, "/bodies", token, null).code()).isEqualTo("NOT_LINKED");
    }

    @Test
    void linksByDomainEmailIgnoringCase() {
        TMember member = newMember("Ala", "Linked", "mazowieckie");
        String token = token("google-sub-1", member.getDomainEmail().toUpperCase());

        Res me = call(org.springframework.http.HttpMethod.GET, "/me", token, null);

        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().path("linked").asBoolean()).isTrue();
        assertThat(me.body().path("member").path("id").asLong()).isEqualTo(member.getId());
        assertThat(jdbc.queryForObject(
            "SELECT subject FROM member_identities WHERE member_id = ? AND provider = 'GOOGLE'", String.class,
            member.getId())).isEqualTo("google-sub-1");
        assertThat(auditCount("members :: link-user")).isEqualTo(1);

        assertThat(call(org.springframework.http.HttpMethod.GET, "/me", token, null).body().path("linked").asBoolean())
            .isTrue();
        assertThat(auditCount("members :: link-user")).isEqualTo(1);
    }

    @Test
    void returnsOwnRecordMaskedAndPermissions() {
        TMember coordinator = linked(newMember("Cora", "Board", "mazowieckie"));
        memberService.update(coordinator.getId(), Map.of(MemberField.FORMAL_IDENTIFIER, "90010112345"));
        grant(coordinator, board("mazowieckie"), "COORDINATOR");
        TMember secretary = linked(newMember("Sam", "Secretary", "slaskie"));
        grant(secretary, body("national-board"), "SECRETARY");

        Res me = get(coordinator, "/me");

        assertThat(me.body().path("member").path("fields").path("formalIdentifier").asString()).isEqualTo("*******2345");
        assertThat(me.body().path("member").path("fields").path("formalNameFirst").asString()).isEqualTo("Cora");
        assertThat(me.body().path("canSeeMembers").asBoolean()).isTrue();
        assertThat(me.body().path("canManageBodies").asBoolean()).isFalse();
        assertThat(me.body().path("pendingChangeRequest").asBoolean(true)).isFalse();
        assertThat(me.body().path("permissions").path("perDistrict")).hasSize(1);
        assertThat(me.body().path("permissions").path("perDistrict").get(0).path("permissions").toString())
            .contains("FORMAL_PERSONAL_DATA_MANAGE");
        assertThat(me.body().path("member").path("bodyFunctions")).hasSize(1);

        Res zk = get(secretary, "/me");
        assertThat(zk.body().path("canManageBodies").asBoolean()).isTrue();
        assertThat(zk.body().path("permissions").path("global").toString()).contains("BODIES_MANAGE");
        assertThat(zk.body().path("permissions").path("perDistrict").size()).isGreaterThan(10);

        TMember plain = linked(newMember("Pat", "Plain", "slaskie"));
        assertThat(get(plain, "/me").body().path("canSeeMembers").asBoolean(true)).isFalse();
    }

    @Test
    void selfEditAllowsContactAndNamingOnly() {
        TMember member = linked(newMember("Edek", "Edit", "mazowieckie"));

        Res ok = patch(member, "/me", fields("pseudonym", "Zed", "phoneNumbers", List.of("123", "456")));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().path("fields").path("pseudonym").asString()).isEqualTo("Zed");
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getPhoneNumbers()).containsExactly("123", "456");
        assertThat(auditCount("members :: update")).isEqualTo(1);

        Res denied = patch(member, "/me", fields("status", "REMOVED", "formalNameFirst", "X", "pseudonym", "Q"));
        assertThat(denied.status()).isEqualTo(400);
        assertThat(denied.code()).isEqualTo("FIELD_NOT_EDITABLE");
        assertThat(Arrays.asList(denied.body().path("fields").get(0).asString(), denied.body().path("fields").get(1).asString()))
            .containsExactly("formalNameFirst", "status");
        TMember reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getPseudonym()).isEqualTo("Zed");
        assertThat(reloaded.getFormalNameFirst()).isEqualTo("Edek");

        assertThat(patch(member, "/me", fields("nonsense", "x")).code()).isEqualTo("UNKNOWN_FIELD");
    }
}
