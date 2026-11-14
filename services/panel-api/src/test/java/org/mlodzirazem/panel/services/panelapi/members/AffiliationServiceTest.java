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
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class AffiliationServiceTest extends DomainTestSupport {
    @Autowired
    AffiliationService service;

    @Test
    void newMemberHasOnePrimaryDistrictAndVotesThere() {
        TMember member = newMember("Ann", "One", "mazowieckie");

        assertThat(service.primaryDistrict(member.getId())).map(b -> b.getId()).contains(district("mazowieckie").getId());
        assertThat(service.votingDistrict(member.getId())).map(b -> b.getId()).contains(district("mazowieckie").getId());
        assertInvariants(member);
    }

    @Test
    void supportingDistrictWithoutVotesKeepsVotingInPrimary() {
        TMember member = newMember("Ann", "Two", "mazowieckie");

        service.addSupportingDistrict(member.getId(), district("slaskie").getId(), false);

        MemberDistricts districts = service.districtsOf(member.getId());
        assertThat(districts.primaryDistrictId()).isEqualTo(district("mazowieckie").getId());
        assertThat(districts.supportingDistrictIds()).containsExactly(district("slaskie").getId());
        assertThat(service.votingDistrict(member.getId())).map(b -> b.getId()).contains(district("mazowieckie").getId());
        assertInvariants(member);
    }

    @Test
    void votingInASupportingDistrictMovesTheFlag() {
        TMember member = newMember("Ann", "Three", "mazowieckie");

        service.addSupportingDistrict(member.getId(), district("slaskie").getId(), true);

        assertThat(service.votingDistrict(member.getId())).map(b -> b.getId()).contains(district("slaskie").getId());
        assertInvariants(member);

        service.setVotingDistrict(member.getId(), district("mazowieckie").getId());

        assertThat(service.votingDistrict(member.getId())).map(b -> b.getId()).contains(district("mazowieckie").getId());
        assertInvariants(member);
    }

    @Test
    void endingTheVotingSupportingDistrictReturnsVotesToPrimary() {
        TMember member = newMember("Ann", "Four", "mazowieckie");
        service.addSupportingDistrict(member.getId(), district("slaskie").getId(), true);

        service.endSupportingDistrict(member.getId(), district("slaskie").getId());

        assertThat(service.districtsOf(member.getId()).supportingDistrictIds()).isEmpty();
        assertThat(service.votingDistrict(member.getId())).map(b -> b.getId()).contains(district("mazowieckie").getId());
        assertInvariants(member);
    }

    @Test
    void movingThePrimaryDistrictTakesOverVotesAndOptionallyKeepsTheOldOneSupporting() {
        TMember member = newMember("Ann", "Five", "mazowieckie");

        service.setPrimaryDistrict(member.getId(), district("slaskie").getId(), true);

        MemberDistricts districts = service.districtsOf(member.getId());
        assertThat(districts.primaryDistrictId()).isEqualTo(district("slaskie").getId());
        assertThat(districts.supportingDistrictIds()).containsExactly(district("mazowieckie").getId());
        assertThat(service.votingDistrict(member.getId())).map(b -> b.getId()).contains(district("slaskie").getId());
        assertInvariants(member);

        service.setPrimaryDistrict(member.getId(), district("opolskie").getId(), false);

        districts = service.districtsOf(member.getId());
        assertThat(districts.primaryDistrictId()).isEqualTo(district("opolskie").getId());
        assertThat(districts.supportingDistrictIds()).containsExactly(district("mazowieckie").getId());
        assertInvariants(member);
    }

    @Test
    void movingPrimaryKeepsVotesInASupportingDistrict() {
        TMember member = newMember("Ann", "Six", "mazowieckie");
        service.addSupportingDistrict(member.getId(), district("lubelskie").getId(), true);

        service.setPrimaryDistrict(member.getId(), district("slaskie").getId(), false);

        assertThat(service.votingDistrict(member.getId())).map(b -> b.getId()).contains(district("lubelskie").getId());
        assertInvariants(member);
    }

    @Test
    void promotingASupportingDistrictToPrimary() {
        TMember member = newMember("Ann", "Seven", "mazowieckie");
        service.addSupportingDistrict(member.getId(), district("slaskie").getId(), true);

        service.setPrimaryDistrict(member.getId(), district("slaskie").getId(), false);

        MemberDistricts districts = service.districtsOf(member.getId());
        assertThat(districts.primaryDistrictId()).isEqualTo(district("slaskie").getId());
        assertThat(districts.supportingDistrictIds()).isEmpty();
        assertThat(service.votingDistrict(member.getId())).map(b -> b.getId()).contains(district("slaskie").getId());
        assertInvariants(member);
    }

    @Test
    void rejectsInvalidChanges() {
        TMember member = newMember("Ann", "Eight", "mazowieckie");
        long board = board("mazowieckie").getId();

        assertThatThrownBy(() -> service.addSupportingDistrict(member.getId(), district("mazowieckie").getId(), false))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("ALREADY_AFFILIATED");
        assertThatThrownBy(() -> service.addSupportingDistrict(member.getId(), board, false))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("NOT_A_DISTRICT");
        assertThatThrownBy(() -> service.setVotingDistrict(member.getId(), district("slaskie").getId()))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("NOT_AFFILIATED");
    }

    @Test
    void changesAreAudited() {
        TMember member = newMember("Ann", "Nine", "mazowieckie");

        service.addSupportingDistrict(member.getId(), district("slaskie").getId(), false);

        assertThat(auditCount("members :: affiliation-supporting")).isEqualTo(1);
        assertThat(auditPayloads("members :: affiliation-supporting")).contains("districtAffiliations");
    }

    private void assertInvariants(TMember member) {
        List<String> open = jdbc.queryForList(
            "SELECT kind::text || ':' || votes_here FROM member_district_affiliations "
                + "WHERE member_id = ? AND valid_to IS NULL", String.class, member.getId());

        assertThat(open.stream().filter(s -> s.startsWith("PRIMARY")).count()).isEqualTo(1);
        assertThat(open.stream().filter(s -> s.endsWith(":true")).count()).isEqualTo(1);
    }
}
