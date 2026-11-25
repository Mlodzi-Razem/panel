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

package org.mlodzirazem.panel.services.panelapi.fees;

import org.mlodzirazem.panel.services.panelapi.fees.*;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.fee.*;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@PostgresTest
class SenderIbansTest extends FeesTestSupport {
    private static final String IBAN = "PL61109010140000071219812874";

    @Autowired
    SenderIbans ibans;

    @Test
    void confirmsNormalisedIbansAndLooksThemUp() {
        actor = newMember("Tre", "Asurer", "mazowieckie");
        TMember member = newMember("Iban", "Owner", "mazowieckie");

        ConfirmedIban confirmed = ibans.confirm(actor.getId(), "pl61 1090 1014 0000 0712 1981 2874", member.getId());

        assertThat(confirmed.iban()).isEqualTo(IBAN);
        assertThat(ibans.memberOf(IBAN)).contains(member.getId());
        assertThat(ibans.memberOf("61109010140000071219812874")).contains(member.getId());
        assertThat(ibans.memberOf("PL00 0000")).isEmpty();
        assertThat(ibans.of(member.getId())).hasSize(1);
        assertThat(ibans.confirm(actor.getId(), IBAN, member.getId()).id()).isEqualTo(confirmed.id());
        assertThat(auditCount("fees :: iban-confirm")).isEqualTo(1);
    }

    @Test
    void anIbanBelongsToExactlyOneMember() {
        actor = newMember("Tre", "Asurer", "mazowieckie");
        TMember a = newMember("A", "One", "mazowieckie");
        TMember b = newMember("B", "Two", "mazowieckie");
        ibans.confirm(actor.getId(), IBAN, a.getId());

        assertThat(codeOf(() -> ibans.confirm(actor.getId(), IBAN, b.getId()))).isEqualTo("IBAN_ALREADY_CONFIRMED");
        assertThat(codeOf(() -> ibans.confirm(actor.getId(), "PL61109010140000071219812875", b.getId()))).isEqualTo("IBAN_INVALID");

        ibans.forget(actor.getId(), IBAN);

        assertThat(ibans.memberOf(IBAN)).isEmpty();
        assertThat(ibans.confirm(actor.getId(), IBAN, b.getId()).memberId()).isEqualTo(b.getId());
    }
}
