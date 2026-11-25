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

package org.mlodzirazem.panel.services.panelapi.admin;

/**
 * One problem of one CSV row (or of the file itself, with {@code rowLabel = "file"}). Carries
 * identifiers and codes only: the member number points at the CSV row, the column name says which
 * cell is wrong, and the code says why (statutory violations keep the codes of
 * {@code BodyMembershipService.check()}, e.g. INCOMPATIBLE_MEMBERSHIP of art. 18 ust. 1 pkt 6).
 * No names, no free-text messages — the check's own texts are not always PII-free.
 */
public record MandateImportProblem(String rowLabel, String column, String code, boolean error) {
    public String render() {
        return rowLabel + ": " + column + " → " + code;
    }
}
