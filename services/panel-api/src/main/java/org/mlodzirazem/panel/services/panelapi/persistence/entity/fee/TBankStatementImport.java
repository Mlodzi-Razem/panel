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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.member.TMember;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "bank_statement_imports")
@SequenceGenerator(name = "seq_bank_statement_imports_id", sequenceName = "seq_bank_statement_imports_id", allocationSize = 1)
public class TBankStatementImport extends TBaseEntity {
    @Id
    @GeneratedValue(generator = "seq_bank_statement_imports_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_sha256", nullable = false)
    private String fileSha256;

    @Column(name = "format", nullable = false)
    private String format;

    @Column(name = "account_iban", nullable = false)
    private String accountIban;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "statement_id")
    private @Nullable String statementId;

    @Column(name = "period_from")
    private @Nullable LocalDate periodFrom;

    @Column(name = "period_to")
    private @Nullable LocalDate periodTo;

    @Column(name = "transactions_total", nullable = false)
    private int transactionsTotal;

    @Column(name = "transactions_new", nullable = false)
    private int transactionsNew;

    @Column(name = "transactions_duplicate", nullable = false)
    private int transactionsDuplicate;

    @Column(name = "transactions_matched", nullable = false)
    private int transactionsMatched;

    @Column(name = "transactions_unmatched", nullable = false)
    private int transactionsUnmatched;

    @Column(name = "transactions_outgoing", nullable = false)
    private int transactionsOutgoing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imported_by_id", nullable = false)
    private TMember importedBy;
}
