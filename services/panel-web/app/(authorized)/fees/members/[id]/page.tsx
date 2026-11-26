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

import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import {
    AssignTierForm,
    ConfirmIbanForm,
    ForgetIbanButton,
    GrantAdjustmentForm,
    RecordPaymentForm,
    ReversePayment,
    RevokeAdjustmentButton,
} from "@/components/fees/CardForms";
import { managesFees, readsFees } from "@/components/fees/FeeTabs";
import { AdjustmentsTable, BalanceSummary, DuesTable, PaymentsTable } from "@/components/fees/FeeTables";
import { Month } from "@/components/fees/format";
import { forbidden } from "@/components/fees/guard";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate, memberName } from "@/components/members/format";
import { Card, Muted, Notice, Page } from "@/components/page/Page";
import { getFeeCard, listIbans, listTiers } from "@/panel-api/fees/fees";
import { getMember } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";

export default async function FeeCardPage({params}: { params: Promise<{ id: string }> }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }
    const t = await getTranslations('fees');
    const {id} = await params;
    const memberId = /^\d+$/.test(id) ? Number(id) : 0;
    if (memberId <= 0) {
        notFound();
    }
    if (!readsFees(me)) {
        return forbidden(t('title'));
    }
    const card = await getFeeCard(memberId);
    if (!card.ok) {
        return <Page title={t('feeCard')} backHref="/fees/finance" backLabel={t('tabs.finance')}>
            <ApiErrorNotice error={card.error}/>
        </Page>;
    }

    // a decision about one's own fees belongs to another fee manager
    const manager = managesFees(me);
    const own = me.member.id === memberId;
    const canEdit = manager && !own;
    const [member, tiers, ibans] = await Promise.all([
        // reading the member record needs its own permission; without it the card shows the reference only
        me.canSeeMembers ? getMember(memberId) : Promise.resolve(null),
        listTiers(),
        manager ? listIbans(memberId) : Promise.resolve(null),
    ]);
    const name = member?.ok ? memberName(member.data.fields) : card.data.reference;
    const tierNames = new Map(tiers.ok ? tiers.data.map(tier => [tier.id, tier.name]) : []);
    const today = new Date().toISOString().slice(0, 10);
    const currentMonth = today.slice(0, 7);
    const data = card.data;

    return <Page title={`${t('feeCard')}: ${name}`} subtitle={`${t('reference')}: ${data.reference}`}
                 backHref="/fees/finance" backLabel={t('tabs.finance')}>
        {data.arrears.suspendsElectoralRights && <Notice tone="warning">{t('noRightsNotice')}</Notice>}
        {manager && own && <Notice tone="info">{t('ownCard')}</Notice>}
        <Card title={t('balanceTitle')} id="balance">
            <BalanceSummary balance={data.balance}/>
            {data.arrears.since && <p>{t('arrearsSince')} <FormattedDate value={data.arrears.since}/></p>}
        </Card>
        <Card title={t('payments')} id="payments">
            <PaymentsTable payments={data.payments}
                           rowAction={manager ? payment => !payment.reversed && (payment.bankTransactionId
                               ? <Muted>{t('reverseThroughTransfer')}</Muted>
                               : <ReversePayment paymentId={payment.id}/>) : undefined}/>
            {canEdit && <RecordPaymentForm memberId={memberId} today={today}/>}
        </Card>
        <Card title={t('adjustments')} id="adjustments"
              hint={data.reasonsIncluded ? undefined : t('reasonsHidden')}>
            <AdjustmentsTable adjustments={data.adjustments} reasonsIncluded={data.reasonsIncluded}
                              rowAction={manager ? a => <RevokeAdjustmentButton adjustmentId={a.id}/> : undefined}/>
            {canEdit && <GrantAdjustmentForm memberId={memberId} currentMonth={currentMonth}/>}
        </Card>
        <Card title={t('tierAssignments')} id="tiers">
            {data.tierAssignments.length === 0
                ? <p><Muted>{t('defaultTierApplies')}</Muted></p>
                : <ul>{data.tierAssignments.map(a => <li key={a.id}>
                    {tierNames.get(a.tierId) ?? a.tierId}: {t('from')} <Month value={a.validFrom}/>
                </li>)}</ul>}
            {canEdit && tiers.ok && <AssignTierForm memberId={memberId} currentMonth={currentMonth}
                                                    tiers={tiers.data.filter(tier => tier.active)}/>}
        </Card>
        {manager && <Card title={t('ibans')} hint={t('ibansHint')} id="ibans">
            {ibans && !ibans.ok && <ApiErrorNotice error={ibans.error}/>}
            {ibans?.ok && (ibans.data.length === 0
                ? <p><Muted>{t('noIbans')}</Muted></p>
                : <ul>{ibans.data.map(i => <li key={i.id}>{i.iban} <ForgetIbanButton iban={i.iban}/></li>)}</ul>)}
            {canEdit && <ConfirmIbanForm memberId={memberId}/>}
        </Card>}
        <Card title={t('allDues')} id="all-dues">
            <DuesTable dues={data.dues}/>
        </Card>
    </Page>;
}
