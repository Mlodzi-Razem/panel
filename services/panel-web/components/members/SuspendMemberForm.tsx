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

import React, { useRef, useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { checkSuspensionAction, createSuspensionAction } from "@/app/actions/suspensions";
import Button from "@/components/button/Button";
import Dialog from "@/components/dialog/Dialog";
import { ErrorStatus } from "@/components/form/ActionForm";
import { SelectField, TextAreaField, TextField } from "@/components/form/Fields";
import formStyles from "@/components/form/Form.module.scss";
import { Notice } from "@/components/page/Page";
import { SuspensionOptions } from "@/panel-api/members/suspensions";
import { ApiError, SuspensionCheck } from "@/panel-api/members/types";

type Warning = SuspensionCheck['warnings'][number];

const KINDS = ['ARREARS', 'VOLUNTARY', 'BY_ORGAN'] as const;

function kindOf(formData: FormData): string {
    return String(formData.get('kind') ?? '');
}
const SCOPES = ['FULL', 'PASSIVE_ELECTORAL', 'PARTIAL_OTHER'] as const;

type Input = {
    kind: string;
    scope: string;
    scopeDescription?: string;
    suspendingBodyId: number;
    start?: string;
    end?: string;
    decisionDate?: string;
    decisionSignature: string;
};

/** Records a suspension: a dry run first, then the entry; warnings need an explicit confirmation (art. 10). */
export default function SuspendMemberForm({memberId, options}: { memberId: number; options: SuspensionOptions }) {
    const t = useTranslations('suspensions');
    const tEnums = useTranslations('enums');
    const formRef = useRef<HTMLFormElement>(null);
    const [kind, setKind] = useState('');
    const [scope, setScope] = useState('');
    const [error, setError] = useState<ApiError | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [pending, startTransition] = useTransition();
    const [input, setInput] = useState<Input | null>(null);
    const [warnings, setWarnings] = useState<Warning[]>([]);
    const [mandates, setMandates] = useState<SuspensionCheck['mandatesAtStake']>([]);
    const [acknowledged, setAcknowledged] = useState<string[]>([]);

    const bodies = kind ? options.bodies.filter(b => b.allowedFor.includes(kind)) : [];

    const readInput = (formData: FormData): Input => {
        const scopeValue = String(formData.get('scope') ?? '');
        const description = String(formData.get('scopeDescription') ?? '').trim();
        const start = String(formData.get('start') ?? '');
        const end = String(formData.get('end') ?? '');
        const decisionDate = String(formData.get('decisionDate') ?? '');
        return {
            kind: String(formData.get('kind') ?? ''),
            scope: scopeValue,
            suspendingBodyId: Number(formData.get('suspendingBodyId')),
            decisionSignature: String(formData.get('decisionSignature') ?? '').trim(),
            ...(scopeValue === 'PARTIAL_OTHER' && description ? {scopeDescription: description} : {}),
            ...(start ? {start} : {}),
            ...(end ? {end} : {}),
            // data uchwały (W4): a resolution exists for ARREARS and BY_ORGAN only
            ...(decisionDate && kindOf(formData) !== 'VOLUNTARY' ? {decisionDate} : {}),
        };
    };

    const save = async (data: Input, confirmed: string[]) => {
        const result = await createSuspensionAction(memberId, {...data, confirmedWarnings: confirmed});
        if (result.ok) {
            setInput(null);
            setWarnings([]);
            setMessage(t('saved'));
            setKind('');
            setScope('');
            formRef.current?.reset();
            return;
        }
        const check = result.error.check;
        if (result.error.code === 'CONFIRMATION_REQUIRED' && check && 'mandatesAtStake' in check) {
            setInput(data);
            setWarnings(check.warnings);
            setMandates(check.mandatesAtStake);
            setAcknowledged(check.warnings.filter(w => w.confirmed).map(w => w.code));
            return;
        }
        setInput(null);
        setError(result.error);
    };

    const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setError(null);
        setMessage(null);
        const data = readInput(new FormData(event.currentTarget));
        startTransition(async () => {
            const outcome = await checkSuspensionAction(memberId, data);
            if (!outcome.ok) {
                setError(outcome.error);
                return;
            }
            const verdict = outcome.data;
            if (!verdict) {
                return;
            }
            if (verdict.violations.length > 0) {
                const first = verdict.violations[0];
                setError({status: 422, code: first.code, message: first.message});
                return;
            }
            if (verdict.warnings.length > 0) {
                setInput(data);
                setWarnings(verdict.warnings);
                setMandates(verdict.mandatesAtStake);
                setAcknowledged([]);
                return;
            }
            await save(data, []);
        });
    };

    const confirmSave = () => {
        if (!input) {
            return;
        }
        const data = input;
        startTransition(async () => {
            await save(data, acknowledged);
        });
    };

    const toggle = (code: string, checked: boolean) => setAcknowledged(current => checked
        ? [...current, code]
        : current.filter(c => c !== code));

    const allAcknowledged = warnings.every(w => acknowledged.includes(w.code));

    return <>
        <form ref={formRef}
              className={formStyles.form}
              onSubmit={onSubmit}
              aria-label={t('add')}
              aria-busy={pending}>
            <SelectField name="kind" label={t('fieldKind')} required
                         options={KINDS.map(k => ({value: k, label: tEnums(`suspensionKind.${k}`)}))}
                         emptyLabel=""
                         onChange={setKind}/>
            <SelectField name="scope" label={t('fieldScope')} required
                         options={SCOPES.map(s => ({value: s, label: tEnums(`suspensionScope.${s}`)}))}
                         emptyLabel=""
                         onChange={setScope}/>
            {scope === 'PARTIAL_OTHER' &&
                <TextAreaField name="scopeDescription" label={t('fieldScopeDescription')} required maxLength={500}/>}
            <SelectField name="suspendingBodyId" label={t('fieldBody')} required
                         options={bodies.map(b => ({value: String(b.id), label: b.name}))}
                         emptyLabel=""
                         hint={kind ? undefined : t('pickKindFirst')}/>
            {kind !== '' && kind !== 'VOLUNTARY' && <TextField name="decisionDate" label={t('fieldDecisionDate')}
                                                              type="date" required hint={t('hintDecisionDate')}/>}
            <TextField name="start" label={t('fieldStart')} type="date"
                       hint={kind === 'VOLUNTARY' ? t('hintStartVoluntary') : t('hintStart')}/>
            <TextField name="end" label={t('fieldEnd')} type="date" hint={t('hintEnd')}/>
            <TextField name="decisionSignature" label={t('fieldSignature')} required maxLength={200}/>
            {error && <ErrorStatus error={error}/>}
            {message && <Notice tone="success">{message}</Notice>}
            <div className={formStyles.buttons}>
                <Button type="submit" disabled={pending}>{t('add')}</Button>
            </div>
        </form>

        <Dialog open={input !== null} title={t('confirmTitle')} onClose={() => setInput(null)}>
            <div className={formStyles.form}>
                <p>{t('confirmIntro')}</p>
                <ul>
                    {warnings.map(w => <li key={w.code}>
                        <label>
                            <input type="checkbox"
                                   checked={acknowledged.includes(w.code)}
                                   onChange={e => toggle(w.code, e.target.checked)}/>{' '}
                            {t.has(`warnings.${w.code}`) ? t(`warnings.${w.code}`) : w.message}
                        </label>
                    </li>)}
                </ul>
                {mandates.length > 0 && <>
                    <p>{t('mandatesAtStake')}</p>
                    <ul>{mandates.map(m => <li key={m.membershipId}>{m.bodyName} — {m.memberTypeName}</li>)}</ul>
                </>}
                <div className={formStyles.buttons}>
                    <Button onClick={confirmSave} disabled={pending || !allAcknowledged}>{t('confirmSave')}</Button>
                    <Button variant="outline" onClick={() => setInput(null)}>{t('cancel')}</Button>
                </div>
            </div>
        </Dialog>
    </>;
}
