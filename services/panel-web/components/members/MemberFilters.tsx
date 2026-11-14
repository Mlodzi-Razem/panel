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

"use client";

import React, { useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import formStyles from "@/components/form/Form.module.scss";
import { MEMBER_STATUSES, MEMBERSHIP_TYPES } from "@/panel-api/members/fields";

export type MemberFiltersProps = {
    districts: { id: number; name: string }[];
    showStatus: boolean;
};

const DEBOUNCE_MS = 300;

export default function MemberFilters({districts, showStatus}: MemberFiltersProps) {
    const t = useTranslations('members');
    const tEnums = useTranslations('enums');
    const router = useRouter();
    const pathname = usePathname();
    const searchParams = useSearchParams();
    const currentQ = searchParams.get('q') ?? '';
    const [q, setQ] = useState(currentQ);

    const update = (changes: Record<string, string>) => {
        const next = new URLSearchParams(searchParams.toString());
        for (const [key, value] of Object.entries(changes)) {
            if (value) {
                next.set(key, value);
            } else {
                next.delete(key);
            }
        }
        next.delete('page');
        const qs = next.toString();
        router.replace(qs ? `${pathname}?${qs}` : pathname);
    };

    useEffect(() => {
        if (q === currentQ) {
            return;
        }
        const handle = setTimeout(() => {
            const next = new URLSearchParams(window.location.search);
            if (q.trim()) {
                next.set('q', q.trim());
            } else {
                next.delete('q');
            }
            next.delete('page');
            const qs = next.toString();
            router.replace(qs ? `${pathname}?${qs}` : pathname);
        }, DEBOUNCE_MS);
        return () => clearTimeout(handle);
    }, [q, currentQ, pathname, router]);

    return <form role="search"
                 aria-label={t('filtersLabel')}
                 className={formStyles.fieldGrid}
                 onSubmit={event => event.preventDefault()}>
        <div className={formStyles.field}>
            <label className={formStyles.label} htmlFor="members-q">{t('search')}</label>
            <input id="members-q"
                   type="search"
                   className={formStyles.input}
                   value={q}
                   placeholder={t('searchPlaceholder')}
                   maxLength={100}
                   autoComplete="off"
                   onChange={event => setQ(event.target.value)}/>
        </div>
        <div className={formStyles.field}>
            <label className={formStyles.label} htmlFor="members-district">{t('district')}</label>
            <select id="members-district"
                    className={formStyles.input}
                    value={searchParams.get('district') ?? ''}
                    onChange={event => update({district: event.target.value})}>
                <option value="">{t('allDistricts')}</option>
                {districts.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
        </div>
        {showStatus && <div className={formStyles.field}>
            <label className={formStyles.label} htmlFor="members-status">{t('status')}</label>
            <select id="members-status"
                    className={formStyles.input}
                    value={searchParams.get('status') ?? ''}
                    onChange={event => update({status: event.target.value})}>
                <option value="">{t('anyStatus')}</option>
                {MEMBER_STATUSES.map(s => <option key={s} value={s}>{tEnums(`memberStatus.${s}`)}</option>)}
            </select>
        </div>}
        <div className={formStyles.field}>
            <label className={formStyles.label} htmlFor="members-type">{t('membershipType')}</label>
            <select id="members-type"
                    className={formStyles.input}
                    value={searchParams.get('membershipType') ?? ''}
                    onChange={event => update({membershipType: event.target.value})}>
                <option value="">{t('anyType')}</option>
                {MEMBERSHIP_TYPES.map(s => <option key={s} value={s}>{tEnums(`membershipType.${s}`)}</option>)}
            </select>
        </div>
    </form>;
}
