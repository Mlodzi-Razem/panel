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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyMembershipStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionHistoryApiTest extends ApiTestSupport {
    private final LocalDate today = LocalDate.now();

    @Test
    void historyKeepsEndedMandatesNewestFirstWithGapsLeftToTheClient() {
        TMember member = linked(newMember("Hanna", "History", "mazowieckie"));
        TBodyMembership zk = grant(member, body("national-board"), "SECRETARY",
            today.minusMonths(30), today.minusMonths(20));
        grant(member, body("electoral-commission"), "MEMBER",
            today.minusMonths(20).plusDays(14), today.minusMonths(6));
        TBodyMembership current = grant(member, body("press-spokesperson"), "SPOKESPERSON",
            today.minusMonths(3), null);

        JsonNode me = get(member, "/me").body().path("member");

        JsonNode history = me.path("functionHistory");
        assertThat(history).hasSize(3);
        assertThat(history.get(0).path("function").path("membershipId").asLong()).isEqualTo(current.getId());
        assertThat(history.get(0).path("state").asString()).isEqualTo("CURRENT");
        assertThat(history.get(1).path("function").path("bodyName").asString()).isNotBlank();
        assertThat(history.get(1).path("state").asString()).isEqualTo("ENDED");
        assertThat(history.get(2).path("function").path("membershipId").asLong()).isEqualTo(zk.getId());
        assertThat(history.get(2).path("function").path("validTo").asString())
            .isEqualTo(today.minusMonths(20).toString());
        assertThat(history.get(2).path("state").asString()).isEqualTo("ENDED");

        assertThat(me.path("bodyFunctions")).hasSize(1);
        assertThat(me.path("bodyFunctions").get(0).path("membershipId").asLong()).isEqualTo(current.getId());
    }

    @Test
    void voidedMandatesStayOutOfTheHistory() {
        TMember member = linked(newMember("Wiktor", "Voided", "pomorskie"));
        TBodyMembership mistaken = grant(member, body("peer-court"), "MEMBER", today.minusMonths(2), null);
        mistaken.setStatus(BodyMembershipStatus.VOIDED);
        mistaken.setVoidReason("wpis omyłkowy");
        bodyMembershipRepository.saveAndFlush(mistaken);
        TBodyMembership real = grant(member, body("electoral-commission"), "MEMBER",
            today.minusMonths(1), null);

        JsonNode me = get(member, "/me").body().path("member");

        assertThat(me.path("functionHistory")).hasSize(1);
        assertThat(me.path("functionHistory").get(0).path("function").path("membershipId").asLong())
            .isEqualTo(real.getId());
        assertThat(me.path("bodyFunctions")).hasSize(1);
    }

    @Test
    void theSameHistoryIsVisibleToTheBodiesManagerOnTheMemberCard() {
        TMember member = linked(newMember("Julia", "Card", "slaskie"));
        grant(member, body("audit-commission"), "MEMBER", today.minusYears(2), today.minusYears(1));
        TMember secretary = linked(newMember("Sam", "Secretary", "pomorskie"));
        grant(secretary, body("national-board"), "SECRETARY");

        JsonNode card = get(secretary, "/members/" + member.getId()).body();

        assertThat(card.path("bodyFunctions")).isEmpty();
        assertThat(card.path("functionHistory")).hasSize(1);
        assertThat(card.path("functionHistory").get(0).path("state").asString()).isEqualTo("ENDED");
    }
}
