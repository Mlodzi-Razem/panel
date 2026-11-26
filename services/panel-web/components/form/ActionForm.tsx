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

import React, { createContext, useContext, useId, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import Button, { ButtonVariant } from "@/components/button/Button";
import styles from "./Form.module.scss";
import { ActionResult, ApiError } from "@/panel-api/members/types";

type FormState = {
    invalidFields: readonly string[];
    pending: boolean;
};

const FormStateContext = createContext<FormState>({invalidFields: [], pending: false});

export function useFormState(): FormState {
    return useContext(FormStateContext);
}

/** Names the API reports that no input in any form stands for: they are never shown to people. */
const TECHNICAL_FIELDS = new Set(['selfRemovalConfirmations', 'confirmedWarnings', 'kind', 'children', 'memberships']);

/** The outcome of an action the person backed out of: nothing happened, so nothing is reported. */
export const CANCELLED = 'CANCELLED';

export function useErrorMessage() {
    const t = useTranslations('errors');
    const tf = useTranslations('fields');
    return (error: ApiError) => {
        const message = t.has(error.code) ? t(error.code) : error.message;
        const fields = (error.fields ?? [])
            .filter(f => !TECHNICAL_FIELDS.has(f))
            .map(f => (tf.has(f) ? tf(f) : f));
        return {message, fields};
    };
}

export function ErrorStatus({error}: { error: ApiError }) {
    const describe = useErrorMessage();
    const t = useTranslations('errors');
    const {message, fields} = describe(error);
    return <div className={styles.status} data-tone="error" role="alert">
        <p>{message}</p>
        {fields.length > 0 && <>
            <p>{t('affectedFields')}</p>
            <ul>{fields.map(f => <li key={f}>{f}</li>)}</ul>
        </>}
    </div>;
}

export type ActionFormProps<T> = React.PropsWithChildren<{
    action: (formData: FormData) => Promise<ActionResult<T>>;
    submitLabel: string;
    submitVariant?: ButtonVariant;
    successMessage?: string;
    onSuccess?: (result: Extract<ActionResult<T>, { ok: true }>) => void;
    extraButtons?: React.ReactNode;
    disabled?: boolean;
    resetOnSuccess?: boolean;
    ariaLabel?: string;
}>;

export default function ActionForm<T>({
    action,
    submitLabel,
    submitVariant = 'primary',
    successMessage,
    onSuccess,
    extraButtons,
    disabled = false,
    resetOnSuccess = false,
    ariaLabel,
    children,
}: ActionFormProps<T>) {
    const router = useRouter();
    const [result, setResult] = useState<ActionResult<T> | null>(null);
    const [pending, startTransition] = useTransition();

    const onSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const form = event.currentTarget;
        const formData = new FormData(form);
        startTransition(async () => {
            const outcome = await action(formData);
            setResult(!outcome.ok && outcome.error.code === CANCELLED ? null : outcome);
            if (outcome.ok) {
                if (resetOnSuccess) {
                    form.reset();
                }
                onSuccess?.(outcome);
                if (outcome.redirectTo) {
                    router.push(outcome.redirectTo);
                }
            }
        });
    };

    return <FormStateContext value={{invalidFields: result && !result.ok ? (result.error.fields ?? []) : [], pending}}>
        <form className={styles.form} onSubmit={onSubmit} aria-label={ariaLabel} aria-busy={pending}>
            {children}
            <div aria-live="polite">
                {result && !result.ok && <ErrorStatus error={result.error}/>}
                {result && result.ok && successMessage && !result.redirectTo &&
                    <div className={styles.status} data-tone="success" role="status">{successMessage}</div>}
            </div>
            <div className={styles.buttons}>
                <Button type="submit" variant={submitVariant} disabled={disabled || pending}>{submitLabel}</Button>
                {extraButtons}
            </div>
        </form>
    </FormStateContext>;
}

export function useFieldIds(name: string, hasHint: boolean) {
    const id = useId();
    const {invalidFields} = useFormState();
    return {
        id,
        hintId: hasHint ? `${id}-hint` : undefined,
        invalid: invalidFields.includes(name),
    };
}
