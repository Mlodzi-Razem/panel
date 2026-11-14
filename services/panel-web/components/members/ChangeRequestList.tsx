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

import React from "react";
import { useTranslations } from "next-intl";
import { cancelChangeRequestAction } from "@/app/actions/me";
import ActionButton from "@/components/form/ActionButton";
import { FieldValue, FormattedDate } from "@/components/members/format";
import { Badge, Card, Notice, Tone } from "@/components/page/Page";
import { fieldDef } from "@/panel-api/members/fields";
import { ChangeRequest, ChangeRequestStatus } from "@/panel-api/members/types";

const TONES: Record<ChangeRequestStatus, Tone> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'neutral',
};

export function StatusBadge({status}: { status: ChangeRequestStatus }) {
    const t = useTranslations('enums');
    return <Badge tone={TONES[status]}>{t(`changeRequestStatus.${status}`)}</Badge>;
}

export default function ChangeRequestList({requests}: { requests: ChangeRequest[] }) {
    const t = useTranslations('user');
    const tf = useTranslations('fields');
    const tc = useTranslations('common');

    return <Card id="my-change-requests" title={t('requestsTitle')}>
        {requests.length === 0 && <p>{t('requestsEmpty')}</p>}
        {requests.map(request => <Notice key={request.id}>
            <p>
                <StatusBadge status={request.status}/>{' '}
                {request.createdAt && <FormattedDate value={request.createdAt}/>}
            </p>
            <ul>
                {request.fields.map(name => {
                    const def = fieldDef(name);
                    const value = request.proposed?.[name];
                    return <li key={name}>
                        {tf.has(name) ? tf(name) : name}
                        {request.proposed && def && name !== 'formalIdentifier' && <>
                            {': '}<FieldValue def={def} value={value}/>
                        </>}
                    </li>;
                })}
            </ul>
            {request.reason && <p>{t('reasonLabel')}: {request.reason}</p>}
            {request.decisionNote && <p>{t('decisionNoteLabel')}: {request.decisionNote}</p>}
            {request.status === 'PENDING' && <ActionButton label={t('cancelRequest')}
                                                           confirmMessage={t('cancelConfirm')}
                                                           action={() => cancelChangeRequestAction(request.id)}/>}
            {request.decidedAt && <p>{tc('decidedOn')} <FormattedDate value={request.decidedAt}/></p>}
        </Notice>)}
    </Card>;
}
