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

import Link from "next/link";
import { getTranslations } from "next-intl/server";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate } from "@/components/members/format";
import { Badge, Card, Muted, Notice, Page, TableWrap } from "@/components/page/Page";
import { Pagination, Toolbar } from "@/components/ui/Blocks";
import { UrlFilterSelect } from "@/components/ui/UrlControls";
import { requireMe } from "@/panel-api/members/requireMe";
import { hasPermission } from "@/panel-api/members/me";
import { listArrearsCandidates, listSuspensions } from "@/panel-api/members/suspensions";
import { PERMISSIONS } from "@/panel-api/members/types";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const STATES = ['OPEN', 'ENDED', 'VOIDED', 'ALL'] as const;

function single(value: string | string[] | undefined): string | undefined {
    return Array.isArray(value) ? value[0] : value;
}

export default async function SuspensionsPage({searchParams}: { searchParams: SearchParams }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('suspensions');
    const tEnums = await getTranslations('enums');
    const params = await searchParams;

    if (!hasPermission(me, PERMISSIONS.suspensionsRead)) {
        return <Page title={t('listTitle')} subtitle={t('hint')}>
            <Notice tone="info">{t('noAccess')}</Notice>
        </Page>;
    }

    const stateRaw = single(params['state']) ?? 'OPEN';
    const state = (STATES as readonly string[]).includes(stateRaw) ? stateRaw : 'OPEN';
    const needsAction = single(params['needsAction']) === 'true';
    const page = Math.max(0, Number(single(params['page'])) || 0);
    const size = 25;

    const result = await listSuspensions({state, needsAction, page, size});
    const candidates = hasPermission(me, PERMISSIONS.feesRead) ? await listArrearsCandidates() : null;

    const hrefFor = (target: number) => {
        const query = new URLSearchParams({state, page: String(target)});
        if (needsAction) {
            query.set('needsAction', 'true');
        }
        return `/suspensions?${query.toString()}`;
    };

    return <Page title={t('listTitle')} subtitle={t('hint')}>
        <Toolbar>
            <UrlFilterSelect param="state"
                             label={t('listFilterState')}
                             defaultValue="OPEN"
                             options={STATES.map(s => ({value: s, label: t(`listState.${s}`)}))}/>
            <UrlFilterSelect param="needsAction"
                             label={t('needsAction')}
                             defaultValue=""
                             options={[{value: 'true', label: t('needsAction')}]}/>
        </Toolbar>

        {result.ok
            ? <>
                {result.data.items.length === 0
                    ? <Muted>{t('listEmpty')}</Muted>
                    : <TableWrap>
                        <table>
                            <thead>
                                <tr>
                                    <th scope="col">{t('columnMember')}</th>
                                    <th scope="col">{t('columnKind')}</th>
                                    <th scope="col">{t('columnScope')}</th>
                                    <th scope="col">{t('columnBody')}</th>
                                    <th scope="col">{t('columnFrom')}</th>
                                    <th scope="col">{t('columnState')}</th>
                                    <th scope="col">{t('columnAction')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {result.data.items.map(row => <tr key={row.id}>
                                    <td>
                                        <Link href={`/members/${row.memberId}?tab=suspensions`}>
                                            {row.memberName ?? `#${row.memberId}`}
                                        </Link>
                                    </td>
                                    <td>{tEnums(`suspensionKind.${row.kind}`)}</td>
                                    <td>{tEnums(`suspensionScope.${row.scope}`)}</td>
                                    <td>{row.suspendingBodyName ?? '—'}</td>
                                    <td><FormattedDate value={row.start}/></td>
                                    <td><Badge>{tEnums(`suspensionState.${row.state}`)}</Badge></td>
                                    <td>{row.needsAction ? <Badge tone="danger">{t('needsAction')}</Badge> : '—'}</td>
                                </tr>)}
                            </tbody>
                        </table>
                    </TableWrap>}
                <Pagination page={result.data.page}
                            totalPages={result.data.totalPages}
                            totalItems={result.data.totalItems}
                            size={size}
                            defaultSize={25}
                            hrefFor={hrefFor}/>
            </>
            : <ApiErrorNotice error={result.error}/>}

        {candidates?.ok && <Card id="suspension-candidates" title={t('candidatesTitle')} hint={t('candidatesHint')}>
            {candidates.data.length === 0
                ? <Muted>{t('candidatesEmpty')}</Muted>
                : <TableWrap>
                    <table>
                        <thead>
                            <tr>
                                <th scope="col">{t('columnMember')}</th>
                                <th scope="col">{t('columnMonths')}</th>
                                <th scope="col">{t('columnSince')}</th>
                                <th scope="col">{t('columnAction')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {candidates.data.map(c => <tr key={c.memberId}>
                                <td>{c.memberName ?? `#${c.memberId}`}</td>
                                <td>{c.months}</td>
                                <td>{c.since ? <FormattedDate value={c.since}/> : '—'}</td>
                                <td>
                                    <Link href={`/members/${c.memberId}?tab=suspensions`}>{t('openCard')}</Link>
                                </td>
                            </tr>)}
                        </tbody>
                    </table>
                </TableWrap>}
        </Card>}
        {candidates && !candidates.ok && <ApiErrorNotice error={candidates.error}/>}
        <Notice tone="info">{t('candidatesNote')}</Notice>
    </Page>;
}
