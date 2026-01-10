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

package org.mlodzirazem.panel.services.panelapi.api.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryUserProfileFetcher;
import org.mlodzirazem.panel.services.panelapi.directory.api.model.DirectoryUserProfile;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestUserInfoProviderTest {
    @Mock
    DirectoryUserProfileFetcher directoryUserProfileFetcher;

    @InjectMocks
    RequestUserInfoProvider provider;

    @Test
    void resolvesExclusivelyUsingHeadersWhenCompleteDataProvided() {
        var userInfo = new UserAuthInfo("ID", "EMAIL", "NAME");
        HttpServletRequest req = mockRequest(userInfo);

        Optional<UserAuthInfo> resolved = provider.resolve(req);

        assertThat(resolved).isPresent().contains(userInfo);
        verifyNoInteractions(directoryUserProfileFetcher);
    }

    @Test
    void usesUserProfileFetcherWhenOnlyIdProvided() {
        var userInfo = new UserAuthInfo("ID", null, null);
        HttpServletRequest req = mockRequest(userInfo);

        DirectoryUserProfile directoryProfile = new DirectoryUserProfile(
            userInfo.userId(),
            "NAME",
            "EMAIL"
        );
        when(directoryUserProfileFetcher.fetchUserProfileById(userInfo.userId()))
            .thenReturn(Optional.of(directoryProfile));

        Optional<UserAuthInfo> resolved = provider.resolve(req);

        var expectedUserInfo = new UserAuthInfo("ID", "EMAIL", "NAME");

        assertThat(resolved).isPresent().contains(expectedUserInfo);
    }

    @Test
    void returnsJustIdWhenNoDirectoryProfile() {
        var userInfo = new UserAuthInfo("ID", null, null);
        HttpServletRequest req = mockRequest(userInfo);

        when(directoryUserProfileFetcher.fetchUserProfileById(userInfo.userId()))
            .thenReturn(Optional.empty());

        Optional<UserAuthInfo> resolved = provider.resolve(req);
        assertThat(resolved).isPresent().contains(userInfo);
    }

    @Test
    void returnsEmptyWhenNoUserProfileId() {
         HttpServletRequest req = mockRequest(null);

        Optional<UserAuthInfo> resolved  = provider.resolve(req);

        assertThat(resolved).isEmpty();
    }

    private HttpServletRequest mockRequest(@Nullable UserAuthInfo info) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        if (info == null) {
            when(req.getHeader("X-Auth-User-Id")).thenReturn(null);
            when(req.getHeader("X-Auth-User-Name")).thenReturn(null);
            when(req.getHeader("X-Auth-User-Email")).thenReturn(null);
        } else {
            when(req.getHeader("X-Auth-User-Id")).thenReturn(info.userId());
            when(req.getHeader("X-Auth-User-Name")).thenReturn(info.name());
            when(req.getHeader("X-Auth-User-Email")).thenReturn(info.email());
        }
        return req;
    }
}