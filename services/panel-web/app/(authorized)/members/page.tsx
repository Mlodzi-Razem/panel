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

import { getTranslations } from "next-intl/server";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import MemberFilters from "@/components/members/MemberFilters";
import MembersTable from "@/components/members/MembersTable";
import { Card, LinkButton, Page, pageStyles } from "@/components/page/Page";
import Link from "next/link";
import { MEMBER_STATUSES, MEMBERSHIP_TYPES } from "@/panel-api/members/fields";
import { listDistricts, listMembers } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { PERMISSIONS } from "@/panel-api/members/types";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

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
    if (!me.canSeeMembers) {
        return <Page title={t('title')}><ApiErrorNotice error={{status: 403, code: 'FORBIDDEN', message: ''}}/></Page>;
    }

    const params = await searchParams;
    const q = single(params['q'])?.trim().slice(0, 100) ?? '';
    const pageNumber = Math.max(0, Number.parseInt(single(params['page']) ?? '0', 10) || 0);
    const districtParam = Number.parseInt(single(params['district']) ?? '', 10);
    const status = oneOf(single(params['status']), MEMBER_STATUSES);
    const membershipType = oneOf(single(params['membershipType']), MEMBERSHIP_TYPES);

    const [districtsResult, membersResult] = await Promise.all([
        listDistricts(),
        listMembers({
            page: pageNumber,
            q,
            status,
            membershipType,
            district: Number.isFinite(districtParam) ? districtParam : undefined,
        }),
    ]);

    const districts = districtsResult.ok ? districtsResult.data : [];
    const canCreate = districts.some(d => d.permissions.includes(PERMISSIONS.formalManage));
    const canDecide = canCreate || me.permissions.global.includes(PERMISSIONS.formalManage)
        || me.permissions.perDistrict.some(d => d.permissions.includes(PERMISSIONS.formalManage));
    const canReadFormal = districts.some(d => d.permissions.includes(PERMISSIONS.formalRead))
        || me.permissions.global.includes(PERMISSIONS.formalRead);

    const hrefFor = (page: number) => {
        const next = new URLSearchParams();
        if (q) next.set('q', q);
        if (status) next.set('status', status);
        if (membershipType) next.set('membershipType', membershipType);
        if (Number.isFinite(districtParam)) next.set('district', String(districtParam));
        if (page > 0) next.set('page', String(page));
        const qs = next.toString();
        return qs ? `/members?${qs}` : '/members';
    };

    return <Page title={t('title')}
                 actions={<>
                     {canCreate && <LinkButton href="/members/new" primary>{t('newMember')}</LinkButton>}
                     {canDecide && <LinkButton href="/members/change-requests">{t('changeRequests')}</LinkButton>}
                     {canReadFormal && <LinkButton href="/members/ending">{t('endingThisYear')}</LinkButton>}
                 </>}>
        <Card>
            <MemberFilters districts={districts.map(d => ({id: d.id, name: d.name}))}
                           showStatus={canReadFormal}/>
            {membersResult.ok
                ? <>
                    <p aria-live="polite" className={pageStyles.muted}>
                        {t('resultCount', {count: membersResult.data.totalItems})}
                    </p>
                    <MembersTable items={membersResult.data.items}/>
                    {membersResult.data.totalPages > 1 && <nav className={pageStyles.pager} aria-label={t('pagination')}>
                        <Link href={hrefFor(pageNumber - 1)}
                              className={pageStyles.linkButton}
                              aria-disabled={pageNumber <= 0}
                              tabIndex={pageNumber <= 0 ? -1 : undefined}>
                            {t('previous')}
                        </Link>
                        <span>{t('pageOf', {page: membersResult.data.page + 1, total: membersResult.data.totalPages})}</span>
                        <Link href={hrefFor(pageNumber + 1)}
                              className={pageStyles.linkButton}
                              aria-disabled={pageNumber + 1 >= membersResult.data.totalPages}
                              tabIndex={pageNumber + 1 >= membersResult.data.totalPages ? -1 : undefined}>
                            {t('next')}
                        </Link>
                    </nav>}
                </>
                : <ApiErrorNotice error={membersResult.error}/>}
        </Card>
    </Page>;
}
