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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mlodzirazem.panel.services.panelapi.core.user.UserAuthInfo;
import org.mlodzirazem.panel.services.panelapi.directory.api.components.DirectoryUserProfileFetcher;
import org.mlodzirazem.panel.services.panelapi.directory.api.model.DirectoryUserProfile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestUserInfoProvider {
    private final DirectoryUserProfileFetcher userProfileFetcher;

    public Optional<UserAuthInfo> resolve(HttpServletRequest req) {
        String userId = req.getHeader("X-Auth-User-Id");
        String userName = req.getHeader("X-Auth-User-Name");
        String userEmail = req.getHeader("X-Auth-User-Email");

        if (userId == null) {
            return Optional.empty();
        }

        if (userName == null || userEmail == null) {
            return fetchFromDirectory(userId);
        }

        return Optional.of(new UserAuthInfo(userId, userEmail, userName));
    }

    private Optional<UserAuthInfo> fetchFromDirectory(String userId) {
        Optional<DirectoryUserProfile> directoryProfile = userProfileFetcher.fetchUserProfileById(userId);
        if (directoryProfile.isEmpty()) {
            // Such a case is possible, but it is highly unlikely.
            // Since the user has already been authenticated,
            // their profile should be available in the directory.
            // Consistency issues are a thing, though.
            log.atWarn()
               .addKeyValue("userId", userId)
               .log("Missing directory user profile: " + userId);
        }

        String fetchedPreferredName = directoryProfile.map(DirectoryUserProfile::preferredName).orElse(null);
        String fetchedEmail = directoryProfile.map(DirectoryUserProfile::email).orElse(null);

        return Optional.of(new UserAuthInfo(userId, fetchedEmail, fetchedPreferredName));
    }
}
