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

package org.mlodzirazem.panel.services.panelapi.mail.impl;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.mail.MailDeliveryStatus;

import java.time.Instant;

/**
 * The delivery log: names only, never an address, subject or body.
 */
interface MailDeliveries {
    /**
     * External deliveries that Graph accepted since the given instant.
     */
    long countExternalSentSince(Instant since);

    /**
     * Appends a row in its own transaction, so that it survives a rollback of the caller's transaction.
     */
    void record(
        String purpose,
        @Nullable String reference,
        String recipientDomain,
        boolean external,
        MailDeliveryStatus status,
        @Nullable String errorCode
    );
}
