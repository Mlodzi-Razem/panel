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

package org.mlodzirazem.panel.services.panelapi.test;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.members.MemberService;
import org.mlodzirazem.panel.services.panelapi.members.NewMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembershipRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplate;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateMemberTypeRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyTemplateRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

public abstract class DomainTestSupport {
    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MemberService memberService;

    @Autowired
    protected TMemberRepository memberRepository;

    @Autowired
    protected TBodyRepository bodyRepository;

    @Autowired
    protected TBodyTemplateRepository templateRepository;

    @Autowired
    protected TBodyTemplateMemberTypeRepository memberTypeRepository;

    @Autowired
    protected TBodyMembershipRepository bodyMembershipRepository;

    private int sequence = 0;

    @BeforeEach
    protected void cleanDomainData() {
        jdbc.execute("DELETE FROM recruitment_consents");
        jdbc.execute("DELETE FROM declaration_artifacts");
        jdbc.execute("DELETE FROM declaration_form_sessions");
        jdbc.execute("DELETE FROM recruitment_case_events");
        jdbc.execute("DELETE FROM recruitment_decisions");
        jdbc.execute("DELETE FROM admission_bans");
        jdbc.execute("DELETE FROM recruitment_cases");
        jdbc.execute("DELETE FROM audited_operations");
        jdbc.execute("DELETE FROM member_change_requests");
        jdbc.execute("DELETE FROM member_district_affiliations");
        jdbc.execute("DELETE FROM body_memberships");
        jdbc.execute("DELETE FROM suspensions");
        jdbc.execute("DELETE FROM bodies WHERE code IS NULL OR code LIKE 'test-%'");
        jdbc.execute("DELETE FROM member_identities");
        jdbc.execute("DELETE FROM members");
    }

    protected TMember newMember(String first, String last, String districtSlug) {
        sequence++;

        Map<MemberField, Object> values = new EnumMap<>(MemberField.class);
        values.put(MemberField.PREFERRED_NAME_FIRST, first);
        values.put(MemberField.PREFERRED_NAME_LAST, last);
        values.put(MemberField.FORMAL_NAME_FIRST, first);
        values.put(MemberField.FORMAL_NAME_LAST, last);
        values.put(MemberField.BIRTH_DATE, LocalDate.of(2003, 3, 3));
        values.put(MemberField.DOMAIN_EMAIL, first.toLowerCase() + "." + last.toLowerCase() + sequence + "@example.org");

        return memberService.create(new NewMember(values, district(districtSlug).getId(), false));
    }

    protected TBody district(String slug) {
        return body("district-" + slug);
    }

    protected TBody board(String slug) {
        return body("district-" + slug + "-board");
    }

    protected TBody body(String code) {
        return bodyRepository.findByCode(code).orElseThrow();
    }

    protected TBodyTemplate template(BodyKind kind) {
        return templateRepository.findByKind(kind).getFirst();
    }

    protected TBodyTemplateMemberType memberType(BodyKind kind, String code) {
        return memberTypeRepository.findByBodyTemplateIdAndCode(template(kind).getId(), code).orElseThrow();
    }

    protected TBodyMembership grant(
        TMember member,
        TBody body,
        String typeCode,
        LocalDate from,
        @Nullable LocalDate to
    ) {
        BodyKind kind = BodyKind.valueOf(jdbc.queryForObject(
            "SELECT t.kind::text FROM bodies b JOIN body_templates t ON t.id = b.body_template_id WHERE b.id = ?",
            String.class, body.getId()));

        TBodyMembership membership = new TBodyMembership();
        membership.setMember(member);
        membership.setBody(body);
        membership.setMemberType(memberType(kind, typeCode));
        membership.setValidFrom(from);
        membership.setValidTo(to);
        return bodyMembershipRepository.saveAndFlush(membership);
    }

    protected TBodyMembership grant(TMember member, TBody body, String typeCode) {
        return grant(member, body, typeCode, LocalDate.now().minusYears(1), null);
    }

    protected int auditCount(String operationId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audited_operations WHERE operation_id = ?",
            Integer.class, operationId);
        return count == null ? 0 : count;
    }

    protected String auditPayloads(String operationId) {
        return String.join("\n", jdbc.queryForList(
            "SELECT operation_payload::text FROM audited_operations WHERE operation_id = ? ORDER BY id",
            String.class, operationId));
    }
}
