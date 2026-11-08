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

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserScopedValues;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolveUserRequestFilterTest {
    private final RequestUserInfoProvider provider = mock(RequestUserInfoProvider.class);
    private final ResolveUserRequestFilter filter = new ResolveUserRequestFilter(provider);

    @Test
    void bindsUserAndOnlyUserIdInMdcWhenAuthenticated() throws Exception {
        UserAuthInfo info = new UserAuthInfo("ID", "EMAIL", "NAME");
        when(provider.resolve()).thenReturn(Optional.of(info));

        AtomicReference<UserAuthInfo> bound = new AtomicReference<>();
        AtomicReference<String> mdcUserId = new AtomicReference<>();
        AtomicReference<String> mdcName = new AtomicReference<>();
        AtomicReference<String> mdcEmail = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            bound.set(UserScopedValues.USER_AUTH_INFO.get());
            mdcUserId.set(MDC.get("userId"));
            mdcName.set(MDC.get("userName"));
            mdcEmail.set(MDC.get("userEmail"));
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(bound.get()).isEqualTo(info);
        assertThat(mdcUserId.get()).isEqualTo("ID");
        assertThat(mdcName.get()).isNull();
        assertThat(mdcEmail.get()).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void doesNotBindUserWhenNotAuthenticated() throws Exception {
        when(provider.resolve()).thenReturn(Optional.empty());

        AtomicReference<Boolean> bound = new AtomicReference<>();
        FilterChain chain = (req, res) -> bound.set(UserScopedValues.USER_AUTH_INFO.isBound());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(bound.get()).isFalse();
    }
}
