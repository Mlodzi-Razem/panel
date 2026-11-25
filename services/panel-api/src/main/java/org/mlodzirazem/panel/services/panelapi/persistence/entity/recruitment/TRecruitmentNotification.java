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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

/**
 * Idempotency log of one recruitment notification mail (see V0.0.22.00). Numbers and kinds only.
 */
@Getter
@Setter
@Entity
@Table(name = "recruitment_notifications")
@SequenceGenerator(
    name = "seq_recruitment_notifications_id",
    sequenceName = "seq_recruitment_notifications_id",
    allocationSize = 1
)
public class TRecruitmentNotification {
    @Id
    @GeneratedValue(generator = "seq_recruitment_notifications_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "body_id", nullable = false)
    private Long bodyId;

    @Column(name = "reference_key", nullable = false)
    private String referenceKey;

    @Column(name = "covered_until")
    private @Nullable ZonedDateTime coveredUntil;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(java.time.ZoneOffset.UTC);
}
