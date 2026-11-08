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

package org.mlodzirazem.panel.services.panelapi.directory;

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryUserProfileFetcher;
import org.mlodzirazem.panel.services.panelapi.test.db.PostgresTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "panel.directory.mock.enabled=true")
@PostgresTest
class DirectoryStartupIntegrationTest {
    @Autowired
    DirectoryUserProfileFetcher fetcher;

    @Test
    void directoryIsLoadedAtStartup() {
        assertThat(fetcher.fetchUserProfileById("100000000000000000001"))
            .hasValueSatisfying(p -> assertThat(p.email()).isEqualTo("anna.kowalska@example.org"));
        assertThat(fetcher.fetchUserProfileByEmail("jan.nowak@example.org")).isPresent();
        assertThat(fetcher.fetchUserProfileById("unknown")).isEmpty();
    }
}
