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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.fee;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.TBaseEntity;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "fee_adjustments")
@SequenceGenerator(name = "seq_fee_adjustments_id", sequenceName = "seq_fee_adjustments_id", allocationSize = 1)
public class TFeeAdjustment extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_fee_adjustments_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private TMember member;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "fee_adjustment_kind")
    private AdjustmentKind kind;

    @Column(name = "from_month", nullable = false)
    private LocalDate fromMonth;

    @Column(name = "to_month")
    private @Nullable LocalDate toMonth;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private @Nullable BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reason_category", nullable = false, columnDefinition = "fee_adjustment_reason")
    private AdjustmentReason reasonCategory;

    @Column(name = "justification", nullable = false)
    private String justification;

    @Column(name = "resolution_reference")
    private @Nullable String resolutionReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by_id", nullable = false)
    private TMember grantedBy;

    @Column(name = "revoked_at")
    private @Nullable ZonedDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_id")
    private @Nullable TMember revokedBy;

    @Column(name = "revoked_from_month")
    private @Nullable LocalDate revokedFromMonth;

    public @Nullable YearMonth lastCoveredMonth() {
        YearMonth end = toMonth == null ? null : YearMonth.from(toMonth);

        if (revokedFromMonth != null) {
            YearMonth beforeRevocation = YearMonth.from(revokedFromMonth).minusMonths(1);
            end = end == null || end.isAfter(beforeRevocation) ? beforeRevocation : end;
        }

        return end;
    }

    public boolean coversMonth(YearMonth month) {
        return !month.isBefore(YearMonth.from(fromMonth))
            && (toMonth == null || !month.isAfter(YearMonth.from(toMonth)))
            && (revokedFromMonth == null || month.isBefore(YearMonth.from(revokedFromMonth)));
    }
}
