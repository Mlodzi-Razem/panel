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
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;

import java.time.ZonedDateTime;

/**
 * A stored declaration artifact: the rendered template (TEMPLATE_PDF), the signed PAdES file
 * (SIGNED_PDF) or the paper-scan fallback (SCAN). The memory of the signature validation keeps
 * the names of the performed checks only, never raw payloads.
 */
@Getter
@Setter
@Entity
@Table(name = "declaration_artifacts")
@SequenceGenerator(
    name = "seq_declaration_artifacts_id",
    sequenceName = "seq_declaration_artifacts_id",
    allocationSize = 1
)
public class TDeclarationArtifact extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_declaration_artifacts_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private TRecruitmentCase recruitmentCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private @Nullable TDeclarationFormSession session;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "artifact_kind", nullable = false, columnDefinition = "declaration_artifact_kind")
    private DeclarationArtifactKind artifactKind;

    @Column(name = "content_sha256", nullable = false)
    private String contentSha256;

    /** Null once the artifact was purged by the retention job (see {@link #purgedAt}). */
    @Column(name = "storage_ref")
    private @Nullable String storageRef;

    /** SHA-256 of the rendered template the candidate signed; null for scans. */
    @Column(name = "document_sha256")
    private @Nullable String documentSha256;

    @Column(name = "template_version")
    private @Nullable String templateVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "signature_type", columnDefinition = "declaration_signature_type")
    private @Nullable DeclarationSignatureType signatureType;

    @Column(name = "signer_name")
    private @Nullable String signerName;

    @Column(name = "validated_at")
    private @Nullable ZonedDateTime validatedAt;

    /** Retention (M8): the stored file and the signer's name are gone; hashes and check names remain. */
    @Column(name = "purged_at")
    private @Nullable ZonedDateTime purgedAt;

    /** Names of the performed validation checks; no values, no certificate dumps. */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "validation_result", columnDefinition = "jsonb")
    private @Nullable String validationResult;
}
