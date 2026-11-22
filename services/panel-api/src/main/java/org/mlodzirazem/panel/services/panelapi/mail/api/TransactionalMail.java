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
 * Transactional e-mail (one message to one person, triggered by a business event) sent through Microsoft Graph
 * from the association's own domain. There is deliberately no bulk or campaign API here.
 */
public interface TransactionalMail {
    /**
     * Sends the message once the surrounding transaction has committed (nothing is sent when it rolls back);
     * without an active transaction it is sent immediately. The guards run now and decide the returned value;
     * the actual delivery outcome is recorded in the delivery log and never thrown into the caller. The message
     * is never persisted: its body may carry one-time secrets.
     */
    MailDispatch sendAfterCommit(MailMessage message);
}
