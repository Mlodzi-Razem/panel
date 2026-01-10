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

package org.mlodzirazem.panel.services.panelapi.persistence;

import jakarta.validation.constraints.Min;
import lombok.Builder;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@Builder
@ConfigurationProperties(prefix = "panel.db")
public record PanelDatabaseProperties(
    @URL
    String url,
    String user,
    String password,
    PoolProperties pool
) {
    @Builder
    public record PoolProperties(
        @DefaultValue("SELECT 1")
        String queryTest,

        @Min(1)
        int minSize,

        @Min(4)
        int maxSize,

        @DurationMin(seconds = 1)
        Duration keepAlive,

        @DurationMin(seconds = 1)
        Duration idleTimeout,

        @DurationMin(seconds = 1)
        Duration connectionTimeout
    ) {
    }
}
