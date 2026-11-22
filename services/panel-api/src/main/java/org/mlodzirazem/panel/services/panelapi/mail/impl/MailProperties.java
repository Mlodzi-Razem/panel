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
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * {@code panel.mail.*}: transactional mail through Microsoft Graph (needs {@code Mail.Send} on the app
 * registration, restricted to the sender mailbox by an application access policy).
 *
 * <ul>
 *     <li>{@code enabled} - off by default; env {@code PANEL_MAIL_ENABLED}.</li>
 *     <li>{@code sender} - the mailbox sending, e.g. {@code panel@mlodzirazem.org}; env
 *     {@code PANEL_MAIL_SENDER}.</li>
 *     <li>{@code allowed-sender-domain} - the sender must be in this domain (default
 *     {@code mlodzirazem.org}); the tenant's {@code *.onmicrosoft.com} domain is refused regardless.</li>
 *     <li>{@code internal-domains} - recipients in these domains are internal and do not count towards the
 *     external limit (default {@code mlodzirazem.org}).</li>
 *     <li>{@code daily-external-limit} - most external recipients per rolling 24 hours (default 300), the guard
 *     for Exchange Online's tenant external recipient rate limit, reduced for young tenants (10% for the first
 *     30 days, 25% for days 31-60); env {@code PANEL_MAIL_DAILY_EXTERNAL_LIMIT}.</li>
 * </ul>
 */
@ConfigurationProperties("panel.mail")
public record MailProperties(
    boolean enabled,
    @Nullable String sender,
    String allowedSenderDomain,
    List<String> internalDomains,
    Integer dailyExternalLimit
) {
    public static final String DEFAULT_DOMAIN = "mlodzirazem.org";
    public static final int DEFAULT_DAILY_EXTERNAL_LIMIT = 300;

    public MailProperties {
        sender = blank(sender) ? null : sender.strip();
        allowedSenderDomain = blank(allowedSenderDomain)
            ? DEFAULT_DOMAIN
            : allowedSenderDomain.strip().toLowerCase(Locale.ROOT);
        internalDomains = internalDomains == null || internalDomains.isEmpty()
            ? List.of(DEFAULT_DOMAIN)
            : internalDomains.stream().filter(d -> !blank(d)).map(d -> d.strip().toLowerCase(Locale.ROOT)).toList();
        dailyExternalLimit = dailyExternalLimit == null || dailyExternalLimit < 0
            ? DEFAULT_DAILY_EXTERNAL_LIMIT
            : dailyExternalLimit;
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
