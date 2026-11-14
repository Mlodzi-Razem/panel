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
import Button, { ButtonVariant } from "@/components/button/Button";
import { ErrorStatus } from "@/components/form/ActionForm";
import { ActionResult } from "@/panel-api/members/types";

export type ActionButtonProps<T> = {
    action: () => Promise<ActionResult<T>>;
    label: string;
    variant?: ButtonVariant;
    confirmMessage?: string;
    onSuccess?: (result: Extract<ActionResult<T>, { ok: true }>) => void;
    disabled?: boolean;
    ariaLabel?: string;
};

export default function ActionButton<T>({
    action, label, variant = 'outline', confirmMessage, onSuccess, disabled, ariaLabel,
}: ActionButtonProps<T>) {
    const [result, setResult] = useState<ActionResult<T> | null>(null);
    const [pending, startTransition] = useTransition();

    const run = () => {
        if (confirmMessage && !window.confirm(confirmMessage)) {
            return;
        }
        startTransition(async () => {
            const outcome = await action();
            setResult(outcome);
            if (outcome.ok) {
                onSuccess?.(outcome);
            }
        });
    };

    return <span>
        <Button variant={variant} onClick={run} disabled={disabled || pending} ariaLabel={ariaLabel}>{label}</Button>
        {result && !result.ok && <ErrorStatus error={result.error}/>}
    </span>;
}
