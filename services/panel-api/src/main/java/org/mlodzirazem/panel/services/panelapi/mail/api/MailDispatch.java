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

/**
 * What {@link TransactionalMail#sendAfterCommit} decided about a message.
 */
public enum MailDispatch {
    /** Accepted; will be (or has been) handed to Microsoft Graph after commit. */
    SCHEDULED,
    /** Mail is switched off or Graph is not configured; nothing was done. */
    DISABLED,
    /** The configured sender is outside the allowed domain or on the tenant's onmicrosoft.com domain. */
    REFUSED_SENDER,
    /** The daily limit for external recipients is used up. */
    REFUSED_LIMIT,
    /** The recipient is not a plain single address. */
    INVALID_RECIPIENT
}
