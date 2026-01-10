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

package org.mlodzirazem.panel.services.panelapi.directory.mock.user;

import lombok.RequiredArgsConstructor;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryUserProfileFetcher;
import org.mlodzirazem.panel.services.panelapi.directory.api.model.DirectoryUserProfile;
import org.mlodzirazem.panel.services.panelapi.directory.mock.state.MockDirectoryState;
import org.mlodzirazem.panel.services.panelapi.directory.mock.state.MockDirectoryUser;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class MockDirectoryUserProfileFetcher implements DirectoryUserProfileFetcher {
    private final MockDirectoryState mockDirectoryState;

    @Override
    public Optional<DirectoryUserProfile> fetchUserProfileById(String id) {
        return mockDirectoryState.users()
                                 .stream()
                                 .filter(u -> u.id().equals(UUID.fromString(id)))
                                 .map(MockDirectoryUser::toUserProfile)
                                 .findFirst();
    }

    @Override
    public Optional<DirectoryUserProfile> fetchUserProfileByEmail(String email) {
        return mockDirectoryState.users()
                                 .stream()
                                 .filter(u -> u.email().equals(email))
                                 .map(MockDirectoryUser::toUserProfile)
                                 .findFirst();
    }
}
