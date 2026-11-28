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

package org.mlodzirazem.panel.services.panelapi.core.event.impl;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** Test-only transactional listener: the publication must be stored and completed once it runs. */
@Component
class EventPublicationProbeListener {
    private final AtomicInteger handled = new AtomicInteger();

    @ApplicationModuleListener
    void on(EventPublicationProbeEvent event) {
        handled.incrementAndGet();
    }

    int handled() {
        return handled.get();
    }

    void reset() {
        handled.set(0);
    }
}
