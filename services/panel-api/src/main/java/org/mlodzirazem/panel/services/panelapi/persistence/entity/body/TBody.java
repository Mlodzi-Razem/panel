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

@Getter
@Setter
@Entity
@Table(name = "bodies")
@SequenceGenerator(name = "seq_bodies_id", sequenceName = "seq_bodies_id", allocationSize = 1)
public class TBody extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_bodies_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "body_template_id")
    private @Nullable TBodyTemplate bodyTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_body_id")
    private @Nullable TBody parentBody;

    @Column(name = "code")
    private @Nullable String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email")
    private @Nullable String email;

    public @Nullable BodyKind kind() {
        return bodyTemplate == null ? null : bodyTemplate.getKind();
    }

    public boolean isDistrict() {
        return kind() == BodyKind.DISTRICT;
    }
}
