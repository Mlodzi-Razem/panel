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

import java.util.List;

/**
 * Outcome of the rules without writing anything: violations block, warnings need confirmation.
 */
public record SuspensionCheck(
    List<Violation> violations,
    List<SuspensionWarning> warnings,
    List<MandateRef> mandatesAtStake
) {
    public record Violation(String code, String message) {
    }

    public boolean blocked() {
        return !violations.isEmpty();
    }

    public boolean needsConfirmation() {
        return warnings.stream().anyMatch(w -> !w.confirmed());
    }
}
