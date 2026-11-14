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

import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { StatusBadge } from "@/components/members/ChangeRequestList";
import DecisionForm from "@/components/members/DecisionForm";
import { FieldValue, FormattedDate } from "@/components/members/format";
import { Badge, Card, Notice, Page, TableWrap } from "@/components/page/Page";
import { fieldDef } from "@/panel-api/members/fields";
import { getChangeRequest } from "@/panel-api/members/changeRequests";
import { requireMe } from "@/panel-api/members/requireMe";

export default async function ChangeRequestPage({params}: { params: Promise<{ id: string }> }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const {id: rawId} = await params;
    if (!/^\d+$/.test(rawId)) {
        notFound();
    }
    const id = Number(rawId);

    const t = await getTranslations('changeRequests');
    const tf = await getTranslations('fields');
    const result = await getChangeRequest(id);
    if (!result.ok) {
        if (result.error.status === 404) {
            notFound();
        }
        return <Page title={t('title')} backHref="/members/change-requests" backLabel={t('title')}>
            <ApiErrorNotice error={result.error}/>
        </Page>;
    }

    const {request, changes} = result.data;
    const anyStale = changes.some(c => c.stale);

    return <Page title={t('detailTitle', {member: request.member.displayName})}
                 backHref="/members/change-requests"
                 backLabel={t('title')}
                 actions={<StatusBadge status={request.status}/>}>
        <Card id="request-summary">
            <p>
                <Link href={`/members/${request.member.id}`}>{request.member.displayName}</Link>
                {' · '}{t('requestedByLabel', {name: request.requestedBy.displayName})}
                {request.createdAt && <>{' · '}<FormattedDate value={request.createdAt}/></>}
            </p>
            {request.reason && <p>{t('reason')}: {request.reason}</p>}
            {request.decisionNote && <p>{t('decisionNote')}: {request.decisionNote}</p>}
            {request.decidedBy && <p>{t('decidedBy', {name: request.decidedBy.displayName})}</p>}
        </Card>

        {anyStale && request.status === 'PENDING' && <Notice tone="warning" role="alert">{t('staleWarning')}</Notice>}

        <Card id="request-changes" title={t('changesTitle')}>
            <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('field')}</th>
                        <th scope="col">{t('snapshot')}</th>
                        <th scope="col">{t('current')}</th>
                        <th scope="col">{t('proposed')}</th>
                    </tr>
                </thead>
                <tbody>
                    {changes.map(change => {
                        const def = fieldDef(change.field) ?? {name: change.field, kind: 'text' as const};
                        return <tr key={change.field}>
                            <th scope="row">
                                {tf.has(change.field) ? tf(change.field) : change.field}{' '}
                                {change.stale && <Badge tone="warning">{t('stale')}</Badge>}
                            </th>
                            <td><FieldValue def={def} value={change.snapshot}/></td>
                            <td><FieldValue def={def} value={change.current}/></td>
                            <td><strong><FieldValue def={def} value={change.proposed}/></strong></td>
                        </tr>;
                    })}
                </tbody>
            </TableWrap>
        </Card>

        {request.status === 'PENDING' && <Card id="request-decision" title={t('decisionTitle')} hint={t('decisionHint')}>
            <DecisionForm requestId={request.id}/>
        </Card>}
    </Page>;
}
