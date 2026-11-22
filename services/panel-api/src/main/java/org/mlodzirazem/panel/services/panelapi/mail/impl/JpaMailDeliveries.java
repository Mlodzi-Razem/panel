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
import org.mlodzirazem.panel.services.panelapi.persistence.entity.mail.TMailDelivery;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.mail.TMailDeliveryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@Component
class JpaMailDeliveries implements MailDeliveries {
    private final TMailDeliveryRepository repository;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    JpaMailDeliveries(TMailDeliveryRepository repository, PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public long countExternalSentSince(Instant since) {
        return repository.countByExternalTrueAndStatusAndCreatedAtGreaterThanEqual(
            MailDeliveryStatus.SENT, since.atZone(ZoneOffset.UTC));
    }

    @Override
    public void record(
        String purpose,
        @Nullable String reference,
        String recipientDomain,
        boolean external,
        MailDeliveryStatus status,
        @Nullable String errorCode
    ) {
        TMailDelivery row = new TMailDelivery();
        row.setPurpose(purpose);
        row.setReference(reference);
        row.setRecipientDomain(recipientDomain);
        row.setExternal(external);
        row.setStatus(status);
        row.setErrorCode(errorCode);
        row.setCreatedAt(clock.instant().atZone(ZoneOffset.UTC));

        requiresNew.executeWithoutResult(tx -> repository.save(row));
    }
}
