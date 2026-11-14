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

import React, { useEffect, useRef, useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import { assignMembershipAction, checkAssignmentAction } from "@/app/actions/bodies";
import { MemberPickerItem, searchMembersAction } from "@/app/actions/members";
import Button from "@/components/button/Button";
import { ErrorStatus } from "@/components/form/ActionForm";
import { SelectField, TextField } from "@/components/form/Fields";
import formStyles from "@/components/form/Form.module.scss";
import Dialog from "@/components/dialog/Dialog";
import { FormattedDate } from "@/components/members/format";
import { Notice } from "@/components/page/Page";
import { ActionResult, ApiError, AssignmentCheck, MemberType } from "@/panel-api/members/types";

type Input = { memberId: number; memberTypeId: number; validFrom?: string; validTo?: string };

export default function AssignFunctionForm({bodyId, memberTypes}: { bodyId: number; memberTypes: MemberType[] }) {
    const t = useTranslations('bodies');
    const tc = useTranslations('common');
    const te = useTranslations('errors');
    const formRef = useRef<HTMLFormElement>(null);
    const [member, setMember] = useState<MemberPickerItem | null>(null);
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<MemberPickerItem[]>([]);
    const [error, setError] = useState<ApiError | null>(null);
    const [violations, setViolations] = useState<AssignmentCheck['violations']>([]);
    const [confirm, setConfirm] = useState<{ check: AssignmentCheck; input: Input } | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [pending, startTransition] = useTransition();

    const searching = query.trim().length >= 2;

    useEffect(() => {
        if (!searching) {
            return;
        }
        let cancelled = false;
        const handle = setTimeout(async () => {
            const outcome = await searchMembersAction(query);
            if (!cancelled && outcome.ok) {
                setResults(outcome.data ?? []);
            }
        }, 300);
        return () => {
            cancelled = true;
            clearTimeout(handle);
        };
    }, [query, searching]);

    const reset = () => {
        formRef.current?.reset();
        setMember(null);
        setQuery('');
        setResults([]);
    };

    const handleAssignOutcome = (outcome: ActionResult<unknown>, input: Input) => {
        if (outcome.ok) {
            setConfirm(null);
            setMessage(t('assigned'));
            reset();
            return;
        }
        setConfirm(null);
        if (outcome.error.code === 'CONFIRMATION_REQUIRED' && outcome.error.check) {
            setConfirm({check: outcome.error.check, input});
            return;
        }
        if (outcome.error.check && outcome.error.check.violations.length > 0) {
            setViolations(outcome.error.check.violations);
            return;
        }
        setError(outcome.error);
    };

    const assign = (input: Input, confirmed: boolean) => {
        startTransition(async () => {
            handleAssignOutcome(await assignMembershipAction(bodyId, input, confirmed), input);
        });
    };

    const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setError(null);
        setViolations([]);
        setMessage(null);
        if (!member) {
            setError({status: 400, code: 'INVALID_VALUE', message: 'Member is required', fields: ['memberId']});
            return;
        }
        const formData = new FormData(event.currentTarget);
        const validFrom = String(formData.get('validFrom') ?? '');
        const validTo = String(formData.get('validTo') ?? '');
        const input: Input = {
            memberId: member.id,
            memberTypeId: Number(formData.get('memberTypeId')),
            ...(validFrom ? {validFrom} : {}),
            ...(validTo ? {validTo} : {}),
        };
        startTransition(async () => {
            const check = await checkAssignmentAction(bodyId, input);
            if (!check.ok) {
                setError(check.error);
                return;
            }
            const data = check.data;
            if (!data) {
                return;
            }
            if (data.violations.length > 0) {
                setViolations(data.violations);
            } else if (data.conflicts.length > 0 || data.warnings.length > 0) {
                setConfirm({check: data, input});
            } else {
                handleAssignOutcome(await assignMembershipAction(bodyId, input, false), input);
            }
        });
    };

    const defaultType = memberTypes.find(m => m.isDefault) ?? memberTypes[0];

    return <>
        <form ref={formRef} className={formStyles.form} onSubmit={onSubmit} aria-label={t('assignTitle')} aria-busy={pending}>
            <div className={formStyles.picker}>
                {member
                    ? <div className={formStyles.pickerSelected}>
                        <span>{t('selectedMember')}: <strong>{member.name}</strong></span>
                        <Button variant="outline" onClick={() => setMember(null)}>{t('changeMember')}</Button>
                    </div>
                    : <>
                        <div className={formStyles.field}>
                            <label className={formStyles.label} htmlFor={`member-search-${bodyId}`}>
                                {t('memberSearch')}<span className={formStyles.required} aria-hidden="true"> *</span>
                            </label>
                            <input id={`member-search-${bodyId}`}
                                   className={formStyles.input}
                                   type="search"
                                   value={query}
                                   maxLength={100}
                                   autoComplete="off"
                                   aria-describedby={`member-search-hint-${bodyId}`}
                                   onChange={event => setQuery(event.target.value)}/>
                            <span className={formStyles.hint} id={`member-search-hint-${bodyId}`}>{t('memberSearchHint')}</span>
                        </div>
                        {searching && <ul className={formStyles.pickerResults} aria-live="polite">
                            {results.length === 0 && <li>{t('noMemberResults')}</li>}
                            {results.map(item => <li key={item.id}>
                                <button type="button"
                                        className={formStyles.pickerOption}
                                        onClick={() => {
                                            setMember(item);
                                            setQuery('');
                                        }}>
                                    {item.name}{item.memberId ? ` (${item.memberId})` : ''}
                                </button>
                            </li>)}
                        </ul>}
                    </>}
            </div>
            <div className={formStyles.fieldGrid}>
                <SelectField name="memberTypeId"
                             label={t('role')}
                             required
                             defaultValue={defaultType ? String(defaultType.id) : ''}
                             options={memberTypes.map(m => ({value: String(m.id), label: m.name}))}/>
                <TextField name="validFrom" label={t('validFrom')} type="date" hint={t('validFromHint')}/>
                <TextField name="validTo" label={t('validTo')} type="date" hint={t('validToHint')}/>
            </div>
            <div aria-live="polite">
                {error && <ErrorStatus error={error}/>}
                {violations.length > 0 && <Notice tone="danger" role="alert">
                    <p>{t('violationsTitle')}</p>
                    <ul>{violations.map(v => <li key={v.code}>{te.has(v.code) ? te(v.code) : v.message}</li>)}</ul>
                </Notice>}
                {message && <Notice tone="success" role="status">{message}</Notice>}
            </div>
            <div className={formStyles.buttons}>
                <Button type="submit" disabled={pending || memberTypes.length === 0}>{t('assign')}</Button>
            </div>
        </form>

        <Dialog open={confirm !== null} title={t('confirmTitle')} onClose={() => setConfirm(null)}>
            {confirm && <>
                {confirm.check.conflicts.length > 0 && <div>
                    <h3>{t('conflictsTitle')}</h3>
                    <p>{t('conflictsIntro')}</p>
                    <ul>
                        {confirm.check.conflicts.map(c => <li key={c.membershipId}>
                            {t('conflictItem', {body: c.bodyName, role: c.memberTypeName})}{' '}
                            {t('conflictEnds')} <FormattedDate value={c.endsOn}/>.
                        </li>)}
                    </ul>
                </div>}
                {confirm.check.warnings.length > 0 && <div>
                    <h3>{t('warningsTitle')}</h3>
                    <ul>
                        {confirm.check.warnings.map((w, i) => <li key={`${w.code}-${i}`}>
                            {w.code === 'SINGLE_PERSON_EXEMPTION_LOST' && w.affectedMemberName && w.affectedBodyName
                                ? t('exemptionLost', {member: w.affectedMemberName, body: w.affectedBodyName})
                                : w.message}
                            {w.consecutiveTerms != null && w.maxConsecutiveTerms != null && <>
                                {' '}({t('termCount', {count: w.consecutiveTerms, max: w.maxConsecutiveTerms})})
                            </>}
                        </li>)}
                    </ul>
                </div>}
                <div className={formStyles.buttons}>
                    <Button variant="primary" disabled={pending} onClick={() => assign(confirm.input, true)}>
                        {t('confirmAssign')}
                    </Button>
                    <Button variant="outline" onClick={() => setConfirm(null)}>{tc('cancel')}</Button>
                </div>
            </>}
        </Dialog>
    </>;
}
