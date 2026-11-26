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

import { useTranslations } from "next-intl";
import { addRateAction, createTierAction, updateTierAction } from "@/app/actions/fees";
import ActionButton from "@/components/form/ActionButton";
import ActionForm from "@/components/form/ActionForm";
import { CheckboxField, TextField } from "@/components/form/Fields";

export function CreateTierForm() {
    const t = useTranslations('fees');
    return <ActionForm action={createTierAction} submitLabel={t('createTier')} successMessage={t('saved')}
                       resetOnSuccess ariaLabel={t('createTier')}>
        <TextField name="code" label={t('tierCode')} required maxLength={40}/>
        <TextField name="name" label={t('tierName')} required maxLength={120}/>
        <CheckboxField name="makeDefault" label={t('makeDefault')}/>
    </ActionForm>;
}

export function AddRateForm({tierId, currentMonth}: { tierId: number; currentMonth: string }) {
    const t = useTranslations('fees');
    return <ActionForm action={addRateAction} submitLabel={t('addRate')} successMessage={t('saved')}
                       resetOnSuccess ariaLabel={t('addRate')}>
        <input type="hidden" name="tierId" value={tierId}/>
        <TextField name="monthlyAmount" label={t('monthlyAmount')} required maxLength={12}/>
        <TextField name="validFrom" label={t('validFrom')} type="month" required defaultValue={currentMonth}
                   hint={t('validFromHint')}/>
        <TextField name="resolutionReference" label={t('resolution')} required maxLength={200}/>
    </ActionForm>;
}

export function TierButtons({tierId, isDefault, active}: { tierId: number; isDefault: boolean; active: boolean }) {
    const t = useTranslations('fees');
    if (isDefault) {
        return null;
    }
    return <>
        {active && <ActionButton action={() => updateTierAction(tierId, {makeDefault: true})}
                                 label={t('makeDefault')} confirmMessage={t('makeDefaultConfirm')}/>}
        <ActionButton action={() => updateTierAction(tierId, {active: !active})}
                      label={active ? t('deactivate') : t('activate')}/>
    </>;
}
