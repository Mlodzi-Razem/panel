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

package org.mlodzirazem.panel.services.panelapi.mail.api;

import org.jspecify.annotations.Nullable;

/**
 * One plain-text message.
 *
 * @param recipient the single recipient address
 * @param subject   the subject line
 * @param textBody  the plain-text body; may carry one-time secrets
 * @param purpose   a short key such as {@code recruitment-declaration-link}, logged and stored
 * @param reference a pointer to the business object such as {@code recruitment-case:123}, logged and stored
 * @param replyTo   optional Reply-To address; never logged
 */
public record MailMessage(
    String recipient,
    String subject,
    String textBody,
    String purpose,
    @Nullable String reference,
    @Nullable String replyTo
) {
    public MailMessage(String recipient, String subject, String textBody, String purpose, @Nullable String reference) {
        this(recipient, subject, textBody, purpose, reference, null);
    }

    /**
     * Addresses, subject and body never appear rendered.
     */
    @Override
    public String toString() {
        return "MailMessage[purpose=" + purpose + ", reference=" + reference + ']';
    }
}
