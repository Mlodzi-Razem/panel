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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MembershipType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class MemberServiceTest extends DomainTestSupport {
    @Test
    void roundTripsAllColumns() {
        Map<MemberField, Object> values = new EnumMap<>(MemberField.class);
        values.put(MemberField.PREFERRED_NAME_FIRST, "Jan");
        values.put(MemberField.PREFERRED_NAME_MIDDLE, "Adam");
        values.put(MemberField.PREFERRED_NAME_LAST, "Kowalski");
        values.put(MemberField.FORMAL_NAME_FIRST, "Janusz");
        values.put(MemberField.FORMAL_NAME_LAST, "Kowalski-Nowak");
        values.put(MemberField.PSEUDONYM, "Janek");
        values.put(MemberField.PRONOUNS, "on/jego");
        values.put(MemberField.INSTITUTION, "UW");
        values.put(MemberField.SOCIAL_PROFILE_URL, "https://example.org/jan");
        values.put(MemberField.DOMAIN_EMAIL, "jan@example.org");
        values.put(MemberField.DOMAIN_EMAIL_ALIASES, List.of("j@example.org", "jk@example.org"));
        values.put(MemberField.PRIVATE_EMAILS, List.of("jan@home.pl"));
        values.put(MemberField.PHONE_NUMBERS, List.of("+48 111", "+48 222"));
        values.put(MemberField.MEMBER_ID, "123");
        values.put(MemberField.MEMBER_SINCE, LocalDate.of(2024, 5, 1));
        values.put(MemberField.REQUEST_DATE, LocalDate.of(2024, 4, 1));
        values.put(MemberField.PROBATION_PERIOD_START_DATE, LocalDate.of(2024, 5, 1));
        values.put(MemberField.PROBATION_PERIOD_END_DATE, LocalDate.of(2024, 8, 1));
        values.put(MemberField.REMOVED_AT, LocalDate.of(2030, 1, 1));
        values.put(MemberField.FORMAL_IDENTIFIER, "03030300000");
        values.put(MemberField.BIRTH_DATE, LocalDate.of(2003, 3, 3));
        values.put(MemberField.ADDRESS_LINE, "ul. Testowa 1/2, Warszawa");
        values.put(MemberField.ADDRESS_COUNTRY_CODE, "POL");
        values.put(MemberField.ADDRESS_POSTAL_CODE, "00-001");
        values.put(MemberField.ADDRESS_STATE, "mazowieckie");
        values.put(MemberField.ADDRESS_PROVINCE, "Warszawa");
        values.put(MemberField.ADDRESS_CITY, "Warszawa");
        values.put(MemberField.ADDRESS_STREET, "Testowa");
        values.put(MemberField.ADDRESS_BUILDING, "1");
        values.put(MemberField.ADDRESS_APARTMENT, "2");
        values.put(MemberField.DECLARATION_REFERENCE, "DECL-1");
        values.put(MemberField.STATUS, MemberStatus.PROBATION);
        values.put(MemberField.MEMBERSHIP_TYPE, MembershipType.SUPPORTING);

        TMember created = memberService.create(new NewMember(values, district("mazowieckie").getId(), false));
        TMember loaded = memberRepository.findById(created.getId()).orElseThrow();

        for (MemberField field : MemberField.values()) {
            assertThat(field.get(loaded)).as(field.propertyName()).isEqualTo(values.get(field));
        }

        assertThat(loaded.getFormalName()).isEqualTo("Janusz Kowalski-Nowak");
        assertThat(loaded.getPreferredName()).isEqualTo("Jan Adam Kowalski");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(created.getFormalName()).isEqualTo("Janusz Kowalski-Nowak");
    }

    @Test
    void createRequiresNamesAndBirthDate() {
        Map<MemberField, Object> values = new EnumMap<>(MemberField.class);
        values.put(MemberField.PREFERRED_NAME_FIRST, "Jan");

        assertThatThrownBy(() -> memberService.create(new NewMember(values, district("mazowieckie").getId())))
            .isInstanceOf(RuleViolationException.class)
            .hasMessageContaining("is required");
    }

    @Test
    void createEnforcesAdmissionAge() {
        Map<MemberField, Object> values = new EnumMap<>(MemberField.class);
        values.put(MemberField.PREFERRED_NAME_FIRST, "Old");
        values.put(MemberField.PREFERRED_NAME_LAST, "Timer");
        values.put(MemberField.FORMAL_NAME_FIRST, "Old");
        values.put(MemberField.FORMAL_NAME_LAST, "Timer");
        values.put(MemberField.BIRTH_DATE, LocalDate.now().minusYears(40));

        assertThatThrownBy(() -> memberService.create(new NewMember(values, district("mazowieckie").getId())))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("ADMISSION_AGE");

        TMember honorary = memberService.create(new NewMember(
            Map.of(
                MemberField.PREFERRED_NAME_FIRST, "Old", MemberField.PREFERRED_NAME_LAST, "Timer",
                MemberField.FORMAL_NAME_FIRST, "Old", MemberField.FORMAL_NAME_LAST, "Timer",
                MemberField.BIRTH_DATE, LocalDate.now().minusYears(40),
                MemberField.MEMBERSHIP_TYPE, MembershipType.HONORARY),
            district("mazowieckie").getId()));

        assertThat(honorary.getMembershipType()).isEqualTo(MembershipType.HONORARY);
    }

    @Test
    void createRejectsMemberNumbersFromTheReservedTestBand() {
        Map<MemberField, Object> values = new EnumMap<>(MemberField.class);
        values.put(MemberField.PREFERRED_NAME_FIRST, "Reza");
        values.put(MemberField.PREFERRED_NAME_LAST, "Rezerw");
        values.put(MemberField.FORMAL_NAME_FIRST, "Reza");
        values.put(MemberField.FORMAL_NAME_LAST, "Rezerw");
        values.put(MemberField.BIRTH_DATE, LocalDate.of(2003, 3, 3));
        values.put(MemberField.MEMBER_ID, "950123");

        assertThatThrownBy(() -> memberService.create(new NewMember(values, district("mazowieckie").getId())))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("MEMBER_ID_RESERVED");
    }

    @Test
    void updateRejectsMemberNumbersFromTheReservedTestBand() {
        TMember member = newMember("Nina", "Numer", "mazowieckie");

        assertThatThrownBy(() -> memberService.update(member.getId(), Map.of(MemberField.MEMBER_ID, "975000")))
            .isInstanceOf(RuleViolationException.class)
            .extracting("code").isEqualTo("MEMBER_ID_RESERVED");

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getMemberId()).isNull();
    }

    @Test
    void createAlsoCreatesThePrimaryAffiliationVotingThere() {
        TMember member = newMember("Pia", "Primary", "slaskie");

        assertThat(jdbc.queryForList(
            "SELECT kind::text || ':' || votes_here FROM member_district_affiliations WHERE member_id = ?",
            String.class, member.getId())).containsExactly("PRIMARY:true");
    }

    @Test
    void updateReportsOnlyReallyChangedFields() {
        TMember member = newMember("Uma", "Update", "mazowieckie");

        MemberUpdate result = memberService.update(member.getId(), Map.of(
            MemberField.PREFERRED_NAME_FIRST, "Uma",
            MemberField.PSEUDONYM, "U",
            MemberField.ADDRESS_LINE, "ul. Nowa 3"));

        assertThat(result.changedFields()).containsExactlyInAnyOrder(MemberField.PSEUDONYM, MemberField.ADDRESS_LINE);
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getPseudonym()).isEqualTo("U");
        assertThat(jdbc.queryForObject("SELECT updated_at IS NOT NULL FROM members WHERE id = ?", Boolean.class,
            member.getId())).isTrue();
    }

    @Test
    void updateRejectsNullingRequiredFields() {
        TMember member = newMember("Rex", "Required", "mazowieckie");
        Map<MemberField, Object> change = new EnumMap<>(MemberField.class);
        change.put(MemberField.BIRTH_DATE, null);

        assertThatThrownBy(() -> memberService.update(member.getId(), change))
            .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void listsMembersWhoseMembershipEndsInTheGivenYear() {
        int year = LocalDate.now().getYear();
        TMember ending = newMember("End", "This", "mazowieckie");
        TMember supportingEnding = newMember("Sup", "This", "mazowieckie");
        TMember honorary = newMember("Hon", "This", "mazowieckie");
        TMember later = newMember("Late", "That", "mazowieckie");
        TMember removed = newMember("Rem", "This", "mazowieckie");

        LocalDate bornInYear = LocalDate.of(year - 26, 12, 31);
        memberService.update(ending.getId(), Map.of(MemberField.BIRTH_DATE, LocalDate.of(year - 26, 1, 1)));
        memberService.update(supportingEnding.getId(), Map.of(MemberField.BIRTH_DATE, bornInYear,
            MemberField.MEMBERSHIP_TYPE, MembershipType.SUPPORTING));
        memberService.update(honorary.getId(), Map.of(MemberField.BIRTH_DATE, bornInYear,
            MemberField.MEMBERSHIP_TYPE, MembershipType.HONORARY));
        memberService.update(later.getId(), Map.of(MemberField.BIRTH_DATE, LocalDate.of(year - 25, 1, 1)));
        memberService.update(removed.getId(), Map.of(MemberField.BIRTH_DATE, bornInYear,
            MemberField.STATUS, MemberStatus.REMOVED));

        assertThat(memberService.membersEndingIn(year)).extracting(TMember::getId)
                                                       .containsExactly(ending.getId(), supportingEnding.getId());
        assertThat(memberService.membersEndingIn(year + 1)).extracting(TMember::getId).containsExactly(later.getId());
    }
}
