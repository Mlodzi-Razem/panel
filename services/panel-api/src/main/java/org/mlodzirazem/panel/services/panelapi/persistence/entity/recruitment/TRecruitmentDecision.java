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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.TBody;

import java.time.LocalDate;

/**
 * A collegial decision of an organ with its resolution signature
 * (pattern: {@code suspensions.decision_signature}). Accepting a case
 * requires one {@code ZK_APPROVAL} and one {@code ZO_ADMISSION_RESOLUTION}.
 */
@Getter
@Setter
@Entity
@Table(name = "recruitment_decisions")
@SequenceGenerator(
    name = "seq_recruitment_decisions_id",
    sequenceName = "seq_recruitment_decisions_id",
    allocationSize = 1
)
public class TRecruitmentDecision extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_recruitment_decisions_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private TRecruitmentCase recruitmentCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deciding_body_id", nullable = false)
    private TBody decidingBody;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "decision_kind", nullable = false, columnDefinition = "recruitment_decision_kind")
    private RecruitmentDecisionKind decisionKind;

    @Column(name = "decision_signature", nullable = false)
    private String decisionSignature;

    @Column(name = "decided_on", nullable = false)
    private LocalDate decidedOn;

    @Column(name = "note")
    private @Nullable String note;
}
