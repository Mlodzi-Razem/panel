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
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { CheckIcon, XIcon } from "@phosphor-icons/react/ssr";
import { decideChangeRequestAction } from "@/app/actions/changeRequests";
import Button from "@/components/button/Button";
import { ErrorStatus } from "@/components/form/ActionForm";
import { TextAreaField } from "@/components/form/Fields";
import formStyles from "@/components/form/Form.module.scss";
import { ActionResult } from "@/panel-api/members/types";

export default function DecisionForm({requestId}: { requestId: number }) {
    const t = useTranslations('changeRequests');
    const router = useRouter();
    const [result, setResult] = useState<ActionResult | null>(null);
    const [pending, startTransition] = useTransition();
    const formRef = useRef<HTMLFormElement>(null);

    const decide = (decision: 'approve' | 'reject') => {
        const note = formRef.current ? String(new FormData(formRef.current).get('note') ?? '') : '';
        startTransition(async () => {
            const outcome = await decideChangeRequestAction(requestId, decision, note);
            setResult(outcome);
            if (outcome.ok && outcome.redirectTo) {
                router.push(outcome.redirectTo);
            }
        });
    };

    return <form ref={formRef}
                 className={formStyles.form}
                 aria-label={t('decisionTitle')}
                 aria-busy={pending}
                 onSubmit={event => event.preventDefault()}>
        <TextAreaField name="note" label={t('noteLabel')} hint={t('noteHint')} maxLength={2000}/>
        <div aria-live="polite">
            {result && !result.ok && <ErrorStatus error={result.error}/>}
        </div>
        <div className={formStyles.buttons}>
            <Button variant="primary"
                    iconLeft={<CheckIcon aria-hidden/>}
                    disabled={pending}
                    onClick={() => decide('approve')}>
                {t('approve')}
            </Button>
            <Button variant="danger"
                    iconLeft={<XIcon aria-hidden/>}
                    disabled={pending}
                    onClick={() => decide('reject')}>
                {t('reject')}
            </Button>
        </div>
    </form>;
}
