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
import { forbidden } from "@/components/fees/guard";
import FeeTabs, { readsFees } from "@/components/fees/FeeTabs";
import { Money, Month } from "@/components/fees/format";
import MemberPicker from "@/components/fees/MemberPicker";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate } from "@/components/members/format";
import { Badge, Card, Muted, Page, TableWrap } from "@/components/page/Page";
import { Tabs, Toolbar } from "@/components/ui/Blocks";
import { UrlFilterSelect } from "@/components/ui/UrlControls";
import { getArrears, getFeeOverview } from "@/panel-api/fees/fees";
import { listDistricts } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { PERMISSIONS } from "@/panel-api/members/types";

/** All overdue members, or only those past the 3 months after which Panel grants no electoral rights. */
const THRESHOLDS = [0, 3] as const;

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function FinancePage({searchParams}: { searchParams: SearchParams }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }
    const t = await getTranslations('fees');
    if (!readsFees(me)) {
        return forbidden(t('title'));
    }
    const params = await searchParams;
    const requested = Number(params['over'] ?? 0);
    const over = THRESHOLDS.find(v => v === requested) ?? 0;
    const districtParam = Number(params['district']);
    const district = Number.isInteger(districtParam) && districtParam > 0 ? districtParam : undefined;

    const [overview, arrears, districts] = await Promise.all([
        getFeeOverview(12, district),
        getArrears(over, district),
        listDistricts(),
    ]);
    const readable = districts.ok ? districts.data.filter(d => d.permissions.includes(PERMISSIONS.feesRead)) : [];
    const hrefWith = (value: number) => {
        const query = new URLSearchParams();
        if (value > 0) {
            query.set('over', String(value));
        }
        if (district) {
            query.set('district', String(district));
        }
        const qs = query.toString();
        return qs ? `/fees/finance?${qs}` : '/fees/finance';
    };

    return <Page title={t('title')} subtitle={t('financeSubtitle')}>
        <FeeTabs me={me} active="finance"/>
        <Toolbar>
            <UrlFilterSelect param="district" label={t('district')} allLabel={t('allDistricts')}
                             options={readable.map(d => ({value: String(d.id), label: d.name}))}/>
        </Toolbar>
        {me.canSeeMembers && <Card title={t('openCard')} id="member-search">
            <MemberPicker label={t('findMember')} hrefBase="/fees/members"/>
        </Card>}
        <Card title={t('arrears')} hint={t('arrearsHint')} id="arrears">
            <Tabs label={t('arrearsFilter')}
                  items={THRESHOLDS.map(v => ({
                      href: hrefWith(v), label: t(`thresholds.t${v}`), active: v === over,
                  }))}/>
            {!arrears.ok ? <ApiErrorNotice error={arrears.error}/>
                : arrears.data.length === 0 ? <p><Muted>{t('noArrears')}</Muted></p>
                    : <TableWrap>
                        <thead>
                            <tr>
                                <th scope="col">{t('member')}</th>
                                <th scope="col">{t('district')}</th>
                                <th scope="col">{t('since')}</th>
                                <th scope="col">{t('monthsCount')}</th>
                                <th scope="col">{t('overdueAmount')}</th>
                                <th scope="col">{t('thresholdLabel')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {arrears.data.map(row => <tr key={row.memberId}>
                                <td>
                                    <Link href={`/fees/members/${row.memberId}`}>{row.displayName}</Link>
                                    {row.memberNumber && <div><Muted>{row.memberNumber}</Muted></div>}
                                </td>
                                <td>{row.districtName ?? '—'}</td>
                                <td><FormattedDate value={row.since}/></td>
                                <td>{row.months}</td>
                                <td><Money value={row.overdueAmount}/></td>
                                <td>
                                    {row.suspendsElectoralRights && <Badge tone="warning">{t('noRightsBadge')}</Badge>}
                                </td>
                            </tr>)}
                        </tbody>
                    </TableWrap>}
        </Card>
        <Card title={t('overview')} id="overview">
            {!overview.ok ? <ApiErrorNotice error={overview.error}/> : <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('month')}</th>
                        <th scope="col">{t('expected')}</th>
                        <th scope="col">{t('collected')}</th>
                        <th scope="col">{t('outstanding')}</th>
                        <th scope="col">{t('paidMembers')}</th>
                    </tr>
                </thead>
                <tbody>
                    {[...overview.data.months].reverse().map(row => <tr key={row.month}>
                        <td><Month value={row.month}/></td>
                        <td><Money value={row.expected}/></td>
                        <td><Money value={row.collected}/></td>
                        <td><Money value={row.outstanding}/></td>
                        <td>{row.membersPaid} / {row.members}</td>
                    </tr>)}
                </tbody>
            </TableWrap>}
        </Card>
    </Page>;
}
