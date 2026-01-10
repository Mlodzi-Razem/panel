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

import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.wait.strategy.ShellStrategy;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class PostgresContainer {
    private final PostgreSQLContainer container = new PostgreSQLContainer("postgres:18.1-alpine");

    private static final String BACKUP_FILE = "db_backup.sql";
    private final AtomicBoolean backUpExists = new AtomicBoolean(false);

    public synchronized void start() {
        if (isRunning()) {
            return;
        }

        log.info("Starting PostgreSQL container...");
        container.start();

        log.info("Waiting for PostgreSQL container to be ready...");
        container.waitingFor(
            new ShellStrategy()
                .withCommand("pg_isready -U %s -d %s".formatted(container.getUsername(), container.getDatabaseName()))
                .withStartupTimeout(Duration.ofSeconds(15))
        );

        log.info("PostgreSQL container started.");
    }

    @PreDestroy
    public synchronized void stop() {
        log.info("Stopping PostgreSQL container...");
        container.stop();
        log.info("PostgreSQL container stopped.");
    }

    public boolean isRunning() {
        return container.isRunning();
    }

    public JdbcProps jdbcProperties() {
        if (!isRunning()) {
            start();
        }

        return new JdbcProps(
            container.getJdbcUrl(),
            container.getUsername(),
            container.getPassword()
        );
    }

    public synchronized void backUpSchema() {
        log.info("Backing up schema...");
        shell("pg_dumpall -U %s > %s".formatted(container.getUsername(), BACKUP_FILE));

        backUpExists.set(true);
        log.info("Schema backed up.");
    }

    public synchronized void restoreSchema() {
        log.info("Restoring schema...");
        shell("psql -U %s -d %s -c 'DROP SCHEMA public CASCADE;'".formatted(container.getUsername(), container.getDatabaseName()));
        shell("psql -U %s -f %s".formatted(container.getUsername(), BACKUP_FILE));

        backUpExists.set(false);
        log.info("Schema restored.");
    }

    public boolean backUpExists() {
        return backUpExists.get();
    }

    @SneakyThrows
    private synchronized void shell(String command) {
        log.debug("Executing command in Postgres container: {}", command);
        Container.ExecResult result = container.execInContainer("/bin/sh", "-c", command);

        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Command '%s' exited with %d.\nstdout:\n%s\nstderr:\n%s\n".formatted(
                command,
                result.getExitCode(),
                result.getStdout(),
                result.getStderr()
            ));
        }
    }
}
