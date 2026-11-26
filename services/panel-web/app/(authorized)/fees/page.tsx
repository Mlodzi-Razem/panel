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

import { getTranslations } from "next-intl/server";
import FeeTabs from "@/components/fees/FeeTabs";
import { AdjustmentsTable, BalanceSummary, DuesTable, PaymentsTable } from "@/components/fees/FeeTables";
import { Money, Month } from "@/components/fees/format";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate } from "@/components/members/format";
import { Card, DescriptionList, Notice, Page } from "@/components/page/Page";
import { getMyFees } from "@/panel-api/fees/fees";
import { requireMe } from "@/panel-api/members/requireMe";

export default async function FeesPage() {
    const me = await requireMe();
    if (!me) {
        return null;
    }
    const t = await getTranslations('fees');
    const result = await getMyFees();

    return <Page title={t('title')} subtitle={t('subtitle')}>
        <FeeTabs me={me} active="mine"/>
        {!result.ok ? <ApiErrorNotice error={result.error}/> : <>
            {result.data.arrears.suspendsElectoralRights && <Notice tone="danger">{t('rightsSuspended')}</Notice>}
            <Card title={t('balanceTitle')} id="balance">
                <BalanceSummary balance={result.data.balance}/>
                {result.data.nextDue && <p>
                    {t('nextDue')}: <Month value={result.data.nextDue.month}/>,{' '}
                    <Money value={result.data.nextDue.amount}/>
                </p>}
                {result.data.arrears.since && <p>
                    {t('arrearsSince')} <FormattedDate value={result.data.arrears.since}/>
                </p>}
            </Card>
            <Card title={t('howToPay')} id="how-to-pay">
                {!result.data.payTo.accountIban && <Notice tone="warning">{t('noAccount')}</Notice>}
                <DescriptionList>
                    <dt>{t('account')}</dt>
                    <dd>{result.data.payTo.accountIban ?? '—'}</dd>
                    <dt>{t('recipient')}</dt>
                    <dd>{result.data.payTo.recipientName}</dd>
                    <dt>{t('transferTitle')}</dt>
                    <dd>{result.data.payTo.title}</dd>
                </DescriptionList>
            </Card>
            <Card title={t('openDues')} id="open-dues">
                <DuesTable dues={result.data.openDues}/>
            </Card>
            <Card title={t('payments')} id="payments">
                <PaymentsTable payments={result.data.history.payments}/>
            </Card>
            <Card title={t('adjustments')} id="adjustments">
                <AdjustmentsTable adjustments={result.data.history.adjustments} reasonsIncluded/>
            </Card>
            <Card title={t('allDues')} id="all-dues">
                <DuesTable dues={result.data.history.dues}/>
            </Card>
        </>}
    </Page>;
}
