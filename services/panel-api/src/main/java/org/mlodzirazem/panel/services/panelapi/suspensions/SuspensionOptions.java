/*
 * Copyright (C) 2026 Stowarzyszenie Młodzi Razem
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

package org.mlodzirazem.panel.services.panelapi.suspensions;

import org.jspecify.annotations.Nullable;
import org.mlodzirazem.panel.services.panelapi.persistence.entity.body.BodyKind;

import java.util.List;

/**
 * Bodies that may take or lift a suspension of the member, and per body the kinds it is competent for. This is the
 * only source of the matrix for the front end. {@code appealHintDays} is the suggested length of the appeal period.
 */
public record SuspensionOptions(int appealHintDays, List<Body> bodies) {
    public record Body(long id, String name, @Nullable BodyKind kind, List<SuspensionKind> allowedFor) {
    }
}
