"use client";

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

import { useEffect, useId, useState } from "react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { searchMembersAction } from "@/app/actions/members";
import formStyles from "@/components/form/Form.module.scss";

export type PickedMember = { id: number; name: string; memberId?: string };

/** Member search by name or number; with `hrefFor` the results are links, otherwise `onPick` gets the choice. */
export default function MemberPicker({label, onPick, hrefBase}: {
    label: string;
    onPick?: (member: PickedMember) => void;
    hrefBase?: string;
}) {
    const t = useTranslations('fees');
    const id = useId();
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<PickedMember[] | null>(null);
    const searching = query.trim().length >= 2;

    useEffect(() => {
        if (!searching) {
            return;
        }
        let cancelled = false;
        const handle = setTimeout(async () => {
            const outcome = await searchMembersAction(query);
            if (!cancelled) {
                setResults(outcome.ok ? (outcome.data ?? []) : []);
            }
        }, 300);
        return () => {
            cancelled = true;
            clearTimeout(handle);
        };
    }, [query, searching]);

    const describe = (m: PickedMember) => m.memberId ? `${m.name} (${m.memberId})` : m.name;

    return <div className={formStyles.field}>
        <label className={formStyles.label} htmlFor={id}>{label}</label>
        <input id={id}
               className={formStyles.input}
               type="search"
               autoComplete="off"
               value={query}
               onChange={e => setQuery(e.target.value)}
               aria-describedby={`${id}-hint`}/>
        <span className={formStyles.hint} id={`${id}-hint`}>{t('memberSearchHint')}</span>
        {searching && results !== null && <ul>
            {results.length === 0 && <li>{t('noMemberResults')}</li>}
            {results.map(m => <li key={m.id}>
                {hrefBase
                    ? <Link href={`${hrefBase}/${m.id}`}>{describe(m)}</Link>
                    : <button type="button" onClick={() => {
                        onPick?.(m);
                        setQuery('');
                        setResults(null);
                    }}>{describe(m)}</button>}
            </li>)}
        </ul>}
    </div>;
}
