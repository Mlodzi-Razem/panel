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
import { getTranslations } from "next-intl/server";
import { IdentificationCardIcon, MapPinIcon, PlusIcon, SealCheckIcon } from "@phosphor-icons/react/ssr";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import MembersTable from "@/components/members/MembersTable";
import { LinkButton, Page } from "@/components/page/Page";
import { Pagination, parseSort, ResultCount, Toolbar } from "@/components/ui/Blocks";
import { UrlFilterSelect, UrlSearchField } from "@/components/ui/UrlControls";
import { listDistricts, listMembers } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { MEMBER_STATUSES, MEMBERSHIP_TYPES } from "@/panel-api/members/fields";
import { PERMISSIONS } from "@/panel-api/members/types";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const DEFAULT_SIZE = 25;
const SIZES = [10, 25, 50, 100];
const SORT_FIELDS = ['name', 'memberId', 'status', 'membershipType', 'district'] as const;

function single(value: string | string[] | undefined): string | undefined {
    return Array.isArray(value) ? value[0] : value;
}

function oneOf<T extends string>(value: string | undefined, allowed: readonly T[]): T | undefined {
    return allowed.find(a => a === value);
}

export default async function MembersPage({searchParams}: { searchParams: SearchParams }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('members');
    const tEnums = await getTranslations('enums');
    if (!me.canSeeMembers) {
        return <Page title={t('title')}><ApiErrorNotice error={{status: 403, code: 'FORBIDDEN', message: ''}}/></Page>;
    }

    const params = await searchParams;
    const q = single(params['q'])?.trim().slice(0, 100) ?? '';
    const pageNumber = Math.max(0, Number.parseInt(single(params['page']) ?? '0', 10) || 0);
    const sizeParam = Number.parseInt(single(params['size']) ?? '', 10);
    const size = SIZES.includes(sizeParam) ? sizeParam : DEFAULT_SIZE;
    const districtParam = Number.parseInt(single(params['district']) ?? '', 10);
    const status = oneOf(single(params['status']), MEMBER_STATUSES);
    const membershipType = oneOf(single(params['membershipType']), MEMBERSHIP_TYPES);
    const sortRaw = single(params['sort']);
    const sort = parseSort(sortRaw, SORT_FIELDS, {field: 'name', dir: 'asc'});

    const [districtsResult, membersResult] = await Promise.all([
        listDistricts(),
        listMembers({
            page: pageNumber,
            size,
            q,
            status,
            membershipType,
            district: Number.isFinite(districtParam) ? districtParam : undefined,
            sort: sortRaw ? `${sort.field}:${sort.dir}` : undefined,
        }),
    ]);

    const districts = districtsResult.ok ? districtsResult.data : [];
    const canCreate = districts.some(d => d.permissions.includes(PERMISSIONS.formalManage));
    const canReadFormal = districts.some(d => d.permissions.includes(PERMISSIONS.formalRead))
        || me.permissions.global.includes(PERMISSIONS.formalRead);

    const hrefWith = (changes: Record<string, string | undefined>) => {
        const next = new URLSearchParams();
        const current: Record<string, string | undefined> = {
            q: q || undefined,
            status,
            membershipType,
            district: Number.isFinite(districtParam) ? String(districtParam) : undefined,
            size: size !== DEFAULT_SIZE ? String(size) : undefined,
            sort: sortRaw ? `${sort.field}:${sort.dir}` : undefined,
            page: pageNumber > 0 ? String(pageNumber) : undefined,
        };
        for (const [key, value] of Object.entries({...current, ...changes})) {
            if (value) {
                next.set(key, value);
            }
        }
        const qs = next.toString();
        return qs ? `/members?${qs}` : '/members';
    };

    return <Page title={t('title')}
                 subtitle={t('subtitle')}
                 actions={canCreate && <LinkButton href="/members/new" primary><PlusIcon aria-hidden/>{t('newMember')}</LinkButton>}>
        <Toolbar end={membersResult.ok && <ResultCount>{t('resultCount', {count: membersResult.data.totalItems})}</ResultCount>}>
            <UrlSearchField label={t('search')} placeholder={t('searchPlaceholder')}/>
            <UrlFilterSelect param="district"
                             label={t('district')}
                             allLabel={t('allDistricts')}
                             icon={<MapPinIcon aria-hidden/>}
                             options={districts.map(d => ({value: String(d.id), label: d.name}))}/>
            {canReadFormal && <UrlFilterSelect param="status"
                                               label={t('status')}
                                               allLabel={t('anyStatus')}
                                               icon={<SealCheckIcon aria-hidden/>}
                                               options={MEMBER_STATUSES.map(s => ({value: s, label: tEnums(`memberStatus.${s}`)}))}/>}
            <UrlFilterSelect param="membershipType"
                             label={t('membershipType')}
                             allLabel={t('anyType')}
                             icon={<IdentificationCardIcon aria-hidden/>}
                             options={MEMBERSHIP_TYPES.map(s => ({value: s, label: tEnums(`membershipType.${s}`)}))}/>
        </Toolbar>
        {membersResult.ok
            ? <>
                <MembersTable items={membersResult.data.items}
                              sort={sort}
                              sortHref={sortParam => hrefWith({sort: sortParam, page: undefined})}/>
                <Pagination page={membersResult.data.page}
                            totalPages={membersResult.data.totalPages}
                            totalItems={membersResult.data.totalItems}
                            size={size}
                            defaultSize={DEFAULT_SIZE}
                            sizeOptions={SIZES}
                            hrefFor={p => hrefWith({page: p > 0 ? String(p) : undefined})}/>
            </>
            : <ApiErrorNotice error={membersResult.error}/>}
    </Page>;
}
