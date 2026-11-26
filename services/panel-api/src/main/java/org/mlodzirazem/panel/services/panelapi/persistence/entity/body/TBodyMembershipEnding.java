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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.body;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;

import java.time.LocalDate;

/**
 * One ending (or later shortening) of a mandate. Append-only: it keeps the resolution that justified the
 * change and the end date it replaced, so that neither is ever overwritten.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "body_membership_endings")
@SequenceGenerator(name = "seq_body_membership_endings_id", sequenceName = "seq_body_membership_endings_id",
    allocationSize = 1)
public class TBodyMembershipEnding extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_body_membership_endings_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "body_membership_id", nullable = false, updatable = false)
    private TBodyMembership membership;

    @Column(name = "previous_valid_to", updatable = false)
    private @Nullable LocalDate previousValidTo;

    @Column(name = "valid_to", nullable = false, updatable = false)
    private LocalDate validTo;

    @Column(name = "decision_reference", updatable = false)
    private @Nullable String decisionReference;
}
