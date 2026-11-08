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

import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.directory.api.model.DirectoryUserProfile;
import org.mlodzirazem.panel.services.panelapi.directory.mock.state.MockDirectoryState;
import org.mlodzirazem.panel.services.panelapi.directory.mock.state.MockDirectoryUser;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MockDirectoryUserProfileFetcherTest {
    MockDirectoryUserProfileFetcher fetcher = new MockDirectoryUserProfileFetcher(new MockDirectoryState(
        Set.of(new MockDirectoryUser("118234567890123456789", "a@example.org", "Anna")),
        Set.of()
    ));

    @Test
    void findsByNonUuidId() {
        assertThat(fetcher.fetchUserProfileById("118234567890123456789"))
            .contains(new DirectoryUserProfile("118234567890123456789", "Anna", "a@example.org"));
    }

    @Test
    void unknownIdIsEmpty() {
        assertThat(fetcher.fetchUserProfileById("not-a-uuid")).isEmpty();
    }

    @Test
    void findsByEmail() {
        assertThat(fetcher.fetchUserProfileByEmail("a@example.org")).isPresent();
        assertThat(fetcher.fetchUserProfileByEmail("b@example.org")).isEmpty();
    }
}
