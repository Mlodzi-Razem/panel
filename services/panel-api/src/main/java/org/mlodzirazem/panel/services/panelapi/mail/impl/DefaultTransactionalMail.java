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

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.GraphAppToken;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailDispatch;
import org.mlodzirazem.panel.services.panelapi.mail.api.MailMessage;
import org.mlodzirazem.panel.services.panelapi.mail.api.TransactionalMail;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.mail.MailDeliveryStatus;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

@Slf4j
@Service
@EnableConfigurationProperties(MailProperties.class)
class DefaultTransactionalMail implements TransactionalMail {
    private static final Duration LIMIT_WINDOW = Duration.ofHours(24);
    private static final String ONMICROSOFT = ".onmicrosoft.com";

    private final MailProperties properties;
    private final GraphAppToken token;
    private final GraphMailSender sender;
    private final MailDeliveries deliveries;
    private final Clock clock;

    private boolean warnedDisabled;

    DefaultTransactionalMail(
        MailProperties properties,
        GraphAppToken token,
        GraphMailSender sender,
        MailDeliveries deliveries,
        Clock clock
    ) {
        this.properties = properties;
        this.token = token;
        this.sender = sender;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Override
    public MailDispatch sendAfterCommit(MailMessage message) {
        if (!properties.enabled() || !token.configured()) {
            warnDisabledOnce();
            return MailDispatch.DISABLED;
        }

        String domain = domainOf(message.recipient());
        String senderAddress = properties.sender();

        if (senderAddress == null || !senderAllowed(senderAddress)) {
            refuse(message, domain == null ? "invalid" : domain, !internal(domain), MailDeliveryStatus.REFUSED_SENDER);
            return MailDispatch.REFUSED_SENDER;
        }

        if (domain == null) {
            log.warn("mail purpose={} reference={} not sent: invalid recipient", message.purpose(), message.reference());
            return MailDispatch.INVALID_RECIPIENT;
        }

        boolean external = !internal(domain);

        if (external) {
            long sent = deliveries.countExternalSentSince(clock.instant().minus(LIMIT_WINDOW));
            if (sent >= properties.dailyExternalLimit()) {
                refuse(message, domain, true, MailDeliveryStatus.REFUSED_LIMIT);
                return MailDispatch.REFUSED_LIMIT;
            }
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(senderAddress, message, domain, external);
                }
            });
        } else {
            deliver(senderAddress, message, domain, external);
        }

        return MailDispatch.SCHEDULED;
    }

    private void deliver(String senderAddress, MailMessage message, String domain, boolean external) {
        MailDeliveryStatus status = MailDeliveryStatus.FAILED;
        String errorCode = "INTERNAL";

        try {
            GraphMailSender.Result result = sender.send(senderAddress, message);
            status = result.sent() ? MailDeliveryStatus.SENT : MailDeliveryStatus.FAILED;
            errorCode = result.errorCode();
        } catch (RuntimeException e) {
            log.warn("mail purpose={} reference={} failed unexpectedly: {}",
                message.purpose(), message.reference(), e.getClass().getSimpleName());
        }

        log.info("mail purpose={} reference={} recipientDomain={} status={}{}",
            message.purpose(), message.reference(), domain, status, errorCode == null ? "" : " error=" + errorCode);

        record(message, domain, external, status, errorCode);
    }

    private void refuse(MailMessage message, String domain, boolean external, MailDeliveryStatus status) {
        log.warn("mail purpose={} reference={} recipientDomain={} refused: {}",
            message.purpose(), message.reference(), domain, status);
        record(message, domain, external, status, null);
    }

    private void record(MailMessage message, String domain, boolean external, MailDeliveryStatus status,
                        @Nullable String errorCode) {
        try {
            deliveries.record(message.purpose(), message.reference(), domain, external, status, errorCode);
        } catch (RuntimeException e) {
            log.error("mail delivery log write failed for purpose={} reference={}: {}",
                message.purpose(), message.reference(), e.getClass().getSimpleName());
        }
    }

    private boolean senderAllowed(String address) {
        String domain = domainOf(address);
        return domain != null
            && domain.equals(properties.allowedSenderDomain())
            && !domain.endsWith(ONMICROSOFT);
    }

    private boolean internal(@Nullable String domain) {
        return domain != null && properties.internalDomains().contains(domain);
    }

    /**
     * The lower-cased domain of a plain address with exactly one {@code @} and no whitespace; null otherwise.
     */
    static @Nullable String domainOf(@Nullable String address) {
        if (address == null || address.isBlank() || address.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }

        int at = address.indexOf('@');
        if (at <= 0 || at != address.lastIndexOf('@') || at == address.length() - 1) {
            return null;
        }

        return address.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    private void warnDisabledOnce() {
        if (!warnedDisabled) {
            warnedDisabled = true;
            log.warn("panel.mail is disabled or Microsoft Graph is not configured: mail is not sent");
        }
    }
}
