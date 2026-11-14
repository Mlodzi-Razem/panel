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

package org.mlodzirazem.panel.services.panelapi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@MappedSuperclass
@Getter
@Setter
public abstract class TBaseEntity {
    @JoinColumn(name = "created_by_user_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private @Nullable TMember createdBy;

    @JoinColumn(name = "updated_by_user_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private @Nullable TMember updatedBy;

    @Column(name = "created_at", nullable = false)
    private @Nullable ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private @Nullable ZonedDateTime updatedAt;

    public abstract Long getId();

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
