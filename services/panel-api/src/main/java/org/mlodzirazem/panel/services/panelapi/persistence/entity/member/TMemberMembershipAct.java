/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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
package org.mlodzirazem.panel.services.panelapi.persistence.entity.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * One membership act (grant, removal, reinstatement). Append-only: the database rejects UPDATE and DELETE,
 * so the register keeps every resolution that ever changed the membership of a person.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "member_membership_acts")
@SequenceGenerator(name = "seq_member_membership_acts_id", sequenceName = "seq_member_membership_acts_id",
    allocationSize = 1)
public class TMemberMembershipAct extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_member_membership_acts_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private TMember member;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "act_kind", nullable = false, updatable = false, columnDefinition = "membership_act_kind")
    private MembershipActKind actKind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "removal_ground", updatable = false, columnDefinition = "membership_removal_ground")
    private @Nullable MembershipRemovalGround removalGround;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "previous_status", nullable = false, updatable = false, columnDefinition = "member_status")
    private MemberStatus previousStatus;

    /** Null only for a grant that created a new person: there was no membership type before it. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "previous_membership_type", updatable = false, columnDefinition = "membership_type")
    private @Nullable MembershipType previousMembershipType;

    /** Type after a MEMBERSHIP_TYPE_CHANGE act; null for every other kind. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "membership_type_after", updatable = false, columnDefinition = "membership_type")
    private @Nullable MembershipType membershipTypeAfter;

    @Column(name = "effective_on", nullable = false, updatable = false)
    private LocalDate effectiveOn;

    @Column(name = "decision_reference", nullable = false, updatable = false)
    private String decisionReference;

    @Column(name = "reason", updatable = false)
    private @Nullable String reason;
}
