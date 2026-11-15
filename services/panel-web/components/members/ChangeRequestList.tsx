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
import { ClipboardTextIcon } from "@phosphor-icons/react/ssr";
import { cancelChangeRequestAction } from "@/app/actions/me";
import ActionButton from "@/components/form/ActionButton";
import { FieldValue, FormattedDate } from "@/components/members/format";
import styles from "@/components/members/record/record.module.scss";
import { Badge, Card, TableWrap, Tone } from "@/components/page/Page";
import { EmptyState } from "@/components/ui/Blocks";
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

    return <Card id="my-change-requests" title={t('requestsTitle')} hint={t('requestsHint')}>
        {requests.length === 0
            ? <EmptyState icon={<ClipboardTextIcon aria-hidden/>} title={t('requestsEmpty')}/>
            : <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('reqSubmitted')}</th>
                        <th scope="col">{t('reqChanges')}</th>
                        <th scope="col">{t('reqStatus')}</th>
                        <th scope="col">{t('reqNotes')}</th>
                        <th scope="col">{tc('actions')}</th>
                    </tr>
                </thead>
                <tbody>
                    {requests.map(request => <tr key={request.id}>
                        <td>{request.createdAt && <FormattedDate value={request.createdAt}/>}</td>
                        <td>
                            <ul className={styles.requestFields}>
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
                        </td>
                        <td>
                            <StatusBadge status={request.status}/>
                            {request.decidedAt && <>
                                <br/><small>{tc('decidedOn')} <FormattedDate value={request.decidedAt}/></small>
                            </>}
                        </td>
                        <td>
                            <div className={styles.notes}>
                                {request.reason && <span>{t('reasonLabel')}: {request.reason}</span>}
                                {request.decisionNote && <span>{t('decisionNoteLabel')}: {request.decisionNote}</span>}
                            </div>
                        </td>
                        <td>
                            {request.status === 'PENDING' && <ActionButton label={t('cancelRequest')}
                                                                           confirmMessage={t('cancelConfirm')}
                                                                           action={() => cancelChangeRequestAction(request.id)}/>}
                        </td>
                    </tr>)}
                </tbody>
            </TableWrap>}
    </Card>;
}
