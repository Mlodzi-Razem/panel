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

/** PESEL helpers shared by the declaration-form wizard (client) and panel-api validationlayers. Pure functions, no logging. */

export const PESEL_LENGTH = 11;

const PESEL_WEIGHTS = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3] as const;

/** True only for 11 digits with a valid official checksum (the last digit is `(10 - sum mod 10) mod 10`). */
export function isValidPesel(pesel: string): boolean {
    const value = pesel.trim();
    if (!/^\d{11}$/.test(value)) {
        return false;
    }
    const digits = [...value].map(Number);
    const weightedSum = digits.slice(0, 10).reduce((sum, digit, index) => sum + digit * PESEL_WEIGHTS[index], 0);
    const expected = (10 - (weightedSum % 10)) % 10;
    return digits[10] === expected;
}

/**
 * The birth date encoded in a PESEL (digits 1–6), or null for values that do not decode cleanly.
 * The month carries the century: 01–12 → 1900s, 21–32 → 2000s, 41–52 → 2100s, 81–92 → 1800s.
 */
export function peselBirthDate(pesel: string): Date | null {
    const value = pesel.trim();
    if (!/^\d{11}$/.test(value)) {
        return null;
    }
    const rawMonth = Number(value.slice(2, 4));
    const day = Number(value.slice(4, 6));
    const year2 = Number(value.slice(0, 2));

    let century = 1900;
    let month = rawMonth;
    if (rawMonth >= 81) {
        century = 1800;
        month = rawMonth - 80;
    } else if (rawMonth >= 41) {
        century = 2100;
        month = rawMonth - 40;
    } else if (rawMonth >= 21) {
        century = 2000;
        month = rawMonth - 20;
    }

    if (month < 1 || month > 12 || day < 1 || day > 31) {
        return null;
    }
    const date = new Date(Date.UTC(century + year2, month - 1, day));
    return date.getUTCMonth() === month - 1 && date.getUTCDate() === day ? date : null;
}
