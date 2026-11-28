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

import { useMemberTypeName } from "@/components/bodies/MemberTypeName";
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

type Input = {
    memberId: number;
    memberTypeId: number;
    validFrom?: string;
    validTo?: string;
    decisionReference?: string;
};

export default function AssignFunctionForm({bodyId, bodyKind, memberTypes}: {
    bodyId: number;
    bodyKind: string | null | undefined;
    memberTypes: MemberType[];
}) {
    const memberTypeName = useMemberTypeName();
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
    const [acknowledged, setAcknowledged] = useState<string[]>([]);
    const [message, setMessage] = useState<string | null>(null);
    const [pending, startTransition] = useTransition();
    // bumped on every edit: an answer to a check of what the form no longer says is dropped
    const revision = useRef(0);

    const clearOutcome = () => {
        revision.current += 1;
        setError(null);
        setViolations([]);
        setMessage(null);
        setConfirm(null);
    };

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

    // every warning code is ticked on its own; codes confirmed in an earlier round stay ticked
    const askToConfirm = (check: AssignmentCheck, input: Input) => {
        setAcknowledged(check.warnings.filter(w => w.confirmed).map(w => w.code));
        setConfirm({check, input});
    };

    const handleAssignOutcome = (outcome: ActionResult<unknown>, input: Input) => {
        if (outcome.ok) {
            setConfirm(null);
            setMessage(t('assigned'));
            reset();
            return;
        }
        setConfirm(null);
        if (outcome.error.code === 'CONFIRMATION_REQUIRED' && outcome.error.check && 'conflicts' in outcome.error.check) {
            askToConfirm(outcome.error.check, input);
            return;
        }
        if (outcome.error.check && outcome.error.check.violations.length > 0) {
            setViolations(outcome.error.check.violations);
            return;
        }
        setError(outcome.error);
    };

    const assign = (input: Input, confirmedWarnings: string[]) => {
        startTransition(async () => {
            handleAssignOutcome(await assignMembershipAction(bodyId, {...input, confirmedWarnings}), input);
        });
    };

    const toggle = (code: string, checked: boolean) => setAcknowledged(current => checked
        ? [...current, code]
        : current.filter(c => c !== code));

    const warningText = (warning: AssignmentCheck['warnings'][number]) => {
        if (warning.code === 'SINGLE_PERSON_EXEMPTION_LOST' && warning.affectedMemberName && warning.affectedBodyName) {
            return t('exemptionLost', {member: warning.affectedMemberName, body: warning.affectedBodyName});
        }
        return t.has(`warnings.${warning.code}`) ? t(`warnings.${warning.code}`) : warning.message;
    };

    const allAcknowledged = confirm !== null && confirm.check.warnings.every(w => acknowledged.includes(w.code));

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
        const decisionReference = String(formData.get('decisionReference') ?? '').trim();
        const input: Input = {
            memberId: member.id,
            memberTypeId: Number(formData.get('memberTypeId')),
            ...(validFrom ? {validFrom} : {}),
            ...(validTo ? {validTo} : {}),
            ...(decisionReference ? {decisionReference} : {}),
        };
        const checked = revision.current;
        startTransition(async () => {
            const check = await checkAssignmentAction(bodyId, input);
            if (checked !== revision.current) {
                return;
            }
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
                askToConfirm(data, input);
            } else {
                handleAssignOutcome(await assignMembershipAction(bodyId, input), input);
            }
        });
    };

    const defaultType = memberTypes.find(m => m.isDefault) ?? memberTypes[0];

    return <>
        <form ref={formRef}
              className={formStyles.form}
              onSubmit={onSubmit}
              onChange={clearOutcome}
              aria-label={t('assignTitle')}
              aria-busy={pending}>
            <div className={formStyles.picker}>
                {member
                    ? <div className={formStyles.pickerSelected}>
                        <span>{t('selectedMember')}: <strong>{member.name}</strong></span>
                        <Button variant="outline"
                                onClick={() => {
                                    clearOutcome();
                                    setMember(null);
                                }}>{t('changeMember')}</Button>
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
                                            clearOutcome();
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
                             options={memberTypes.map(m => ({value: String(m.id), label: memberTypeName(m.code, m.name)}))}/>
                <TextField name="validFrom" label={t('validFrom')} type="date" hint={t('validFromHint')}/>
                <TextField name="validTo" label={t('validTo')} type="date" hint={t(bodyKind === 'DISTRICT_COMMISSIONER' ? 'validToHint' : 'validToHintOptional')}/>
                <TextField name="decisionReference"
                           label={t('assignReference')}
                           maxLength={200}
                           hint={t('assignReferenceHint')}/>
            </div>
            <div aria-live="polite">
                {error && <ErrorStatus error={error}/>}
                {violations.length > 0 && <Notice tone="danger" role="alert">
                    <p>{t('violationsTitle')}</p>
                    <ul>{violations.map((v, i) => <li key={`${v.code}-${i}`}>{te.has(v.code) ? te(v.code) : v.message}</li>)}</ul>
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
                    <p>{t('warningsIntro')}</p>
                    <ul>
                        {confirm.check.warnings.map((w, i) => <li key={`${w.code}-${i}`}>
                            <label className={formStyles.checkbox}>
                                <input type="checkbox"
                                       checked={acknowledged.includes(w.code)}
                                       onChange={event => toggle(w.code, event.target.checked)}/>
                                <span>
                                    {warningText(w)}
                                    {w.consecutiveTerms != null && w.maxConsecutiveTerms != null && <>
                                        {' '}({t('termCount', {count: w.consecutiveTerms, max: w.maxConsecutiveTerms})})
                                    </>}
                                </span>
                            </label>
                        </li>)}
                    </ul>
                </div>}
                <div className={formStyles.buttons}>
                    <Button variant="primary"
                            disabled={pending || !allAcknowledged}
                            onClick={() => assign(confirm.input, [...new Set(acknowledged)])}>
                        {t('confirmAssign')}
                    </Button>
                    <Button variant="outline" onClick={() => setConfirm(null)}>{tc('cancel')}</Button>
                </div>
            </>}
        </Dialog>
    </>;
}
