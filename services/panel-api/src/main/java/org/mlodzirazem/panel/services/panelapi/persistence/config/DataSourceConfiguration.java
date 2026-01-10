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

package org.mlodzirazem.panel.services.panelapi.persistence.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mlodzirazem.panel.services.panelapi.persistence.PanelDatabaseProperties;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(PanelDatabaseProperties.class)
public class DataSourceConfiguration {
    @Bean
    @FlywayDataSource
    public DataSource dataSource(PanelDatabaseProperties properties) {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(properties.url());
        dataSource.setUser(properties.user());
        dataSource.setPassword(properties.password());
        return dataSource;
    }

    @Primary
    @Bean
    public HikariDataSource hikariDataSource(PanelDatabaseProperties properties) {
        var hikari = new HikariConfig();

        PanelDatabaseProperties.PoolProperties pool = properties.pool();

        hikari.setDataSource(dataSource(properties));
        hikari.setPoolName("panel-api-pool");
        hikari.setDriverClassName("org.postgresql.Driver");
        hikari.setMinimumIdle(pool.minSize());
        hikari.setMaximumPoolSize(pool.maxSize());
        hikari.setIdleTimeout(pool.idleTimeout().toMillis());
        hikari.setKeepaliveTime(pool.keepAlive().toMillis());
        hikari.setConnectionTimeout(pool.connectionTimeout().toMillis());
        hikari.setConnectionTestQuery(pool.queryTest());

        return new HikariDataSource(hikari);
    }
}
