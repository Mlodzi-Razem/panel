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
import { useTranslations } from "next-intl";
import { Money, Month, Period } from "@/components/fees/format";
import { FormattedDate } from "@/components/members/format";
import { Badge, Muted, TableWrap } from "@/components/page/Page";
import { Stats } from "@/components/ui/Blocks";
import { Adjustment, Due, FeePayment } from "@/panel-api/fees/types";

export function BalanceSummary({balance}: { balance: { owed: string; overdue: string; credit: string } }) {
    const t = useTranslations('fees');
    return <Stats>
        {(['owed', 'overdue', 'credit'] as const).map(key => <div key={key}>
            <div><Muted>{t(`balance.${key}`)}</Muted></div>
            <strong><Money value={balance[key]}/></strong>
        </div>)}
    </Stats>;
}

export function DuesTable({dues}: { dues: Due[] }) {
    const t = useTranslations('fees');
    if (dues.length === 0) {
        return <p><Muted>{t('noDues')}</Muted></p>;
    }
    return <TableWrap>
        <thead>
            <tr>
                <th scope="col">{t('month')}</th>
                <th scope="col">{t('amount')}</th>
                <th scope="col">{t('paid')}</th>
                <th scope="col">{t('outstanding')}</th>
                <th scope="col">{t('status')}</th>
            </tr>
        </thead>
        <tbody>
            {[...dues].reverse().map(due => <tr key={due.id}>
                <td><Month value={due.month}/></td>
                <td><Money value={due.amount}/></td>
                <td><Money value={due.paid}/></td>
                <td><Money value={due.outstanding}/></td>
                <td>
                    <Badge tone={due.status === 'PAID' ? 'success' : due.status === 'OPEN' ? 'warning' : 'muted'}>
                        {t(`dueStatus.${due.status}`)}
                    </Badge>
                </td>
            </tr>)}
        </tbody>
    </TableWrap>;
}

export function PaymentsTable({payments, rowAction}: {
    payments: FeePayment[];
    rowAction?: (payment: FeePayment) => React.ReactNode;
}) {
    const t = useTranslations('fees');
    if (payments.length === 0) {
        return <p><Muted>{t('noPayments')}</Muted></p>;
    }
    return <TableWrap>
        <thead>
            <tr>
                <th scope="col">{t('paidOn')}</th>
                <th scope="col">{t('amount')}</th>
                <th scope="col">{t('source')}</th>
                <th scope="col">{t('status')}</th>
                {rowAction && <th scope="col">{t('actions')}</th>}
            </tr>
        </thead>
        <tbody>
            {payments.map(payment => <tr key={payment.id}>
                <td><FormattedDate value={payment.paidOn}/></td>
                <td><Money value={payment.amount}/></td>
                <td>
                    {t(`paymentSource.${payment.source}`)}
                    {payment.note && <div><Muted>{payment.note}</Muted></div>}
                </td>
                <td>
                    <Badge tone={payment.reversed ? 'muted' : 'success'}>
                        {payment.reversed ? t('reversed') : t('booked')}
                    </Badge>
                    {payment.reversalReason && <div><Muted>{payment.reversalReason}</Muted></div>}
                </td>
                {rowAction && <td>{rowAction(payment)}</td>}
            </tr>)}
        </tbody>
    </TableWrap>;
}

export function AdjustmentsTable({adjustments, reasonsIncluded, rowAction}: {
    adjustments: Adjustment[];
    reasonsIncluded: boolean;
    rowAction?: (adjustment: Adjustment) => React.ReactNode;
}) {
    const t = useTranslations('fees');
    if (adjustments.length === 0) {
        return <p><Muted>{t('noAdjustments')}</Muted></p>;
    }
    return <TableWrap>
        <thead>
            <tr>
                <th scope="col">{t('kind')}</th>
                <th scope="col">{t('period')}</th>
                <th scope="col">{t('resolution')}</th>
                {reasonsIncluded && <th scope="col">{t('reason')}</th>}
                {rowAction && <th scope="col">{t('actions')}</th>}
            </tr>
        </thead>
        <tbody>
            {adjustments.map(a => <tr key={a.id}>
                <td>
                    {t(`adjustmentKind.${a.kind}`)}
                    {a.discountAmount && <> (<Money value={a.discountAmount}/>)</>}
                </td>
                <td>
                    <Period from={a.from} to={a.to}/>
                    {a.revokedFrom && <div><Muted>{t('revokedFrom')} <Month value={a.revokedFrom}/></Muted></div>}
                </td>
                <td>{a.resolutionReference ?? '—'}</td>
                {reasonsIncluded && <td>
                    {a.reasonCategory && t(`reasonCategory.${a.reasonCategory}`)}
                    {a.justification && <div><Muted>{a.justification}</Muted></div>}
                </td>}
                {rowAction && <td>{!a.revoked && rowAction(a)}</td>}
            </tr>)}
        </tbody>
    </TableWrap>;
}
