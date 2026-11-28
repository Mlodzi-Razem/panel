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

package org.mlodzirazem.panel.services.panelapi.directory.provisioning;

import org.mlodzirazem.panel.services.panelapi.directory.api.provisioning.AccountProvisioningFinished;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test listener of {@link AccountProvisioningFinished}: {@code @EventListener} that records the event after the commit of
 * the publishing transaction (the registry table of {@code @ApplicationModuleListener} is not on this branch).
 */
@Component
class FinishedEventCapture {
    final List<AccountProvisioningFinished> committed = new CopyOnWriteArrayList<>();

    @EventListener
    void on(AccountProvisioningFinished event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed.add(event);
                }
            });
        } else {
            committed.add(event);
        }
    }
}
