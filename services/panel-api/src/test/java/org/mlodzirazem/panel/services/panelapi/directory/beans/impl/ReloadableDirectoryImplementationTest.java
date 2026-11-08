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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.directory.api.DirectoryImplementation;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryUserProfileFetcher;
import org.mlodzirazem.panel.services.panelapi.directory.api.model.DirectoryUserProfile;
import org.mlodzirazem.panel.services.panelapi.directory.beans.DirectoryImplementationNotProvidedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReloadableDirectoryImplementationTest {
    DirectoryImplementationComplianceValidator validator = mock(DirectoryImplementationComplianceValidator.class);
    DirectoryImplementationProvider provider = mock(DirectoryImplementationProvider.class);
    ReloadableDirectoryImplementation reloadable;

    @BeforeEach
    void setUp() {
        reloadable = new ReloadableDirectoryImplementation(validator, provider);
    }

    @Test
    void reloadKeepsNewAndClosesOldAfterSwap() throws Exception {
        DirectoryImplementation first = mock(DirectoryImplementation.class);
        DirectoryImplementation second = mock(DirectoryImplementation.class);
        DirectoryUserProfileFetcher firstFetcher = mock(DirectoryUserProfileFetcher.class);
        DirectoryUserProfileFetcher secondFetcher = mock(DirectoryUserProfileFetcher.class);
        DirectoryUserProfile profile = new DirectoryUserProfile("1", "A", "a@example.org");
        when(first.injectService(DirectoryUserProfileFetcher.class)).thenReturn(firstFetcher);
        when(second.injectService(DirectoryUserProfileFetcher.class)).thenReturn(secondFetcher);
        when(secondFetcher.fetchUserProfileById("1")).thenReturn(Optional.of(profile));
        when(provider.createImplementation()).thenReturn(first, second);

        reloadable.reload();
        DirectoryUserProfileFetcher proxy = reloadable.injectService(DirectoryUserProfileFetcher.class);
        reloadable.reload();

        verify(first).close();
        verify(second, never()).close();
        assertThat(proxy.fetchUserProfileById("1")).contains(profile);
    }

    @Test
    void failedValidationKeepsOldAndClosesCandidate() throws Exception {
        DirectoryImplementation first = mock(DirectoryImplementation.class);
        DirectoryImplementation second = mock(DirectoryImplementation.class);
        DirectoryUserProfileFetcher firstFetcher = mock(DirectoryUserProfileFetcher.class);
        when(first.injectService(DirectoryUserProfileFetcher.class)).thenReturn(firstFetcher);
        when(firstFetcher.fetchUserProfileByEmail("a@example.org")).thenReturn(Optional.empty());
        when(provider.createImplementation()).thenReturn(first, second);
        reloadable.reload();

        doThrow(new DirectoryComplianceException("bad")).when(validator).validate(second);

        assertThatThrownBy(reloadable::reload).isInstanceOf(IllegalStateException.class);

        verify(first, never()).close();
        verify(second).close();
        DirectoryUserProfileFetcher proxy = reloadable.injectService(DirectoryUserProfileFetcher.class);
        assertThat(proxy.fetchUserProfileByEmail("a@example.org")).isEmpty();
        verify(firstFetcher).fetchUserProfileByEmail("a@example.org");
    }

    @Test
    void failedCreationKeepsOld() throws Exception {
        DirectoryImplementation first = mock(DirectoryImplementation.class);
        when(provider.createImplementation()).thenReturn(first).thenThrow(new IllegalStateException("boom"));
        reloadable.reload();

        assertThatThrownBy(reloadable::reload).isInstanceOf(IllegalStateException.class);

        verify(first, never()).close();
        assertThat(reloadable.currentProviderName()).isNotEqualTo("none");
    }

    @Test
    void closeClosesCurrent() throws Exception {
        DirectoryImplementation first = mock(DirectoryImplementation.class);
        when(provider.createImplementation()).thenReturn(first);
        reloadable.reload();

        reloadable.close();

        verify(first).close();
        assertThat(reloadable.currentProviderName()).isEqualTo("none");
    }

    @Test
    void proxyThrowsWhenNothingLoaded() {
        DirectoryUserProfileFetcher proxy = reloadable.injectService(DirectoryUserProfileFetcher.class);

        assertThatThrownBy(() -> proxy.fetchUserProfileById("1"))
            .isInstanceOf(DirectoryImplementationNotProvidedException.class);
    }

    @Test
    void proxyUnwrapsOriginalException() throws Exception {
        DirectoryImplementation impl = mock(DirectoryImplementation.class);
        DirectoryUserProfileFetcher fetcher = mock(DirectoryUserProfileFetcher.class);
        when(impl.injectService(any())).thenReturn(fetcher);
        when(fetcher.fetchUserProfileById("1")).thenThrow(new IllegalArgumentException("original"));
        when(provider.createImplementation()).thenReturn(impl);
        reloadable.reload();

        DirectoryUserProfileFetcher proxy = reloadable.injectService(DirectoryUserProfileFetcher.class);

        assertThatThrownBy(() -> proxy.fetchUserProfileById("1"))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessage("original");
    }

    @Test
    void proxyHandlesObjectMethodsItself() {
        DirectoryUserProfileFetcher proxy = reloadable.injectService(DirectoryUserProfileFetcher.class);

        assertThat(proxy.toString()).contains("DirectoryUserProfileFetcher");
        assertThat(proxy.equals(proxy)).isTrue();
        assertThat(proxy.equals(new Object())).isFalse();
        assertThat(proxy.hashCode()).isEqualTo(proxy.hashCode());
    }
}
