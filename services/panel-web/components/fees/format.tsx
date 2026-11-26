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

import React from "react";
import { useFormatter, useTranslations } from "next-intl";

export function Money({value}: { value: string | null | undefined }) {
    const format = useFormatter();
    return <>{value === null || value === undefined
        ? '—'
        : format.number(Number(value), {style: 'currency', currency: 'PLN'})}</>;
}

/** A "2026-11" month, shown by its name; UTC keeps it from sliding into the neighbouring month. */
export function Month({value}: { value: string | null | undefined }) {
    const format = useFormatter();
    if (!value) {
        return <>—</>;
    }
    const [year, month] = value.split('-').map(Number);
    return <>{format.dateTime(new Date(Date.UTC(year, month - 1, 1)), {
        month: 'long', year: 'numeric', timeZone: 'UTC',
    })}</>;
}

export function Period({from, to}: { from: string; to?: string | null }) {
    const t = useTranslations('fees');
    return <><Month value={from}/> – {to ? <Month value={to}/> : t('untilRevoked')}</>;
}
