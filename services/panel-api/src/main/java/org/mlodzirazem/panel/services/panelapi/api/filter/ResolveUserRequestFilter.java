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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.core.user.UserScopedValues;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ResolveUserRequestFilter extends OncePerRequestFilter {
    private final RequestUserInfoProvider requestUserInfoProvider;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<UserAuthInfo> authInfo = requestUserInfoProvider.resolve(request);

        if (authInfo.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        UserAuthInfo resolvedAuthInfo = authInfo.get();

        ScopedValue
            .where(UserScopedValues.USER_AUTH_INFO, resolvedAuthInfo)
            .run(() -> {
                try {
                    MDC.put("userId", resolvedAuthInfo.userId());
                    MDC.put("userName", resolvedAuthInfo.name());
                    MDC.put("userEmail", resolvedAuthInfo.email());

                    chainAndThrowUnchecked(request, response, filterChain);
                } finally {
                    MDC.remove("userId");
                    MDC.remove("userName");
                    MDC.remove("userEmail");
                }
            });
    }

    // Ugly trick to directly propagate checked exceptions
    @SneakyThrows
    private static void chainAndThrowUnchecked(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) {
        filterChain.doFilter(request, response);
    }
}
