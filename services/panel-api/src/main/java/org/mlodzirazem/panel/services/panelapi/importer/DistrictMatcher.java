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

package org.mlodzirazem.panel.services.panelapi.importer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DistrictMatcher {
    record District(long id, String name, String code) {
    }

    private static final Set<String> NOISE = Set.of("okreg", "okregu", "woj", "wojewodztwo", "wojewodztwa");
    private static final List<String> SUFFIXES = List.of("iego", "ego", "ie", "i", "a", "e");

    private final Map<String, Long> byKey = new HashMap<>();

    DistrictMatcher(List<District> districts) {
        for (District district : districts) {
            String slug = district.code().startsWith("district-")
                ? district.code().substring("district-".length())
                : district.code();

            byKey.putIfAbsent(key(district.name()), district.id());
            byKey.putIfAbsent(key(slug), district.id());
        }
    }

    Optional<Long> match(String value) {
        String key = key(value);

        return key.isEmpty() ? Optional.empty() : Optional.ofNullable(byKey.get(key));
    }

    static String key(String value) {
        return Stream.of(Text.fold(value).split("[^a-z0-9]+"))
                     .filter(token -> !token.isEmpty() && !NOISE.contains(token))
                     .map(DistrictMatcher::stem)
                     .collect(Collectors.joining(" "));
    }

    private static String stem(String token) {
        for (String suffix : SUFFIXES) {
            if (token.endsWith(suffix) && token.length() - suffix.length() >= 3) {
                return token.substring(0, token.length() - suffix.length());
            }
        }

        return token;
    }
}
