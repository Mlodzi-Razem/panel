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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One row of the mail delivery log. Names only: the recipient's domain, never the address, subject or body.
 */
@Getter
@Setter
@Entity
@Table(name = "mail_deliveries")
@SequenceGenerator(
    name = "seq_mail_deliveries_id",
    sequenceName = "seq_mail_deliveries_id",
    allocationSize = 1
)
public class TMailDelivery {
    @Id
    @GeneratedValue(generator = "seq_mail_deliveries_id", strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "reference")
    private @Nullable String reference;

    @Column(name = "recipient_domain", nullable = false)
    private String recipientDomain;

    @Column(name = "external", nullable = false)
    private boolean external;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MailDeliveryStatus status;

    @Column(name = "error_code")
    private @Nullable String errorCode;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
