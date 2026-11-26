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

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import Button from "@/components/button/Button";
import { useFieldIds } from "@/components/form/ActionForm";
import styles from "./Form.module.scss";

type CommonProps = {
    name: string;
    label: string;
    required?: boolean;
    hint?: string;
    disabled?: boolean;
};

function Label({id, label, required}: { id: string; label: string; required?: boolean }) {
    return <label className={styles.label} htmlFor={id}>
        {label}{required && <span className={styles.required} aria-hidden="true"> *</span>}
    </label>;
}

export type TextFieldProps = CommonProps & {
    type?: 'text' | 'email' | 'url' | 'date' | 'month' | 'tel' | 'search' | 'password';
    defaultValue?: string;
    placeholder?: string;
    autoComplete?: string;
    maxLength?: number;
};

export function TextField({
    name, label, required, hint, disabled, type = 'text', defaultValue, placeholder, autoComplete, maxLength,
}: TextFieldProps) {
    const {id, hintId, invalid} = useFieldIds(name, !!hint);
    return <div className={styles.field}>
        <Label id={id} label={label} required={required}/>
        <input id={id}
               className={styles.input}
               name={name}
               type={type}
               defaultValue={defaultValue}
               placeholder={placeholder}
               autoComplete={autoComplete ?? 'off'}
               maxLength={maxLength}
               required={required}
               disabled={disabled}
               aria-invalid={invalid}
               aria-describedby={hintId}/>
        {hint && <span className={styles.hint} id={hintId}>{hint}</span>}
    </div>;
}

export function TextAreaField({name, label, required, hint, disabled, defaultValue, maxLength}: CommonProps & {
    defaultValue?: string;
    maxLength?: number;
}) {
    const {id, hintId, invalid} = useFieldIds(name, !!hint);
    return <div className={styles.field}>
        <Label id={id} label={label} required={required}/>
        <textarea id={id}
                  className={styles.input}
                  name={name}
                  defaultValue={defaultValue}
                  maxLength={maxLength}
                  required={required}
                  disabled={disabled}
                  aria-invalid={invalid}
                  aria-describedby={hintId}/>
        {hint && <span className={styles.hint} id={hintId}>{hint}</span>}
    </div>;
}

export type SelectOption = { value: string; label: string };

export function SelectField({
    name, label, required, hint, disabled, options, defaultValue, emptyLabel, onChange,
}: CommonProps & {
    options: readonly SelectOption[];
    defaultValue?: string;
    emptyLabel?: string;
    onChange?: (value: string) => void;
}) {
    const {id, hintId, invalid} = useFieldIds(name, !!hint);
    return <div className={styles.field}>
        <Label id={id} label={label} required={required}/>
        <select id={id}
                className={styles.input}
                name={name}
                defaultValue={defaultValue ?? ''}
                required={required}
                disabled={disabled}
                aria-invalid={invalid}
                aria-describedby={hintId}
                onChange={onChange ? (e => onChange(e.target.value)) : undefined}>
            {emptyLabel !== undefined && <option value="">{emptyLabel}</option>}
            {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        {hint && <span className={styles.hint} id={hintId}>{hint}</span>}
    </div>;
}

export function CheckboxField({name, label, defaultChecked, disabled, value = 'true'}: {
    name: string;
    label: string;
    defaultChecked?: boolean;
    disabled?: boolean;
    value?: string;
}) {
    const id = useId();
    return <div className={styles.checkbox}>
        <input id={id} type="checkbox" name={name} value={value} defaultChecked={defaultChecked} disabled={disabled}/>
        <label htmlFor={id}>{label}</label>
    </div>;
}

export function ListField({name, label, hint, disabled, defaultValue, type = 'text'}: CommonProps & {
    defaultValue?: readonly string[];
    type?: 'text' | 'email' | 'tel' | 'url';
}) {
    const t = useTranslations('common');
    const {invalid} = useFieldIds(name, !!hint);
    const hintId = useId();
    const [rows, setRows] = useState<{ key: number; value: string }[]>(() =>
        (defaultValue ?? []).map((value, key) => ({key, value})));
    const [nextKey, setNextKey] = useState(() => (defaultValue ?? []).length);

    const addRow = () => {
        setRows(current => [...current, {key: nextKey, value: ''}]);
        setNextKey(key => key + 1);
    };

    return <fieldset className={styles.field} disabled={disabled}>
        <legend className={styles.label}>{label}</legend>
        <input type="hidden" name="__lists" value={name}/>
        <div className={styles.listRows}>
            {rows.length === 0 && <span className={styles.hint}>{t('listEmpty')}</span>}
            {rows.map((row, index) => <div key={row.key} className={styles.listRow}>
                <input className={styles.input}
                       name={name}
                       type={type}
                       defaultValue={row.value}
                       autoComplete="off"
                       aria-label={`${label} ${index + 1}`}
                       aria-invalid={invalid}
                       aria-describedby={hint ? hintId : undefined}/>
                <Button variant="outline"
                        ariaLabel={`${t('remove')}: ${label} ${index + 1}`}
                        onClick={() => setRows(current => current.filter(r => r.key !== row.key))}>
                    {t('remove')}
                </Button>
            </div>)}
        </div>
        <div>
            <Button variant="outline" onClick={addRow}>{t('add')}</Button>
        </div>
        {hint && <span className={styles.hint} id={hintId}>{hint}</span>}
    </fieldset>;
}
