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
import FeeTabs, { managesFees } from "@/components/fees/FeeTabs";
import { Money } from "@/components/fees/format";
import { forbidden } from "@/components/fees/guard";
import { AssignTransferForm, IgnoreTransferForm, ReopenTransferForm } from "@/components/fees/TransactionForms";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate } from "@/components/members/format";
import { Card, DescriptionList, Muted, Page } from "@/components/page/Page";
import { Pagination, Tabs } from "@/components/ui/Blocks";
import { listTransactions } from "@/panel-api/fees/fees";
import { TRANSACTION_STATUSES } from "@/panel-api/fees/types";
import { requireMe } from "@/panel-api/members/requireMe";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function TransactionsPage({searchParams}: { searchParams: SearchParams }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }
    const t = await getTranslations('fees');
    if (!managesFees(me)) {
        return forbidden(t('title'));
    }
    const params = await searchParams;
    const status = TRANSACTION_STATUSES.find(s => s === params['status']) ?? 'UNMATCHED';
    const page = Math.max(0, Number.parseInt(String(params['page'] ?? '0'), 10) || 0);
    const result = await listTransactions(status, page);

    return <Page title={t('title')} subtitle={t('transactionsSubtitle')}>
        <FeeTabs me={me} active="transactions"/>
        <Tabs label={t('transactionFilter')}
              items={TRANSACTION_STATUSES.map(s => ({
                  href: `/fees/transactions?status=${s}`, label: t(`transactionStatus.${s}`), active: s === status,
              }))}/>
        {!result.ok ? <ApiErrorNotice error={result.error}/> : <>
            {result.data.items.length === 0 && <p><Muted>{t('noTransactions')}</Muted></p>}
            {result.data.items.map(tx => <Card key={tx.id} id={`transaction-${tx.id}`}>
                <DescriptionList>
                    <dt>{t('bookingDate')}</dt>
                    <dd><FormattedDate value={tx.bookingDate}/></dd>
                    <dt>{t('amount')}</dt>
                    <dd>{tx.currency === 'PLN' ? <Money value={tx.amount}/> : `${tx.amount} ${tx.currency}`}</dd>
                    <dt>{t('counterparty')}</dt>
                    <dd>{tx.counterpartyName ?? '—'}{tx.counterpartyIban ? `, ${tx.counterpartyIban}` : ''}</dd>
                    <dt>{t('transferTitle')}</dt>
                    <dd>{tx.title || '—'}</dd>
                    {tx.matchMethod && <>
                        <dt>{t('matchedBy')}</dt>
                        <dd>{t(`matchMethod.${tx.matchMethod}`)}</dd>
                    </>}
                    {tx.ignoredNote && <>
                        <dt>{t('ignoreNote')}</dt>
                        <dd>{tx.ignoredNote}</dd>
                    </>}
                </DescriptionList>
                {tx.payments.length > 0 && <ul>
                    {tx.payments.map(p => <li key={p.paymentId}>
                        <Link href={`/fees/members/${p.member.id}`}>{p.member.displayName}</Link>:{' '}
                        <Money value={p.amount}/>{p.reversed ? ` (${t('reversed')})` : ''}
                    </li>)}
                </ul>}
                {tx.status === 'UNMATCHED' && <>
                    {tx.currency === 'PLN' && <AssignTransferForm transactionId={tx.id} amount={tx.amount}
                                                                  hasIban={!!tx.counterpartyIban}/>}
                    <IgnoreTransferForm transactionId={tx.id}/>
                </>}
                {(tx.status === 'MATCHED' || tx.status === 'IGNORED') &&
                    <ReopenTransferForm transactionId={tx.id} reasonRequired={tx.status === 'MATCHED'}/>}
            </Card>)}
            <Pagination page={result.data.page} totalPages={result.data.totalPages}
                        totalItems={result.data.totalItems} size={25} defaultSize={25} sizeOptions={[25]}
                        hrefFor={p => `/fees/transactions?status=${status}${p > 0 ? `&page=${p}` : ''}`}/>
        </>}
    </Page>;
}
