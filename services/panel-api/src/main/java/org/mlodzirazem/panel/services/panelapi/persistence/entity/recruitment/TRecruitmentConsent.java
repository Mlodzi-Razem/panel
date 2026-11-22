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

import java.time.ZonedDateTime;

/**
 * A consent given with the declaration (Zgoda form, the 16+ block of the 2026 template).
 * The under-16 parent branch was dropped per the owner's decision; withdrawn_at is reserved
 * for the future image-consent withdrawal flow.
 */
@Getter
@Setter
@Entity
@Table(name = "recruitment_consents")
@SequenceGenerator(
    name = "seq_recruitment_consents_id",
    sequenceName = "seq_recruitment_consents_id",
    allocationSize = 1
)
public class TRecruitmentConsent extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_recruitment_consents_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private TRecruitmentCase recruitmentCase;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "consent_kind", nullable = false, columnDefinition = "recruitment_consent_kind")
    private RecruitmentConsentKind consentKind;

    /** self for the 2026 template (no parent flow); parent is reserved. */
    @Column(name = "granted_by", nullable = false)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private ZonedDateTime grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artifact_id")
    private @Nullable TDeclarationArtifact artifact;

    @Column(name = "withdrawn_at")
    private @Nullable ZonedDateTime withdrawnAt;
}
