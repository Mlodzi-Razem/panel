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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.recruitment;

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
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;

import java.time.LocalDate;

/**
 * ZK resolution banning a person from being admitted for up to 365 days
 * (statute jednolity 2026, art. 9 ust. 5). Identified by the formal identifier,
 * so it works across cases.
 */
@Getter
@Setter
@Entity
@Table(name = "admission_bans")
@SequenceGenerator(
    name = "seq_admission_bans_id",
    sequenceName = "seq_admission_bans_id",
    allocationSize = 1
)
public class TAdmissionBan extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_admission_bans_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private @Nullable TRecruitmentCase recruitmentCase;

    @Column(name = "formal_identifier")
    private @Nullable String formalIdentifier;

    @Column(name = "decision_signature", nullable = false)
    private String decisionSignature;

    /** The statutory reason of the uchwała (art. 9 ust. 5); back-office only, never public. */
    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until", nullable = false)
    private LocalDate effectiveUntil;

    @Column(name = "note")
    private @Nullable String note;
}
