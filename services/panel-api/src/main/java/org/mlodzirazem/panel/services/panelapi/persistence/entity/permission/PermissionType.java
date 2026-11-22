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

package org.mlodzirazem.panel.services.panelapi.persistence.entity.permission;

public enum PermissionType {
    FEES_READ,
    FEES_MANAGE,
    FORMAL_PERSONAL_DATA_READ,
    FORMAL_PERSONAL_DATA_MANAGE,
    CONTACT_PERSONAL_DATA_READ,
    CONTACT_PERSONAL_DATA_MANAGE,
    DIRECTORY_GROUPS_MANAGE,
    DIRECTORY_USERS_MANAGE,
    BODIES_MANAGE,
    RECRUITMENT_READ,
    RECRUITMENT_MANAGE,
    RECRUITMENT_APPROVE
}
