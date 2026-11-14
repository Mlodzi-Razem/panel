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

public enum ProblemCode {
    REQUIRED(true),
    FORMAT(true),
    CHECKSUM(true),
    UNPARSEABLE_DATE(true),
    UNKNOWN_DISTRICT(true),
    MATCHES_MULTIPLE_MEMBERS(true),
    MEMBER_ID_CONFLICT(true),
    DUPLICATE_IN_FILE(true),
    RULE_VIOLATION(true),
    BIRTH_DATE_MISMATCH(false),
    AGE_MISMATCH(false),
    AGE_UNPARSEABLE(false),
    VOTING_RIGHT_MISMATCH(false),
    VOTING_RIGHT_UNPARSEABLE(false),
    MEMBERSHIP_AGE_LIMIT_EXCEEDED(false),
    REMOVAL_VOTE_FOLLOW_UP(false),
    UNKNOWN_COLUMN(false),
    DUPLICATE_COLUMN(false);

    private final boolean error;

    ProblemCode(boolean error) {
        this.error = error;
    }

    public boolean isError() {
        return error;
    }
}
