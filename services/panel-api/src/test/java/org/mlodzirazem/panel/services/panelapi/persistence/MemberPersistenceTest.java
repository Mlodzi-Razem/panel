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

package org.mlodzirazem.panel.services.panelapi.persistence;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.audit.TAuditedOperation;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.audit.TAuditedOperationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBodyMembership;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.AffiliationKind;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.ChangeRequestStatus;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberChangeRequest;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberChangeRequestRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliation;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMemberDistrictAffiliationRepository;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionType;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.PermissionScope;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.TPermissionGroup;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.permission.TPermissionGroupRepository;
import org.mlodzirazem.panel.services.panelapi.test.DomainTestSupport;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@PostgresTest
class MemberPersistenceTest extends DomainTestSupport {
    @Autowired
    TMemberDistrictAffiliationRepository affiliationRepository;

    @Autowired
    TMemberChangeRequestRepository changeRequestRepository;

    @Autowired
    TPermissionGroupRepository permissionGroupRepository;

    @Autowired
    TAuditedOperationRepository auditedOperationRepository;

    @Test
    void bodyMembershipRoundTrip() {
        TMember member = newMember("Bea", "Body", "mazowieckie");
        TBodyMembership saved = grant(member, board("mazowieckie"), "COORDINATOR", LocalDate.of(2025, 1, 1),
            LocalDate.of(2026, 1, 1));

        TBodyMembership loaded = bodyMembershipRepository.findAllByMemberId(member.getId()).getFirst();

        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getBody().getCode()).isEqualTo("district-mazowieckie-board");
        assertThat(loaded.getMemberType().getCode()).isEqualTo("COORDINATOR");
        assertThat(loaded.getValidFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(loaded.getValidTo()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.isActiveOn(LocalDate.of(2025, 6, 1))).isTrue();
        assertThat(loaded.isActiveOn(LocalDate.of(2026, 1, 1))).isFalse();
    }

    @Test
    void membershipPeriodMustBeValidAndOpenMembershipsUnique() {
        TMember member = newMember("Bea", "Body", "mazowieckie");

        assertThatThrownBy(() -> grant(member, board("mazowieckie"), "COORDINATOR", LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 1))).isInstanceOf(DataIntegrityViolationException.class);

        grant(member, board("mazowieckie"), "COORDINATOR");

        assertThatThrownBy(() -> grant(member, board("mazowieckie"), "COORDINATOR"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void affiliationInvariantsAreEnforcedInSql() {
        TMember member = newMember("Ada", "Affil", "mazowieckie");

        TMemberDistrictAffiliation second = new TMemberDistrictAffiliation();
        second.setMember(member);
        second.setDistrict(district("slaskie"));
        second.setKind(AffiliationKind.PRIMARY);
        second.setValidFrom(LocalDate.now());
        assertThatThrownBy(() -> affiliationRepository.saveAndFlush(second))
            .isInstanceOf(DataIntegrityViolationException.class);

        TMemberDistrictAffiliation supporting = new TMemberDistrictAffiliation();
        supporting.setMember(member);
        supporting.setDistrict(district("slaskie"));
        supporting.setKind(AffiliationKind.SUPPORTING);
        supporting.setVotesHere(true);
        supporting.setValidFrom(LocalDate.now());
        assertThatThrownBy(() -> affiliationRepository.saveAndFlush(supporting))
            .isInstanceOf(DataIntegrityViolationException.class);

        TMemberDistrictAffiliation duplicate = new TMemberDistrictAffiliation();
        duplicate.setMember(member);
        duplicate.setDistrict(district("mazowieckie"));
        duplicate.setKind(AffiliationKind.SUPPORTING);
        duplicate.setValidFrom(LocalDate.now());
        assertThatThrownBy(() -> affiliationRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void changeRequestRoundTripAndSinglePendingRequest() {
        TMember member = newMember("Cia", "Change", "mazowieckie");

        TMemberChangeRequest request = pending(member);
        changeRequestRepository.saveAndFlush(request);

        TMemberChangeRequest loaded = changeRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(loaded.getProposedValues()).contains("addressLine");
        assertThat(loaded.getSnapshot()).contains("addressLine");
        assertThat(loaded.getStatus()).isEqualTo(ChangeRequestStatus.PENDING);

        assertThatThrownBy(() -> changeRequestRepository.saveAndFlush(pending(member)))
            .isInstanceOf(DataIntegrityViolationException.class);

        loaded.setStatus(ChangeRequestStatus.REJECTED);
        loaded.setDecidedBy(member);
        loaded.setDecidedAt(ZonedDateTime.now());
        changeRequestRepository.saveAndFlush(loaded);
        changeRequestRepository.saveAndFlush(pending(member));
    }

    @Test
    void permissionGroupPermissionsRoundTrip() {
        TPermissionGroup group = new TPermissionGroup();
        group.setName("Test group");
        group.setScope(PermissionScope.BODY);
        group.getPermissions().add(PermissionType.FEES_READ);
        group.getPermissions().add(PermissionType.BODIES_MANAGE);
        permissionGroupRepository.saveAndFlush(group);

        try {
            TPermissionGroup loaded = permissionGroupRepository.findByScopeAndName(PermissionScope.BODY, "Test group")
                                                               .orElseThrow();

            assertThat(loaded.getPermissions()).containsExactlyInAnyOrder(PermissionType.FEES_READ,
                PermissionType.BODIES_MANAGE);
            assertThat(permissionGroupRepository.findByScopeAndName(PermissionScope.LOCAL, "District board")
                                                .orElseThrow().getPermissions()).hasSize(9);
        } finally {
            permissionGroupRepository.delete(group);
        }
    }

    @Test
    void auditedOperationRoundTrip() {
        TAuditedOperation operation = new TAuditedOperation();
        operation.setOperationId("test :: round-trip");
        operation.setTraceId("trace");
        operation.setTimestamp(ZonedDateTime.now());
        operation.setPayload("{\"ids\": {\"memberId\": 1}}");
        auditedOperationRepository.saveAndFlush(operation);

        assertThat(auditPayloads("test :: round-trip")).contains("memberId");
    }

    private TMemberChangeRequest pending(TMember member) {
        TMemberChangeRequest request = new TMemberChangeRequest();
        request.setMember(member);
        request.setRequestedBy(member);
        request.setProposedValues("{\"addressLine\": \"new\"}");
        request.setSnapshot("{\"addressLine\": \"old\"}");
        request.setReason("because");
        return request;
    }
}
