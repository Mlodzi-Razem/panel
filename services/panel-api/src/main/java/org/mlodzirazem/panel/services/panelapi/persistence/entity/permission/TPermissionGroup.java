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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.permission;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "permission_group")
@SequenceGenerator(name = "seq_permission_group_id", sequenceName = "seq_permission_group_id", allocationSize = 1)
public class TPermissionGroup extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_permission_group_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "scope", nullable = false, columnDefinition = "permission_scope")
    private PermissionScope scope;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "email")
    private @Nullable String email;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "permission_group_permissions", joinColumns = @JoinColumn(name = "permission_group_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "permission_type", nullable = false, columnDefinition = "permission_type")
    private Set<PermissionType> permissions = EnumSet.noneOf(PermissionType.class);
}
