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

package org.mlodzirazem.panel.services.panelapi.fees;

/**
 * Outcome of a dues generation: how many members were processed and how many dues were created, recalculated or
 * cancelled (exemptions).
 */
public record DuesRun(int members, int created, int updated, int cancelled) {
    public static final DuesRun NONE = new DuesRun(0, 0, 0, 0);

    public boolean changed() {
        return created + updated + cancelled > 0;
    }
}
