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

import { CANCELLED } from "@/components/form/ActionForm";
import React, { useRef, useState } from "react";
import { useTranslations } from "next-intl";
import Button from "@/components/button/Button";
import Dialog from "@/components/dialog/Dialog";
import formStyles from "@/components/form/Form.module.scss";
import { ActionResult } from "@/panel-api/members/types";

/**
 * Removing one's own function takes two separate confirmations. The API decides whose function it is: the first
 * request goes without any, and only its SELF_REMOVAL_CONFIRMATION_REQUIRED answer opens the two dialogs.
 */
export function useSelfRemoval() {
    const t = useTranslations('bodies');
    const tc = useTranslations('common');
    const [step, setStep] = useState<0 | 1 | 2>(0);
    const answer = useRef<((confirmed: boolean) => void) | null>(null);

    const finish = (confirmed: boolean) => {
        setStep(0);
        answer.current?.(confirmed);
        answer.current = null;
    };

    async function run(send: (confirmations: string[]) => Promise<ActionResult>): Promise<ActionResult> {
        const first = await send([]);
        if (first.ok || first.error.code !== 'SELF_REMOVAL_CONFIRMATION_REQUIRED') {
            return first;
        }
        const confirmed = await new Promise<boolean>(resolve => {
            answer.current = resolve;
            setStep(1);
        });
        return confirmed
            ? send(['SELF_REMOVAL', 'SELF_REMOVAL_FINAL'])
            : {ok: false, error: {status: 0, code: CANCELLED, message: ''}};
    }

    // the buttons swap places between the steps, so a double click cannot give both confirmations
    const dialog = <Dialog open={step !== 0}
                           title={step === 2 ? t('selfRemovalFinalTitle') : t('selfRemovalTitle')}
                           onClose={() => finish(false)}>
        {step === 1 && <>
            <p>{t('selfRemovalText')}</p>
            <div className={formStyles.buttons}>
                <Button key="confirm-first" variant="danger" onClick={() => setStep(2)}>{t('selfRemovalContinue')}</Button>
                <Button key="cancel-first" variant="outline" onClick={() => finish(false)}>{tc('cancel')}</Button>
            </div>
        </>}
        {step === 2 && <>
            <p>{t('selfRemovalFinalText')}</p>
            <div className={formStyles.buttons}>
                <Button key="cancel-final" variant="outline" onClick={() => finish(false)}>{tc('cancel')}</Button>
                <Button key="confirm-final" variant="danger" onClick={() => finish(true)}>{t('selfRemovalConfirm')}</Button>
            </div>
        </>}
    </Dialog>;

    return {run, dialog};
}
