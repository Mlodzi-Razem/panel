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

@Getter
@Setter
@Entity
@Table(name = "body_templates")
@SequenceGenerator(name = "seq_body_templates_id", sequenceName = "seq_body_templates_id", allocationSize = 1)
public class TBodyTemplate extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_body_templates_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "scope", nullable = false, columnDefinition = "body_scope")
    private BodyScope scope;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "body_kind")
    private BodyKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "body_parent_template_id")
    private @Nullable TBodyTemplate parentTemplate;

    @Column(name = "max_consecutive_terms")
    private @Nullable Integer maxConsecutiveTerms;

    @Column(name = "term_length_months")
    private @Nullable Integer termLengthMonths;

    @Column(name = "max_term_months")
    private @Nullable Integer maxTermMonths;

    @Column(name = "max_extensions")
    private @Nullable Integer maxExtensions;
}
