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

package org.mlodzirazem.panel.services.panelapi.directory.mock;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.directory.api.DirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryInjectableComponent;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryUserProfileFetcher;
import org.mlodzirazem.panel.services.panelapi.directory.mock.state.MockDirectoryState;
import org.mlodzirazem.panel.services.panelapi.directory.mock.user.MockDirectoryUserProfileFetcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.concurrent.atomic.AtomicReference;

@ConditionalOnProperty(prefix = "panel.directory.mock", name = "enabled", havingValue = "true")
public class MockDirectoryImplementation implements DirectoryImplementation {
    private AtomicReference<@Nullable MockDirectoryState> directoryState = new AtomicReference<>(null);

    @SuppressWarnings("unchecked")
    @Override
    public <T extends DirectoryInjectableComponent> T injectService(Class<T> serviceClass) {
        MockDirectoryState state = directoryState.get();
        if (state == null) {
            throw new IllegalStateException("Directory state is not initialized");
        }

        if (serviceClass.equals(DirectoryUserProfileFetcher.class)) {
            return (T) new MockDirectoryUserProfileFetcher(state);
        }

        throw new IllegalStateException("Unsupported service class " + serviceClass);
    }

    @Override
    public void close() throws Exception {
        directoryState.set(null);
    }
}
