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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.body;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "body_memberships")
@SequenceGenerator(name = "seq_body_memberships_id", sequenceName = "seq_body_memberships_id", allocationSize = 1)
public class TBodyMembership extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_body_memberships_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private TMember member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "body_id", nullable = false)
    private TBody body;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_type_id", nullable = false)
    private TBodyTemplateMemberType memberType;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private @Nullable LocalDate validTo;

    /**
     * Reference of the resolution that justifies a correction of this membership's history
     * (required whenever valid_to is moved to a past date). Name only — never its contents.
     */
    @Column(name = "decision_reference")
    private @Nullable String decisionReference;

    /**
     * Mandate status. VOIDED marks a mandate recorded by mistake ("wpis omyłkowy"): it stops
     * counting as a function everywhere, but the row is never deleted — the history of the
     * correction itself stays in the table and in the audit log.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private BodyMembershipStatus status = BodyMembershipStatus.ACTIVE;

    /**
     * Why the mandate was voided; present only on VOIDED rows (enforced by the checks in the
     * A3 migration). Free text written by a bodies manager — kept in this table, not in the audit
     * log, whose payloads carry field names only.
     */
    @Column(name = "void_reason")
    private @Nullable String voidReason;

    /**
     * Resolution that voids this mandate; present only on VOIDED rows. Kept apart from
     * {@link #decisionReference}, which holds the appointing resolution and is never overwritten.
     */
    @Column(name = "void_decision_reference")
    private @Nullable String voidDecisionReference;

    public boolean isVoided() {
        return status == BodyMembershipStatus.VOIDED;
    }

    public boolean isActiveOn(LocalDate date) {
        return !isVoided() && !validFrom.isAfter(date) && (validTo == null || validTo.isAfter(date));
    }
}
