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

package org.mlodzirazem.panel.services.panelapi.directory.beans.event;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.directory.api.ReloadableDirectoryService;
import org.mlodzirazem.panel.services.panelapi.directory.beans.DirectoryReloadedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DirectoryReloader {
    private final List<ReloadableDirectoryService> reloadableDirectoryServices;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher events;

    public synchronized void reload() {
        Instant startInstant = clock.instant();

        meterRegistry.timer("directory.reload").record(() ->
            reloadableDirectoryServices.forEach(ReloadableDirectoryService::reload)
        );

        //TODO: Add providers info
        events.publishEvent(new DirectoryReloadedEvent(
            startInstant,
            "",
            ""
        ));
    }
}
