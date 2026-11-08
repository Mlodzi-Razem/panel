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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mlodzirazem.panel.services.panelapi.core.event.RpcEventPublisher;
import org.mlodzirazem.panel.services.panelapi.core.uuid.UuidGenerator;
import org.mlodzirazem.panel.services.panelapi.directory.beans.ReloadDirectoryEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class InitialDirectoryLoader {
    private static final Duration INITIAL_LOAD_TIMEOUT = Duration.ofSeconds(30);

    private final RpcEventPublisher rpcEventPublisher;
    private final UuidGenerator uuidGenerator;

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        try {
            rpcEventPublisher.publishAndReceive(
                new ReloadDirectoryEvent(uuidGenerator.uuidv7(), "default"),
                INITIAL_LOAD_TIMEOUT
            );
            log.info("Directory implementation loaded");
        } catch (RuntimeException ex) {
            log.error(
                "Initial directory load failed; directory services will be unavailable until a successful reload",
                ex
            );
        }
    }
}
