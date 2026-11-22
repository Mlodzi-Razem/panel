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

package org.mlodzirazem.panel.services.panelapi.core.error;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

/**
 * Translates SQL 23505/23514 (unique/check violations) into domain errors by looking for the
 * concrete index/constraint name in the driver message — the same pattern the recruitment
 * module uses for its open-case e-mail index. Unmatched violations are rethrown untouched,
 * so nothing is silently swallowed.
 */
public final class ConstraintConflicts {
    private ConstraintConflicts() {
    }

    public static RuntimeException translate(
        DataIntegrityViolationException cause,
        Map<String, RuntimeException> mapped
    ) {
        String message = String.valueOf(cause.getMostSpecificCause().getMessage());

        return mapped.entrySet()
                     .stream()
                     .filter(entry -> message.contains(entry.getKey()))
                     .map(Map.Entry::getValue)
                     .findFirst()
                     .orElse(cause);
    }
}
