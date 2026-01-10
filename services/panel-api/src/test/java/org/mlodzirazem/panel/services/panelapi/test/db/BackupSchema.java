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

package org.mlodzirazem.panel.services.panelapi.test.db;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.persistence.SchemaMigratedEvent;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.event.EventListener;

@TestComponent
@RequiredArgsConstructor
public class BackupSchema {
    private final PostgresContainer postgresContainer;

    @EventListener
    public void onSchemaMigrated(SchemaMigratedEvent event) {
        postgresContainer.backUpSchema();
    }
}
