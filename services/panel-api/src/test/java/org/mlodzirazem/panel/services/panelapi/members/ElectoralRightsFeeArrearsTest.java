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

package org.mlodzirazem.panel.services.panelapi.members;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@PostgresTest
class ElectoralRightsFeeArrearsTest extends DomainTestSupport {
    @Autowired
    ElectoralRightsService service;

    @MockitoBean
    FeeArrearsPolicy policy;

    @Test
    void feeArrearsPolicyRemovesElectoralRights() {
        TMember inArrears = newMember("Arr", "Ears", "mazowieckie");
        TMember paid = newMember("Pai", "D", "mazowieckie");
        when(policy.hasUnjustifiedArrears(any(), any())).thenAnswer(i -> ((TMember) i.getArgument(0)).getId().equals(inArrears.getId()));

        assertThat(service.rightsOf(inArrears.getId()).feeArrears()).isTrue();
        assertThat(service.rightsOf(inArrears.getId()).eligible()).isFalse();
        assertThat(service.nationalElectorate()).extracting(TMember::getId).containsExactly(paid.getId());
        assertThat(service.districtElectorate(district("mazowieckie").getId())).extracting(TMember::getId)
                                                                               .containsExactly(paid.getId());
    }
}
