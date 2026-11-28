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

"use client";

import React, { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import {
    appealEndedAction,
    endSuspendedMandatesAction,
    liftSuspensionAction,
    voidSuspensionAction,
} from "@/app/actions/suspensions";
import Button from "@/components/button/Button";
import { ErrorStatus } from "@/components/form/ActionForm";
import { SelectField, TextAreaField, TextField } from "@/components/form/Fields";
import formStyles from "@/components/form/Form.module.scss";
import { Notice } from "@/components/page/Page";
import { ActionResult, ApiError, MandateRef } from "@/panel-api/members/types";
import type { SuspensionOptions } from "@/panel-api/members/suspensions";

/** Shared submit state of the small per-entry forms. */
function useOutcome<T>() {
    const [error, setError] = useState<ApiError | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [pending, startTransition] = useTransition();
    const run = (action: () => Promise<ActionResult<T>>, done: string) => {
        setError(null);
        setMessage(null);
        startTransition(async () => {
            const outcome = await action();
            if (outcome.ok) {
                setMessage(done);
                return;
            }
            setError(outcome.error);
        });
    };
    return {error, message, pending, run};
}

export function LiftSuspensionForm({memberId, suspensionId, bodies, mandatesNote}: {
    memberId: number;
    suspensionId: number;
    bodies: SuspensionOptions['bodies'];
    mandatesNote: string;
}) {
    const t = useTranslations('suspensions');
    const {error, message, pending, run} = useOutcome<unknown>();

    const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const liftedOn = String(data.get('liftedOn') ?? '');
        run(() => liftSuspensionAction(memberId, suspensionId, {
            ...(liftedOn ? {liftedOn} : {}),
            reason: String(data.get('reason') ?? '').trim(),
            liftingBodyId: Number(data.get('liftingBodyId')),
            decisionSignature: String(data.get('decisionSignature') ?? '').trim(),
        }), t('lifted'));
    };

    return <form className={formStyles.form} onSubmit={onSubmit} aria-label={t('lift')}>
        <Notice tone="info">{t('liftNote')} {mandatesNote}</Notice>
        <TextField name="liftedOn" label={t('fieldLiftedOn')} type="date" hint={t('hintLiftedOn')}/>
        <TextAreaField name="reason" label={t('fieldReason')} required maxLength={500}/>
        <SelectField name="liftingBodyId" label={t('fieldBody')} required emptyLabel=""
                     options={bodies.map(b => ({value: String(b.id), label: b.name}))}/>
        <TextField name="decisionSignature" label={t('fieldSignature')} required maxLength={200}/>
        {error && <ErrorStatus error={error}/>}
        {message && <Notice tone="success">{message}</Notice>}
        <div className={formStyles.buttons}>
            <Button type="submit" variant="danger" disabled={pending}>{t('lift')}</Button>
        </div>
    </form>;
}

export function AppealEndedForm({memberId, suspensionId, suggestedOn, hintDays}: {
    memberId: number;
    suspensionId: number;
    suggestedOn: string;
    hintDays: number;
}) {
    const t = useTranslations('suspensions');
    const {error, message, pending, run} = useOutcome<unknown>();

    const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        run(() => appealEndedAction(memberId, suspensionId, {
            endedOn: String(data.get('endedOn') ?? ''),
            decisionReference: String(data.get('decisionReference') ?? '').trim(),
        }), t('appealRecorded'));
    };

    return <form className={formStyles.form} onSubmit={onSubmit} aria-label={t('appealEnded')}>
        <TextField name="endedOn" label={t('fieldEndedOn')} type="date" required defaultValue={suggestedOn}
                   hint={t('appealHint', {days: hintDays, date: suggestedOn})}/>
        <TextField name="decisionReference" label={t('fieldReference')} required maxLength={200}/>
        {error && <ErrorStatus error={error}/>}
        {message && <Notice tone="success">{message}</Notice>}
        <div className={formStyles.buttons}>
            <Button type="submit" disabled={pending}>{t('appealEnded')}</Button>
        </div>
    </form>;
}

export function VoidSuspensionForm({memberId, suspensionId}: { memberId: number; suspensionId: number }) {
    const t = useTranslations('suspensions');
    const {error, message, pending, run} = useOutcome<unknown>();

    const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const reference = String(data.get('decisionReference') ?? '').trim();
        run(() => voidSuspensionAction(memberId, suspensionId, {
            reason: String(data.get('reason') ?? '').trim(),
            ...(reference ? {decisionReference: reference} : {}),
        }), t('voided'));
    };

    return <form className={formStyles.form} onSubmit={onSubmit} aria-label={t('void')}>
        <Notice tone="warning">{t('voidNote')}</Notice>
        <TextAreaField name="reason" label={t('fieldVoidReason')} required maxLength={500}/>
        <TextField name="decisionReference" label={t('fieldVoidReference')} maxLength={200}/>
        {error && <ErrorStatus error={error}/>}
        {message && <Notice tone="success">{message}</Notice>}
        <div className={formStyles.buttons}>
            <Button type="submit" variant="danger" disabled={pending}>{t('void')}</Button>
        </div>
    </form>;
}

export function EndSuspendedMandatesForm({memberId, suspensionId, outstanding, conflicting}: {
    memberId: number;
    suspensionId: number;
    outstanding: MandateRef[];
    conflicting: MandateRef[];
}) {
    const t = useTranslations('suspensions');
    const {error, message, pending, run} = useOutcome<unknown>();

    const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        run(() => endSuspendedMandatesAction(memberId, suspensionId, {
            membershipIds: data.getAll('membershipIds').map(v => Number(v)),
            decisionReference: String(data.get('decisionReference') ?? '').trim(),
        }), t('mandatesEnded'));
    };

    return <form className={formStyles.form} onSubmit={onSubmit} aria-label={t('endMandates')}>
        <fieldset>
            <legend>{t('positionLossTitle')}</legend>
            {outstanding.map(m => <label key={m.membershipId}>
                <input type="checkbox" name="membershipIds" value={m.membershipId} defaultChecked/>{' '}
                {m.bodyName} — {m.memberTypeName}
            </label>)}
        </fieldset>
        {conflicting.length > 0 && <Notice tone="warning">
            {t('conflicting')}: {conflicting.map(m => `${m.bodyName} — ${m.memberTypeName}`).join('; ')}
        </Notice>}
        <TextField name="decisionReference" label={t('endMandatesReference')} required maxLength={200}/>
        {error && <ErrorStatus error={error}/>}
        {message && <Notice tone="success">{message}</Notice>}
        <div className={formStyles.buttons}>
            <Button type="submit" variant="danger" disabled={pending || outstanding.length === 0}>
                {t('endMandates')}
            </Button>
        </div>
    </form>;
}
