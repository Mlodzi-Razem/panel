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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import org.springframework.data.annotation.Immutable;

import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "audited_operations")
@SequenceGenerator(
    name = "seq_audited_operations_id",
    sequenceName = "seq_audited_operations_id",
    allocationSize = 50
)
public class TAuditedOperation {
    @Id
    @GeneratedValue(generator = "seq_audited_operations_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "operation_id", nullable = false)
    private String operationId;

    @Column(name = "operation_trace_id", nullable = false)
    private String traceId;

    @Column(name = "operation_timestamp", nullable = false)
    private ZonedDateTime timestamp;

    @Column(name = "operation_payload", nullable = false, columnDefinition = "JSONB")
    private String payload;

    @JoinColumn(name = "triggered_by_id", nullable = true)
    @ManyToOne
    TMember triggeredBy;

    @Column(name = "triggered_by_ip", nullable = true)
    String triggeredByIp;
}
