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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.members.AffiliationService;
import org.mlodzirazem.panel.services.panelapi.members.MemberField;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MembersApiTest extends ApiTestSupport {
    @Autowired
    AffiliationService affiliationService;

    TMember zoA;
    TMember zoB;
    TMember zk;
    TMember readerA;
    TMember inA;
    TMember inB;
    TMember supportingInA;

    @BeforeEach
    void setUp() {
        zoA = linked(newMember("Zofia", "Okregowa", "mazowieckie"));
        grant(zoA, board("mazowieckie"), "COORDINATOR");
        zoB = linked(newMember("Zenon", "Slaski", "slaskie"));
        grant(zoB, board("slaskie"), "COORDINATOR");
        zk = linked(newMember("Karol", "Krajowy", "pomorskie"));
        grant(zk, body("national-board"), "SECRETARY");
        readerA = linked(newMember("Tola", "Czytelnik", "mazowieckie"));
        grantDistrictReader(readerA, "mazowieckie");
        inA = newMember("Anna", "Mazowiecka", "mazowieckie");
        memberService.update(inA.getId(), Map.of(MemberField.FORMAL_IDENTIFIER, "90010112345"));
        inB = newMember("Beata", "Slaska", "slaskie");
        supportingInA = newMember("Sylwia", "Wspierajaca", "slaskie");
        affiliationService.addSupportingDistrict(supportingInA.getId(), district("mazowieckie").getId(), false);
    }

    @AfterEach
    void dropCustomGroup() {
        jdbc.execute("DELETE FROM body_memberships");
        jdbc.execute("DELETE FROM body_template_member_types WHERE code = 'TEST_EDITOR'");
        jdbc.execute("DELETE FROM permission_group WHERE name = 'test contact editor'");
    }

    private Set<Long> ids(Res res) {
        Set<Long> ids = new HashSet<>();
        res.body().path("items").forEach(n -> ids.add(n.path("id").asLong()));
        return ids;
    }

    @Test
    void listIsScopedToTheCallersDistricts() {
        Res a = get(zoA, "/members?size=100");
        assertThat(a.status()).isEqualTo(200);
        assertThat(ids(a)).contains(zoA.getId(), inA.getId(), supportingInA.getId(), readerA.getId())
                          .doesNotContain(inB.getId(), zoB.getId(), zk.getId());

        Res b = get(zoB, "/members?size=100");
        assertThat(ids(b)).contains(zoB.getId(), inB.getId(), supportingInA.getId()).doesNotContain(inA.getId());

        Res all = get(zk, "/members?size=100");
        assertThat(ids(all)).contains(zoA.getId(), zoB.getId(), zk.getId(), inA.getId(), inB.getId());

        assertThat(get(linked(newMember("Pat", "Plain", "slaskie")), "/members").status()).isEqualTo(403);
    }

    @Test
    void supportingDistrictBoardGetsContactFieldsOnlyInRows() {
        Res a = get(zoA, "/members?size=100");
        var row = a.body().path("items");
        for (var n : row) {
            if (n.path("id").asLong() == supportingInA.getId()) {
                assertThat(n.path("fields").has("preferredNameFirst")).isTrue();
                assertThat(n.path("fields").has("status")).isFalse();
                assertThat(n.path("primaryDistrict").path("name").asString()).contains("Śląski");
            }
            if (n.path("id").asLong() == inA.getId()) {
                assertThat(n.path("fields").path("status").asString()).isEqualTo("ACTIVE");
            }
        }
    }

    @Test
    void searchFiltersAndPaging() {
        newMember("Żaneta", "Łukasik", "mazowieckie");

        Res found = get(zk, "/members?q=zaneta%20lukasik");
        assertThat(found.body().path("totalItems").asLong()).isEqualTo(1);
        assertThat(found.body().path("items").get(0).path("fields").path("preferredNameLast").asString())
            .isEqualTo("Łukasik");

        assertThat(get(zk, "/members?size=1000").body().path("size").asInt()).isEqualTo(100);
        assertThat(get(zk, "/members?size=2&page=1").body().path("items")).hasSize(2);
        assertThat(get(zk, "/members?district=" + district("slaskie").getId() + "&size=100").body().path("totalItems")
            .asLong()).isEqualTo(3);
        assertThat(get(zk, "/members?status=REMOVED").body().path("totalItems").asLong()).isZero();
        assertThat(get(zk, "/members?status=NOPE").status()).isEqualTo(400);
    }

    @Test
    void listCanBeSortedWithoutOrderingByHiddenFields() {
        java.util.List<Long> byNameDesc = new java.util.ArrayList<>();
        get(zk, "/members?size=100&sort=name:desc").body().path("items").forEach(n -> byNameDesc.add(n.path("id").asLong()));
        java.util.List<Long> byNameAsc = new java.util.ArrayList<>();
        get(zk, "/members?size=100&sort=name:asc").body().path("items").forEach(n -> byNameAsc.add(n.path("id").asLong()));
        assertThat(byNameDesc).isEqualTo(byNameAsc.reversed());

        java.util.List<String> districtsAsc = new java.util.ArrayList<>();
        get(zk, "/members?size=100&sort=district:asc").body().path("items")
            .forEach(n -> districtsAsc.add(n.path("primaryDistrict").path("name").asString()));
        java.util.List<String> districtsDesc = new java.util.ArrayList<>();
        get(zk, "/members?size=100&sort=district:desc").body().path("items")
            .forEach(n -> districtsDesc.add(n.path("primaryDistrict").path("name").asString()));
        assertThat(districtsAsc).isNotEmpty();
        assertThat(java.util.List.copyOf(new java.util.LinkedHashSet<>(districtsDesc)))
            .isEqualTo(java.util.List.copyOf(new java.util.LinkedHashSet<>(districtsAsc)).reversed());

        memberService.update(supportingInA.getId(), Map.of(MemberField.STATUS,
            org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus.PROBATION));
        java.util.List<Long> byStatus = new java.util.ArrayList<>();
        get(zoA, "/members?size=100&sort=status:asc").body().path("items").forEach(n -> byStatus.add(n.path("id").asLong()));
        assertThat(byStatus).last().isEqualTo(supportingInA.getId());

        assertThat(get(zk, "/members?sort=nope:desc").status()).isEqualTo(200);
    }

    @Test
    void searchDoesNotMatchFieldsTheCallerCannotRead() {
        memberService.update(supportingInA.getId(), Map.of(MemberField.FORMAL_NAME_LAST, "Tajemnicza"));

        assertThat(ids(get(zoA, "/members?q=tajemnicza&size=100"))).doesNotContain(supportingInA.getId());
        assertThat(ids(get(zoA, "/members?q=wspierajaca&size=100"))).contains(supportingInA.getId());
        assertThat(ids(get(zoB, "/members?q=tajemnicza&size=100"))).contains(supportingInA.getId());
        assertThat(ids(get(zk, "/members?q=tajemnicza&size=100"))).contains(supportingInA.getId());
    }

    @Test
    void detailFiltersFieldsAndMasksTheIdentifier() {
        Res full = get(zoA, "/members/" + inA.getId());
        assertThat(full.status()).isEqualTo(200);
        assertThat(full.body().path("fields").path("formalNameFirst").asString()).isEqualTo("Anna");
        assertThat(full.body().path("fields").path("formalIdentifier").asString()).isEqualTo("*******2345");
        assertThat(full.body().path("affiliations")).hasSize(1);
        assertThat(full.body().has("membershipEndsOn")).isTrue();
        assertThat(full.body().path("electoralRights").path("eligible").asBoolean()).isTrue();

        Res supporting = get(zoA, "/members/" + supportingInA.getId());
        assertThat(supporting.status()).isEqualTo(200);
        assertThat(supporting.body().path("fields").has("preferredNameFirst")).isTrue();
        assertThat(supporting.body().path("fields").has("formalNameFirst")).isFalse();
        assertThat(supporting.body().path("fields").has("status")).isFalse();
        assertThat(supporting.body().has("affiliations")).isFalse();

        Res reader = get(readerA, "/members/" + inA.getId());
        assertThat(reader.body().path("fields").has("pseudonym")).isTrue();
        assertThat(reader.body().path("fields").has("formalIdentifier")).isFalse();

        assertThat(get(zoA, "/members/" + inB.getId()).status()).isEqualTo(403);
        assertThat(get(zoA, "/members/999999999").status()).isEqualTo(404);
    }

    @Test
    void revealNeedsManageAndIsAudited() {
        Res plain = get(zoA, "/members/" + inA.getId());
        assertThat(plain.status()).isEqualTo(200);
        assertThat(auditCount("members :: reveal-formal-identifier")).isZero();

        Res revealed = get(zoA, "/members/" + inA.getId() + "?reveal=formalIdentifier");
        assertThat(revealed.body().path("fields").path("formalIdentifier").asString()).isEqualTo("90010112345");
        assertThat(auditCount("members :: reveal-formal-identifier")).isEqualTo(1);
        assertThat(auditPayloads("members :: reveal-formal-identifier")).contains("formalIdentifier")
                                                                          .doesNotContain("90010112345");

        assertThat(get(readerA, "/members/" + inA.getId() + "?reveal=formalIdentifier").status()).isEqualTo(403);
        assertThat(get(zoA, "/members/" + inB.getId() + "?reveal=formalIdentifier").status()).isEqualTo(403);
        assertThat(auditCount("members :: reveal-formal-identifier")).isEqualTo(1);
    }

    @Test
    void patchChecksTheFieldGroupPermissions() {
        Res ok = patch(zoA, "/members/" + inA.getId(), fields("pseudonym", "P", "addressLine", "ul. X 1", "status", "SUSPENDED"));
        assertThat(ok.status()).isEqualTo(200);
        TMember reloaded = memberRepository.findById(inA.getId()).orElseThrow();
        assertThat(reloaded.getPseudonym()).isEqualTo("P");
        assertThat(reloaded.getStatus().name()).isEqualTo("SUSPENDED");

        Map<String, Object> clear = new LinkedHashMap<>();
        clear.put("pseudonym", null);
        Res cleared = patch(zoA, "/members/" + inA.getId(), Map.of("fields", clear));
        assertThat(cleared.status()).isEqualTo(200);
        reloaded = memberRepository.findById(inA.getId()).orElseThrow();
        assertThat(reloaded.getPseudonym()).isNull();
        assertThat(reloaded.getAddressLine()).isEqualTo("ul. X 1");

        Res otherDistrict = patch(zoA, "/members/" + inB.getId(), fields("pseudonym", "Q"));
        assertThat(otherDistrict.status()).isEqualTo(403);
        assertThat(otherDistrict.body().path("fields").get(0).asString()).isEqualTo("pseudonym");
        assertThat(patch(readerA, "/members/" + inA.getId(), fields("pseudonym", "Q")).status()).isEqualTo(403);
        assertThat(patch(zoA, "/members/" + inA.getId(), fields("pseudonym", 5)).code()).isEqualTo("INVALID_VALUE");
    }

    @Test
    void patchRejectsTheWholeRequestWhenOneGroupIsForbidden() {
        Long groupId = jdbc.queryForObject(
            "INSERT INTO permission_group (name, scope) VALUES ('test contact editor', 'LOCAL') RETURNING id",
            Long.class);
        jdbc.update("INSERT INTO permission_group_permissions VALUES (?, 'CONTACT_PERSONAL_DATA_READ'), "
            + "(?, 'CONTACT_PERSONAL_DATA_MANAGE')", groupId, groupId);
        jdbc.update("INSERT INTO body_template_member_types (name, code, is_default, body_template_id, "
            + "permission_group_id) SELECT 'Test editor', 'TEST_EDITOR', FALSE, body_template_id, ? FROM bodies "
            + "WHERE code = 'district-mazowieckie-board'", groupId);
        TMember editor = linked(newMember("Edyta", "Kontaktowa", "mazowieckie"));
        grant(editor, board("mazowieckie"), "TEST_EDITOR");

        Res mixed = patch(editor, "/members/" + inA.getId(), fields("pseudonym", "P", "addressLine", "x"));
        assertThat(mixed.status()).isEqualTo(403);
        assertThat(mixed.body().path("fields")).hasSize(1);
        assertThat(mixed.body().path("fields").get(0).asString()).isEqualTo("addressLine");
        assertThat(memberRepository.findById(inA.getId()).orElseThrow().getPseudonym()).isNull();

        assertThat(patch(editor, "/members/" + inA.getId(), fields("pseudonym", "P")).status()).isEqualTo(200);
    }

    @Test
    void createRequiresManageInTheTargetDistrict() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("primaryDistrictId", district("mazowieckie").getId());
        body.putAll(fields("preferredNameFirst", "Nowa", "preferredNameLast", "Osoba", "formalNameFirst", "Nowa",
            "formalNameLast", "Osoba", "birthDate", "2004-04-04"));

        Res created = post(zoA, "/members", body);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("fields").path("formalNameFirst").asString()).isEqualTo("Nowa");
        assertThat(auditCount("members :: create")).isGreaterThan(0);

        body.put("primaryDistrictId", district("slaskie").getId());
        assertThat(post(zoA, "/members", body).status()).isEqualTo(403);

        body.put("primaryDistrictId", district("mazowieckie").getId());
        body.putAll(fields("preferredNameFirst", "X"));
        assertThat(post(zoA, "/members", body).code()).isEqualTo("FIELD_REQUIRED");
    }

    @Test
    void affiliationsAreDecidedByTheTargetDistrict() {
        String path = "/members/" + inA.getId() + "/affiliations";
        Map<String, Object> add = Map.of("action", "ADD_SUPPORTING", "districtId", district("slaskie").getId());

        assertThat(post(zoA, path, add).status()).isEqualTo(403);

        Res added = post(zoB, path, add);
        assertThat(added.status()).isEqualTo(200);
        assertThat(added.body()).hasSize(2);

        Res ended = delete(zoB, path + "/" + district("slaskie").getId());
        assertThat(ended.status()).isEqualTo(200);
        assertThat(ended.body()).hasSize(1);

        Res moved = post(zoB, path, Map.of("action", "SET_PRIMARY", "districtId", district("slaskie").getId()));
        assertThat(moved.status()).isEqualTo(200);
        assertThat(moved.body().get(0).path("districtId").asLong()).isEqualTo(district("slaskie").getId());
        assertThat(post(readerA, path, add).status()).isEqualTo(403);
    }

    @Test
    void listsMembersEndingThisYear() {
        int year = Year.now().getValue();
        memberService.update(inA.getId(), Map.of(MemberField.BIRTH_DATE, LocalDate.of(year - 26, 5, 5)));

        Res res = get(zoA, "/members/ending-this-year");
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.body()).hasSize(1);
        assertThat(res.body().get(0).path("id").asLong()).isEqualTo(inA.getId());
        assertThat(res.body().get(0).path("membershipEndsOn").asString()).isEqualTo(year + "-12-31");

        assertThat(get(zoB, "/members/ending-this-year").body()).isEmpty();
        assertThat(get(zoA, "/members/ending-this-year?year=" + (year + 1)).body()).isEmpty();
        assertThat(get(readerA, "/members/ending-this-year").status()).isEqualTo(403);
    }
}
