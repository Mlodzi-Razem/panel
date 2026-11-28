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

import MemberTypeName from "@/components/bodies/MemberTypeName";
import React from "react";
import Link from "next/link";
import { useFormatter, useTranslations } from "next-intl";
import { BriefcaseIcon } from "@phosphor-icons/react/ssr";
import { FormattedDate } from "@/components/members/format";
import styles from "@/components/members/record/record.module.scss";
import { Badge, Card, DescriptionList, Notice, TableWrap } from "@/components/page/Page";
import { EmptyState, Pagination } from "@/components/ui/Blocks";
import { BodyFunction, FunctionHistoryItem, MemberDetail } from "@/panel-api/members/types";

export function BodyFunctionsCard({functions, linkBodies = true}: { functions: BodyFunction[]; linkBodies?: boolean }) {
    const t = useTranslations('members');
    return <Card id="functions" title={t('functionsTitle')} hint={t('functionsHint')}>
        {functions.length === 0
            ? <EmptyState icon={<BriefcaseIcon aria-hidden/>} title={t('noFunctions')}/>
            : <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('fnBody')}</th>
                        <th scope="col">{t('fnRole')}</th>
                        <th scope="col">{t('fnFrom')}</th>
                        <th scope="col">{t('fnTo')}</th>
                    </tr>
                </thead>
                <tbody>
                    {functions.map(f => <tr key={f.membershipId}>
                        <td>{linkBodies ? <Link href={`/bodies/${f.bodyId}`}>{f.bodyName}</Link> : f.bodyName}</td>
                        <td><Badge tone="brand"><MemberTypeName code={f.memberTypeCode} name={f.memberTypeName}/></Badge></td>
                        <td><FormattedDate value={f.validFrom}/></td>
                        <td>{f.validTo ? <FormattedDate value={f.validTo}/> : t('ongoing')}</td>
                    </tr>)}
                </tbody>
            </TableWrap>}
    </Card>;
}

type TimelineRow =
    | { kind: 'function'; item: FunctionHistoryItem }
    | { kind: 'gap'; from: string; to: string; days: number };

const DAY_MS = 86_400_000;

function daysBetween(from: string, to: string): number {
    return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS);
}

/**
 * Timeline rows, newest mandate first. Gaps are computed chronologically: a gap is a stretch in which no function
 * covers the day, i.e. between the end of everything before (validTo is the first day without the function) and
 * the next mandate's start; the rows are then reversed, so each gap sits between the two mandates it separates.
 */
function withGaps(history: FunctionHistoryItem[]): TimelineRow[] {
    const ascending = [...history].sort((a, b) => a.function.validFrom.localeCompare(b.function.validFrom)
        || a.function.membershipId - b.function.membershipId);
    const rows: TimelineRow[] = [];
    let coveredTo: string | null = null;
    let openEnded = false;

    for (const item of ascending) {
        const from = item.function.validFrom;
        const to = item.function.validTo ?? null;
        if (!openEnded && coveredTo !== null && from > coveredTo) {
            rows.push({kind: 'gap', from: coveredTo, to: from, days: daysBetween(coveredTo, from)});
        }
        rows.push({kind: 'function', item});
        if (to === null) {
            openEnded = true;
        } else if (!openEnded && (coveredTo === null || to > coveredTo)) {
            coveredTo = to;
        }
    }
    return rows.reverse();
}

export const FUNCTION_HISTORY_PAGE_SIZE = 10;
const FUNCTION_HISTORY_SIZE_OPTIONS = [10, 25, 50];

/** Reads `page` (0-based) and `size` from search params; invalid values fall back to the first page / default size. */
export function functionHistoryPaging(params: Record<string, string | string[] | undefined>): { page: number; size: number } {
    const one = (v: string | string[] | undefined) => Array.isArray(v) ? v[0] : v;
    const page = Number.parseInt(one(params['page']) ?? '', 10);
    const size = Number.parseInt(one(params['size']) ?? '', 10);
    return {
        page: Number.isFinite(page) && page > 0 ? page : 0,
        size: FUNCTION_HISTORY_SIZE_OPTIONS.includes(size) ? size : FUNCTION_HISTORY_PAGE_SIZE,
    };
}

/** Pages by mandates (`size` per page); a gap stays on the page of the newer mandate above it. */
function pageOfRows(rows: TimelineRow[], page: number, size: number): TimelineRow[] {
    let index = -1;
    return rows.filter(row => {
        if (row.kind === 'function') {
            index++;
        }
        return Math.floor(Math.max(index, 0) / size) === page;
    });
}

const STATE_TONE: Record<FunctionHistoryItem['state'], 'success' | 'info' | 'muted'> = {
    CURRENT: 'success',
    UPCOMING: 'info',
    ENDED: 'muted',
};

