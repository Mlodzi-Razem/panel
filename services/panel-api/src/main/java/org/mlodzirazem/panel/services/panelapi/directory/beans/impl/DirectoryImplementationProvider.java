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

import org.mlodzirazem.panel.services.panelapi.directory.api.DirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.mock.MockDirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.mock.state.MockDirectoryState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

@Component
public class DirectoryImplementationProvider {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ResourceLoader resourceLoader;
    private final boolean mockEnabled;
    private final String mockStateFile;

    public DirectoryImplementationProvider(
        ResourceLoader resourceLoader,
        @Value("${panel.directory.mock.enabled:false}") boolean mockEnabled,
        @Value("${panel.directory.mock.state-file:classpath:directory/mock-state.json}") String mockStateFile
    ) {
        this.resourceLoader = resourceLoader;
        this.mockEnabled = mockEnabled;
        this.mockStateFile = mockStateFile;
    }

    public DirectoryImplementation createImplementation() {
        if (mockEnabled) {
            return new MockDirectoryImplementation(loadMockState());
        }

        throw new UnsupportedOperationException(
            "No production directory provider (Google) is implemented yet. "
                + "Set panel.directory.mock.enabled=true to use the mock directory."
        );
    }

    private MockDirectoryState loadMockState() {
        Resource resource = resourceLoader.getResource(mockStateFile);
        if (!resource.exists()) {
            throw new IllegalStateException("Mock directory state file does not exist: " + mockStateFile);
        }

        try (InputStream stream = resource.getInputStream()) {
            return JSON_MAPPER.readValue(stream, MockDirectoryState.class);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Failed to read mock directory state from " + mockStateFile, ex);
        }
    }
}
