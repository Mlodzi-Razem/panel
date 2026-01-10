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

import org.mlodzirazem.panel.services.panelapi.persistence.PanelDatabaseProperties;
import org.mlodzirazem.panel.services.panelapi.persistence.config.DataSourceConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@TestConfiguration
@Import({BackupSchema.class, DataSourceConfiguration.class})
public class PostgresTestConfiguration {
    @Bean
    public PostgresContainer postgresContainer() {
        return new PostgresContainer();
    }

    @Primary
    @Bean
    public PanelDatabaseProperties panelDatabaseProperties(PostgresContainer postgresContainer) {
        JdbcProps jdbcProps = postgresContainer.jdbcProperties();

        Duration timeout = Duration.ofSeconds(15);

        return PanelDatabaseProperties.builder()
            .url(jdbcProps.url())
            .user(jdbcProps.username())
            .password(jdbcProps.password())
            .pool(PanelDatabaseProperties.PoolProperties.builder()
                .minSize(1)
                .maxSize(2)
                .keepAlive(timeout)
                .idleTimeout(timeout)
                .queryTest("SELECT 1")
                .connectionTimeout(timeout)
                .build())
            .build();
    }
}
