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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.body;

public enum BodyKind {
    GENERAL_ASSEMBLY,
    NATIONAL_BOARD,
    PEER_COURT,
    AUDIT_COMMISSION,
    ELECTORAL_COMMISSION,
    PRESS_SPOKESPERSON,
    TEAM,
    DISTRICT,
    DISTRICT_ASSEMBLY,
    DISTRICT_BOARD,
    DISTRICT_TREASURER,
    DISTRICT_COMMISSIONER,
    LOCAL_CIRCLE;

    public boolean isDistrictFunction() {
        return this == DISTRICT_BOARD
            || this == DISTRICT_TREASURER
            || this == DISTRICT_COMMISSIONER
            || this == LOCAL_CIRCLE;
    }
}
