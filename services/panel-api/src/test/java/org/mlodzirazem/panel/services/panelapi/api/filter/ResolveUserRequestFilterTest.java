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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfoResolver;
import org.mlodzirazem.panel.services.panelapi.core.user.impl.ScopedValueUserAuthInfoResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = ResolveUserRequestFilterTestController.class)
@Import(
    {
        ResolveUserRequestFilter.class,
        ScopedValueUserAuthInfoResolver.class,
        ResolveUserRequestFilterTest.Configuration.class
    }
)
class ResolveUserRequestFilterTest {
    @MockitoBean
    RequestUserInfoProvider userInfoProvider;

    @Autowired
    MockMvcTester mockMvc;

    @Test
    @SneakyThrows
    void resolvesUserWhenProvided() {
        UserAuthInfo userAuthInfo = new UserAuthInfo("ID", "EMAIL", "NAME");
        when(userInfoProvider.resolve(any())).thenReturn(Optional.of(userAuthInfo));

        mockMvc.get()
               .uri("/test")
               .assertThat()
               .hasStatus2xxSuccessful()
               .bodyText()
               .isEqualTo(userAuthInfo.toString());
    }

    @Test
    @SneakyThrows
    void doesNotResolvesUserWhenUserIsNotPresent() {
        when(userInfoProvider.resolve(any())).thenReturn(Optional.empty());

        mockMvc.get()
               .uri("/test")
               .assertThat()
               .hasStatus2xxSuccessful()
               .bodyText()
               .isEmpty();
    }

    static class Configuration {
        @Bean
        CacheManager cacheManager() {
            return new SimpleCacheManager();
        }
    }
}

@RestController
@RequiredArgsConstructor
class ResolveUserRequestFilterTestController {
    private final UserAuthInfoResolver authInfoResolver;

    @GetMapping("/test")
    public String test() {
        return authInfoResolver.resolve()
                               .map(Objects::toString)
                               .orElse("");
    }
}
