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

package org.mlodzirazem.panel.services.panelapi.bodies;

/**
 * Removing one's own function (ending or voiding one's own mandate) takes two separate confirmations;
 * a request that carries fewer than both writes nothing.
 */
public enum SelfRemovalConfirmation {
    /** "I want to remove my own function." */
    SELF_REMOVAL,
    /** The second, final confirmation, given after the consequences were shown. */
    SELF_REMOVAL_FINAL
}
