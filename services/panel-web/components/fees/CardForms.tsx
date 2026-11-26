"use client";

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

import { useState } from "react";
import { useTranslations } from "next-intl";
import {
    assignTierAction,
    confirmIbanAction,
    forgetIbanAction,
    grantAdjustmentAction,
    recordPaymentAction,
    reversePaymentAction,
    revokeAdjustmentAction,
} from "@/app/actions/fees";
import Button from "@/components/button/Button";
import ActionButton from "@/components/form/ActionButton";
import ActionForm from "@/components/form/ActionForm";
import { SelectField, TextAreaField, TextField } from "@/components/form/Fields";
import { ADJUSTMENT_KINDS, ADJUSTMENT_REASONS } from "@/panel-api/fees/types";

export function RecordPaymentForm({memberId, today}: { memberId: number; today: string }) {
    const t = useTranslations('fees');
    return <ActionForm action={recordPaymentAction} submitLabel={t('recordPayment')} successMessage={t('saved')}
                       resetOnSuccess ariaLabel={t('recordPayment')}>
        <input type="hidden" name="memberId" value={memberId}/>
        <TextField name="amount" label={t('amount')} required maxLength={12}/>
        <TextField name="paidOn" label={t('paidOn')} type="date" required defaultValue={today}/>
        <SelectField name="source" label={t('source')} required defaultValue="CASH"
                     options={(['CASH', 'OTHER'] as const).map(v => ({value: v, label: t(`paymentSource.${v}`)}))}/>
        <TextField name="note" label={t('note')} maxLength={500} hint={t('noteHint')}/>
    </ActionForm>;
}

export function GrantAdjustmentForm({memberId, currentMonth}: { memberId: number; currentMonth: string }) {
    const t = useTranslations('fees');
    const [kind, setKind] = useState('EXEMPTION');
    return <ActionForm action={grantAdjustmentAction} submitLabel={t('grantAdjustment')} successMessage={t('saved')}
                       ariaLabel={t('grantAdjustment')}>
        <input type="hidden" name="memberId" value={memberId}/>
        <SelectField name="kind" label={t('kind')} required defaultValue="EXEMPTION" onChange={setKind}
                     options={ADJUSTMENT_KINDS.map(v => ({value: v, label: t(`adjustmentKind.${v}`)}))}/>
        <TextField name="from" label={t('fromMonth')} type="month" required defaultValue={currentMonth}
                   hint={t('retroactiveHint')}/>
        <TextField name="to" label={t('toMonth')} type="month" hint={t('toMonthHint')}/>
        {kind === 'DISCOUNT' && <TextField name="discountAmount" label={t('discountAmount')} required maxLength={12}/>}
        <SelectField name="reasonCategory" label={t('reasonCategoryLabel')} required emptyLabel={t('choose')}
                     options={ADJUSTMENT_REASONS.map(v => ({value: v, label: t(`reasonCategory.${v}`)}))}/>
        <TextAreaField name="justification" label={t('justification')} required maxLength={2000}
                       hint={t('confidentialHint')}/>
        <TextField name="resolutionReference" label={t('resolutionOptional')} maxLength={200}/>
    </ActionForm>;
}

export function RevokeAdjustmentButton({adjustmentId}: { adjustmentId: number }) {
    const t = useTranslations('fees');
    return <ActionButton action={() => revokeAdjustmentAction(adjustmentId)} label={t('revoke')}
                         confirmMessage={t('revokeConfirm')}/>;
}

export function ReversePayment({paymentId}: { paymentId: number }) {
    const t = useTranslations('fees');
    const [open, setOpen] = useState(false);
    if (!open) {
        return <Button variant="outline" onClick={() => setOpen(true)}>{t('reverse')}</Button>;
    }
    return <ActionForm action={reversePaymentAction} submitLabel={t('reverse')}
                       extraButtons={<Button variant="outline" onClick={() => setOpen(false)}>{t('cancel')}</Button>}>
        <input type="hidden" name="paymentId" value={paymentId}/>
        <TextField name="reason" label={t('reversalReason')} required maxLength={500}/>
    </ActionForm>;
}

export function AssignTierForm({memberId, tiers, currentMonth}: {
    memberId: number;
    tiers: { id: number; name: string }[];
    currentMonth: string;
}) {
    const t = useTranslations('fees');
    return <ActionForm action={assignTierAction} submitLabel={t('assignTier')} successMessage={t('saved')}
                       ariaLabel={t('assignTier')}>
        <input type="hidden" name="memberId" value={memberId}/>
        <SelectField name="tierId" label={t('tier')} required emptyLabel={t('choose')}
                     options={tiers.map(tier => ({value: String(tier.id), label: tier.name}))}/>
        <TextField name="validFrom" label={t('validFrom')} type="month" required defaultValue={currentMonth}
                   hint={t('validFromHint')}/>
    </ActionForm>;
}

export function ConfirmIbanForm({memberId}: { memberId: number }) {
    const t = useTranslations('fees');
    return <ActionForm action={confirmIbanAction} submitLabel={t('confirmIban')} successMessage={t('saved')}
                       resetOnSuccess ariaLabel={t('confirmIban')}>
        <input type="hidden" name="memberId" value={memberId}/>
        <TextField name="iban" label={t('iban')} required maxLength={64}/>
    </ActionForm>;
}

export function ForgetIbanButton({iban}: { iban: string }) {
    const t = useTranslations('fees');
    return <ActionButton action={() => forgetIbanAction(iban)} label={t('forgetIban')}
                         confirmMessage={t('forgetIbanConfirm')}/>;
}
