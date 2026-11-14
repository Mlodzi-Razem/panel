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
import org.mlodzirazem.panel.services.panelapi.core.error.ConflictException;
import org.mlodzirazem.panel.services.panelapi.core.error.ForbiddenException;
import org.mlodzirazem.panel.services.panelapi.core.error.RuleViolationException;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.ChangeRequestStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.MemberStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberChangeRequest;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class ChangeRequestServiceTest extends DomainTestSupport {
    @Autowired
    ChangeRequestService service;

    @Test
    void createStoresProposedValuesAndSnapshotOfCurrentOnes() {
        TMember member = member("Fiona", "Formal", "mazowieckie");
        memberService.update(member.getId(), Map.of(MemberField.ADDRESS_LINE, "ul. Stara 1"));

        TMemberChangeRequest request = service.create(member.getId(), member.getId(), Map.of(
            MemberField.ADDRESS_LINE, "ul. Nowa 2",
            MemberField.FORMAL_NAME_LAST, "Newname",
            MemberField.BIRTH_DATE, LocalDate.of(2003, 3, 3)), "moved");

        ChangeRequestDetails details = service.details(request.getId());

        assertThat(request.getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
        assertThat(details.proposed()).containsOnlyKeys(MemberField.ADDRESS_LINE, MemberField.FORMAL_NAME_LAST)
                                      .containsEntry(MemberField.ADDRESS_LINE, "ul. Nowa 2");
        assertThat(details.snapshot()).containsEntry(MemberField.ADDRESS_LINE, "ul. Stara 1")
                                      .containsEntry(MemberField.FORMAL_NAME_LAST, "Formal");
        assertThat(auditPayloads("members :: change-request-create")).contains("addressLine", "formalNameLast")
                                                                      .doesNotContain("Nowa").doesNotContain("Newname");
    }

    @Test
    void onlyFormalFieldsAreAllowedAndSomethingMustChange() {
        TMember member = member("Fiona", "Formal", "mazowieckie");

        assertThatThrownBy(() -> service.create(member.getId(), member.getId(), Map.of(MemberField.PSEUDONYM, "x"), null))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("NOT_FORMAL_FIELD");
        assertThatThrownBy(() -> service.create(member.getId(), member.getId(), Map.of(MemberField.STATUS,
            MemberStatus.REMOVED), null))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("NOT_FORMAL_FIELD");
        assertThatThrownBy(() -> service.create(member.getId(), member.getId(),
            Map.of(MemberField.FORMAL_NAME_FIRST, "Fiona"), null))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("NO_CHANGES");
        Map<MemberField, Object> nulling = new EnumMap<>(MemberField.class);
        nulling.put(MemberField.FORMAL_NAME_FIRST, null);
        assertThatThrownBy(() -> service.create(member.getId(), member.getId(), nulling, null))
            .isInstanceOf(RuleViolationException.class).extracting("code").isEqualTo("FIELD_REQUIRED");
    }

    @Test
    void atMostOnePendingRequestPerMember() {
        TMember member = member("Piotr", "Pending", "mazowieckie");
        service.create(member.getId(), member.getId(), Map.of(MemberField.ADDRESS_LINE, "a"), null);

        assertThatThrownBy(() -> service.create(member.getId(), member.getId(), Map.of(MemberField.ADDRESS_LINE, "b"), null))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("PENDING_EXISTS");
    }

    @Test
    void districtBoardApprovesAndValuesAreAppliedAtomically() {
        TMember member = member("Anna", "Approve", "mazowieckie");
        TMember coordinator = member("Cora", "Board", "mazowieckie");
        grant(coordinator, board("mazowieckie"), "COORDINATOR");
        TMemberChangeRequest request = service.create(member.getId(), member.getId(), Map.of(
            MemberField.ADDRESS_LINE, "ul. Nowa 2", MemberField.FORMAL_NAME_LAST, "Changed"), "reason");

        assertThat(service.pendingFor(coordinator.getId())).extracting(TMemberChangeRequest::getId)
                                                           .containsExactly(request.getId());

        TMemberChangeRequest approved = service.approve(request.getId(), coordinator.getId(), "ok");

        TMember reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(ChangeRequestStatus.APPROVED);
        assertThat(approved.getDecidedAt()).isNotNull();
        assertThat(reloaded.getAddressLine()).isEqualTo("ul. Nowa 2");
        assertThat(reloaded.getFormalNameLast()).isEqualTo("Changed");
        assertThat(reloaded.getFormalName()).isEqualTo("Anna Changed");
        assertThat(service.pendingFor(coordinator.getId())).isEmpty();
        assertThat(auditPayloads("members :: change-request-approve")).contains("APPROVED", "addressLine")
                                                                      .doesNotContain("Nowa").doesNotContain("Changed");

        assertThatThrownBy(() -> service.approve(request.getId(), coordinator.getId(), null))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("NOT_PENDING");
    }

    @Test
    void nationalBoardCanApproveAnyDistrictButOtherDistrictBoardCannot() {
        TMember member = member("Anna", "Approve", "mazowieckie");
        TMember otherBoard = member("Olek", "Other", "slaskie");
        TMember secretary = member("Sam", "Secretary", "slaskie");
        grant(otherBoard, board("slaskie"), "COORDINATOR");
        grant(secretary, body("national-board"), "SECRETARY");
        TMemberChangeRequest request = service.create(member.getId(), member.getId(),
            Map.of(MemberField.ADDRESS_LINE, "x"), null);

        assertThat(service.pendingFor(otherBoard.getId())).isEmpty();
        assertThatThrownBy(() -> service.approve(request.getId(), otherBoard.getId(), null))
            .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.reject(request.getId(), member.getId(), null))
            .isInstanceOf(ForbiddenException.class);

        assertThat(service.approve(request.getId(), secretary.getId(), null).getStatus())
            .isEqualTo(ChangeRequestStatus.APPROVED);
    }

    @Test
    void approvalFailsWhenAFieldChangedSinceTheSnapshot() {
        TMember member = member("Stan", "Stale", "mazowieckie");
        TMember coordinator = member("Cora", "Board", "mazowieckie");
        grant(coordinator, board("mazowieckie"), "COORDINATOR");
        TMemberChangeRequest request = service.create(member.getId(), member.getId(), Map.of(
            MemberField.ADDRESS_LINE, "proposed", MemberField.FORMAL_NAME_LAST, "Proposed"), null);

        memberService.update(member.getId(), Map.of(MemberField.ADDRESS_LINE, "changed meanwhile"));

        assertThatThrownBy(() -> service.approve(request.getId(), coordinator.getId(), null))
            .isInstanceOfSatisfying(ConflictException.class, e -> {
                assertThat(e.getCode()).isEqualTo("STALE_SNAPSHOT");
                assertThat(e.getFields()).containsExactly("addressLine");
            });

        TMember reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getAddressLine()).isEqualTo("changed meanwhile");
        assertThat(reloaded.getFormalNameLast()).isEqualTo("Stale");
        assertThat(service.details(request.getId()).request().getStatus()).isEqualTo(ChangeRequestStatus.PENDING);

        assertThat(service.reject(request.getId(), coordinator.getId(), "stale").getStatus())
            .isEqualTo(ChangeRequestStatus.REJECTED);
        service.create(member.getId(), member.getId(), Map.of(MemberField.ADDRESS_LINE, "again"), null);
    }

    @Test
    void requesterCancelsAndOthersCannot() {
        TMember member = member("Cass", "Cancel", "mazowieckie");
        TMember other = member("Oto", "Other", "mazowieckie");
        TMemberChangeRequest request = service.create(member.getId(), member.getId(),
            Map.of(MemberField.ADDRESS_LINE, "x"), null);

        assertThatThrownBy(() -> service.cancel(request.getId(), other.getId())).isInstanceOf(ForbiddenException.class);

        TMemberChangeRequest cancelled = service.cancel(request.getId(), member.getId());

        assertThat(cancelled.getStatus()).isEqualTo(ChangeRequestStatus.CANCELLED);
        assertThat(service.forMember(member.getId())).extracting(TMemberChangeRequest::getStatus)
                                                     .containsExactly(ChangeRequestStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(request.getId(), member.getId())).isInstanceOf(ConflictException.class);
        assertThat(auditCount("members :: change-request-cancel")).isEqualTo(1);
    }

    @Test
    void nobodyDecidesTheirOwnRequest() {
        TMember coordinator = member("Cora", "Board", "mazowieckie");
        TMember subject = member("Sue", "Subject", "mazowieckie");
        grant(coordinator, board("mazowieckie"), "COORDINATOR");
        TMemberChangeRequest own = service.create(coordinator.getId(), coordinator.getId(),
            Map.of(MemberField.ADDRESS_LINE, "x"), null);

        assertThatThrownBy(() -> service.approve(own.getId(), coordinator.getId(), null))
            .isInstanceOf(ForbiddenException.class).extracting("code").isEqualTo("SELF_DECISION_FORBIDDEN");
        assertThatThrownBy(() -> service.reject(own.getId(), coordinator.getId(), null))
            .isInstanceOf(ForbiddenException.class).extracting("code").isEqualTo("SELF_DECISION_FORBIDDEN");
        service.cancel(own.getId(), coordinator.getId());

        TMemberChangeRequest onBehalf = service.create(subject.getId(), coordinator.getId(),
            Map.of(MemberField.ADDRESS_LINE, "y"), null);

        assertThatThrownBy(() -> service.approve(onBehalf.getId(), coordinator.getId(), null))
            .isInstanceOf(ForbiddenException.class).extracting("code").isEqualTo("SELF_DECISION_FORBIDDEN");
    }

    private TMember member(String first, String last, String district) {
        return newMember(first, last, district);
    }
}
