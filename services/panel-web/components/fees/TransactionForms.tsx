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

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { assignTransactionAction, ignoreTransactionAction, reopenTransactionAction } from "@/app/actions/fees";
import Button from "@/components/button/Button";
import ActionForm, { ErrorStatus } from "@/components/form/ActionForm";
import { TextField } from "@/components/form/Fields";
import formStyles from "@/components/form/Form.module.scss";
import MemberPicker, { PickedMember } from "@/components/fees/MemberPicker";
import { ApiError } from "@/panel-api/members/types";

type Split = { member: PickedMember; amount: string };

/** Matches one transfer to one or more members; the API checks that the parts add up. */
export function AssignTransferForm({transactionId, amount, hasIban}: {
    transactionId: number;
    amount: string;
    hasIban: boolean;
}) {
    const t = useTranslations('fees');
    const router = useRouter();
    const [splits, setSplits] = useState<Split[]>([]);
    const [remember, setRemember] = useState(false);
    const [error, setError] = useState<ApiError | null>(null);
    const [pending, startTransition] = useTransition();

    const add = (member: PickedMember) => setSplits(current =>
        current.some(s => s.member.id === member.id)
            ? current
            : [...current, {member, amount: current.length === 0 ? amount : ''}]);

    const submit = () => startTransition(async () => {
        const outcome = await assignTransactionAction(transactionId,
            splits.map(s => ({memberId: s.member.id, amount: s.amount})), remember && splits.length === 1);
        if (outcome.ok) {
            router.refresh();
        } else {
            setError(outcome.error);
        }
    });

    return <div className={formStyles.form} aria-busy={pending}>
        <MemberPicker label={t('assignTo')} onPick={add}/>
        {splits.map((split, index) => <div key={split.member.id} className={formStyles.field}>
            <label className={formStyles.label} htmlFor={`split-${transactionId}-${split.member.id}`}>
                {split.member.name}{split.member.memberId ? ` (${split.member.memberId})` : ''}
            </label>
            <input id={`split-${transactionId}-${split.member.id}`} className={formStyles.input}
                   inputMode="decimal" value={split.amount}
                   onChange={e => setSplits(current => current.map((s, i) =>
                       i === index ? {...s, amount: e.target.value} : s))}/>
            <Button variant="outline" ariaLabel={`${t('removeSplit')}: ${split.member.name}`}
                    onClick={() => setSplits(current => current.filter((_, i) => i !== index))}>
                {t('removeSplit')}
            </Button>
        </div>)}
        {splits.length === 1 && hasIban && <label className={formStyles.checkbox}>
            <input type="checkbox" checked={remember} onChange={e => setRemember(e.target.checked)}/>
            {t('rememberIban')}
        </label>}
        {error && <ErrorStatus error={error}/>}
        <div className={formStyles.buttons}>
            <Button onClick={submit} disabled={splits.length === 0 || pending}>{t('assign')}</Button>
        </div>
    </div>;
}

export function IgnoreTransferForm({transactionId}: { transactionId: number }) {
    const t = useTranslations('fees');
    return <ActionForm action={ignoreTransactionAction} submitLabel={t('notAFee')} submitVariant="outline">
        <input type="hidden" name="transactionId" value={transactionId}/>
        <TextField name="note" label={t('ignoreNote')} maxLength={500}/>
    </ActionForm>;
}

export function ReopenTransferForm({transactionId, reasonRequired}: {
    transactionId: number;
    reasonRequired: boolean;
}) {
    const t = useTranslations('fees');
    return <ActionForm action={reopenTransactionAction} submitLabel={t('reopen')} submitVariant="outline">
        <input type="hidden" name="transactionId" value={transactionId}/>
        {reasonRequired && <TextField name="reason" label={t('reversalReason')} required maxLength={500}/>}
    </ActionForm>;
}
