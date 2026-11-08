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

package org.mlodzirazem.panel.services.panelapi.directory.beans.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mlodzirazem.panel.services.panelapi.directory.api.DirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryUserProfileFetcher;
import org.mlodzirazem.panel.services.panelapi.directory.mock.MockDirectoryImplementation;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectoryImplementationProviderTest {
    static final String DEFAULT_STATE = "classpath:directory/mock-state.json";

    DirectoryImplementationProvider provider(boolean enabled, String stateFile) {
        return new DirectoryImplementationProvider(new DefaultResourceLoader(), enabled, stateFile);
    }

    @Test
    void mockEnabledReadsClasspathState() throws Exception {
        try (DirectoryImplementation impl = provider(true, DEFAULT_STATE).createImplementation()) {
            assertThat(impl).isInstanceOf(MockDirectoryImplementation.class);
            DirectoryUserProfileFetcher fetcher = impl.injectService(DirectoryUserProfileFetcher.class);
            assertThat(fetcher.fetchUserProfileById("100000000000000000001")).isPresent();
        }
    }

    @Test
    void mockEnabledReadsFileState(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("state.json");
        Files.writeString(file, """
            {"users":[{"id":"42","email":"x@example.org","preferredName":"X"}],"groups":[]}
            """);

        try (DirectoryImplementation impl = provider(true, file.toUri().toString()).createImplementation()) {
            DirectoryUserProfileFetcher fetcher = impl.injectService(DirectoryUserProfileFetcher.class);
            assertThat(fetcher.fetchUserProfileByEmail("x@example.org")).isPresent();
            assertThat(fetcher.fetchUserProfileById("100000000000000000001")).isEmpty();
        }
    }

    @Test
    void missingFileFails() {
        assertThatThrownBy(() -> provider(true, "classpath:directory/missing.json").createImplementation())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing.json");
    }

    @Test
    void mockDisabledFailsClearly() {
        assertThatThrownBy(() -> provider(false, DEFAULT_STATE).createImplementation())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("No production directory provider");
    }
}
