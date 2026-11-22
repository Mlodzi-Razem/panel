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
 * One magic-link session of the e-declaration form. Only the SHA-256 hash of the token is stored;
 * the plaintext exists once, in the link response visible to the case manager.
 */
@Getter
@Setter
@Entity
@Table(name = "declaration_form_sessions")
@SequenceGenerator(
    name = "seq_declaration_form_sessions_id",
    sequenceName = "seq_declaration_form_sessions_id",
    allocationSize = 1
)
public class TDeclarationFormSession extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_declaration_form_sessions_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private TRecruitmentCase recruitmentCase;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "declaration_session_status")
    private DeclarationFormSessionStatus status = DeclarationFormSessionStatus.CREATED;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "filled_at")
    private @Nullable ZonedDateTime filledAt;

    @Column(name = "signed_at")
    private @Nullable ZonedDateTime signedAt;

    /**
     * Names of the submitted form fields (names-only GDPR rule — the values live on the case row).
     */
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "submitted_fields", columnDefinition = "jsonb")
    private @Nullable String submittedFields;
}