export function FunctionHistoryCard({history, linkBodies = true, page = 0, size = FUNCTION_HISTORY_PAGE_SIZE, basePath}: {
    history: FunctionHistoryItem[];
    linkBodies?: boolean;
    page?: number;
    size?: number;
    /** Path of the record page; the pager keeps `tab=functions`. Without it all mandates are shown on one page. */
    basePath?: string;
}) {
    const t = useTranslations('members');
    const totalPages = basePath ? Math.max(1, Math.ceil(history.length / size)) : 1;
    const current = Math.min(page, totalPages - 1);
    const rows = basePath ? pageOfRows(withGaps(history), current, size) : withGaps(history);
    const hrefFor = (p: number) =>
        `${basePath}?tab=functions${p > 0 ? `&page=${p}` : ''}${size !== FUNCTION_HISTORY_PAGE_SIZE ? `&size=${size}` : ''}`;
    return <Card id="function-history" title={t('functionHistoryTitle')} hint={t('functionHistoryHint')}>
        {history.length === 0
            ? <EmptyState icon={<BriefcaseIcon aria-hidden/>} title={t('functionHistoryEmpty')}/>
            : <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('fnBody')}</th>
                        <th scope="col">{t('fnRole')}</th>
                        <th scope="col">{t('fnFrom')}</th>
                        <th scope="col">{t('fnTo')}</th>
                        <th scope="col">{t('fnState')}</th>
                    </tr>
                </thead>
                <tbody>
                    {rows.map((row, index) => row.kind === 'gap'
                        ? <tr key={`gap-${index}`} data-gap="true">
                            <td colSpan={5}>
                                <span>{t('gapNoFunction')}</span>{' '}
                                <Badge tone="neutral">{t('gapLength', {days: row.days})}</Badge>{' '}
                                <span><FormattedDate value={row.from}/> – <FormattedDate value={row.to}/></span>
                            </td>
                        </tr>
                        : <tr key={`fn-${row.item.function.membershipId}`}>
                            <td>{linkBodies
                                ? <Link href={`/bodies/${row.item.function.bodyId}`}>{row.item.function.bodyName}</Link>
                                : row.item.function.bodyName}</td>
                            <td><Badge tone="brand"><MemberTypeName code={row.item.function.memberTypeCode}
                                                                    name={row.item.function.memberTypeName}/></Badge></td>
                            <td><FormattedDate value={row.item.function.validFrom}/></td>
                            <td>{row.item.function.validTo
                                ? <FormattedDate value={row.item.function.validTo}/>
                                : t('ongoing')}</td>
                            <td><Badge tone={STATE_TONE[row.item.state]}>
                                {t(row.item.state === 'CURRENT' ? 'stateCurrent'
                                    : row.item.state === 'UPCOMING' ? 'stateUpcoming' : 'stateEnded')}
                            </Badge></td>
                        </tr>)}
                </tbody>
            </TableWrap>}
        {basePath && history.length > FUNCTION_HISTORY_PAGE_SIZE &&
            <Pagination page={current}
                        totalPages={totalPages}
                        totalItems={history.length}
                        size={size}
                        defaultSize={FUNCTION_HISTORY_PAGE_SIZE}
                        sizeOptions={FUNCTION_HISTORY_SIZE_OPTIONS}
                        hrefFor={hrefFor}/>}
    </Card>;
}

export function ElectoralRights({member}: { member: MemberDetail }) {
    const t = useTranslations('electoral');
    const rights = member.electoralRights;
    if (!rights) {
        return null;
    }
    const votingDistrict = member.affiliations?.find(a => a.districtId === rights.votingDistrictId);
    return <Card id="electoral-rights" title={t('title')} hint={t('hint')}>
        <DescriptionList>
            <div>
                <dt>{t('eligibleLabel')}</dt>
                <dd><Badge tone={rights.eligible ? 'success' : 'warning'}>
                    {rights.eligible ? t('yes') : t('no')}
                </Badge></dd>
            </div>
            <div>
                <dt>{t('nationalLabel')}</dt>
                <dd><Badge tone={rights.canVoteInNationalElection ? 'success' : 'muted'}>
                    {rights.canVoteInNationalElection ? t('yes') : t('no')}
                </Badge></dd>
            </div>
            {votingDistrict && <div>
                <dt>{t('districtLabel')}</dt>
                <dd>{votingDistrict.districtName}</dd>
            </div>}
        </DescriptionList>
        <Notice tone={rights.eligible ? 'success' : 'warning'} role="status">
            <p>{rights.eligible ? t('eligible') : t('notEligible')}</p>
            {!rights.eligible && <ul>
                {!rights.activeOrdinaryMember && <li>{t('reasonNotOrdinary')}</li>}
                {rights.suspended && <li>{t('reasonSuspended')}</li>}
                {rights.feeArrears && <li>{t('reasonArrears')}</li>}
            </ul>}
            <p>{rights.canVoteInNationalElection ? t('nationalYes') : t('nationalNo')}</p>
            {votingDistrict && <p>{t('votingDistrict', {district: votingDistrict.districtName})}</p>}
        </Notice>
    </Card>;
}

export function MembershipEndNotice({endsOn}: { endsOn: string | null | undefined }) {
    const t = useTranslations('members');
    const format = useFormatter();
    if (!endsOn) {
        return null;
    }
    return <Notice tone="info" role="status">
        <p className={styles.rowTitle}>{t('membershipEndTitle')}</p>
        <p>{t('membershipEndText', {
            date: format.dateTime(new Date(`${endsOn}T00:00:00Z`), {dateStyle: 'long', timeZone: 'UTC'}),
        })}</p>
    </Notice>;
}
