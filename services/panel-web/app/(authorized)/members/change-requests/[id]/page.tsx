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

import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { ArrowRightIcon } from "@phosphor-icons/react/ssr";
import styles from "@/components/requests/Requests.module.scss";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import DecisionForm from "@/components/members/DecisionForm";
import { FieldValue, FormattedDate } from "@/components/members/format";
import { Badge, Card, DescriptionList, Muted, Notice, Page, TableWrap } from "@/components/page/Page";
import RequestStatus from "@/components/requests/RequestStatus";
import { RecordHeader } from "@/components/ui/Blocks";
import { getChangeRequest } from "@/panel-api/members/changeRequests";
import { fieldDef } from "@/panel-api/members/fields";
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
    const pending = request.status === 'PENDING';
    const anyStale = changes.some(c => c.stale);

    const header = <RecordHeader name={request.member.displayName}
                                 subtitle={t('requestedByLabel', {name: request.requestedBy.displayName})}
                                 badges={<RequestStatus status={request.status}/>}
                                 facts={[
                                     {label: t('createdAt'), value: <FormattedDate value={request.createdAt}/>},
                                     {label: t('changesCount'), value: changes.length},
                                 ]}/>;

    return <Page title={t('detailTitle', {member: request.member.displayName})}
                 backHref="/members/change-requests"
                 backLabel={t('title')}
                 header={header}>
        {anyStale && pending && <Notice tone="warning" role="alert">{t('staleWarning')}</Notice>}

        <div className={styles.detail}>
            <div className={styles.main}>
                <Card id="request-changes" title={t('changesTitle')}>
                    <TableWrap>
                        <thead>
                            <tr>
                                <th scope="col">{t('field')}</th>
                                <th scope="col">{t('current')}</th>
                                <th scope="col">{t('proposed')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {changes.map(change => {
                                const def = fieldDef(change.field) ?? {name: change.field, kind: 'text' as const};
                                return <tr key={change.field} className={change.stale ? styles.stale : undefined}>
                                    <th scope="row">{tf.has(change.field) ? tf(change.field) : change.field}</th>
                                    <td>
                                        <FieldValue def={def} value={change.current}/>
                                        {change.stale && <div className={styles.staleLine}>
                                            <Badge tone="warning">{t('stale')}</Badge>
                                            <Muted>{t('snapshot')}: <FieldValue def={def} value={change.snapshot}/></Muted>
                                        </div>}
                                    </td>
                                    <td className={styles.proposed}>
                                        <ArrowRightIcon aria-hidden/>
                                        <strong><FieldValue def={def} value={change.proposed}/></strong>
                                    </td>
                                </tr>;
                            })}
                        </tbody>
                    </TableWrap>
                </Card>

                {(request.reason || request.decisionNote || request.decidedBy) && <Card id="request-notes" title={t('notesTitle')}>
                    <DescriptionList>
                        {request.reason && <div><dt>{t('reason')}</dt><dd>{request.reason}</dd></div>}
                        {request.decidedBy && <div>
                            <dt>{t('decidedByLabel')}</dt>
                            <dd>{request.decidedBy.displayName}</dd>
                        </div>}
                        {request.decisionNote && <div><dt>{t('decisionNote')}</dt><dd>{request.decisionNote}</dd></div>}
                    </DescriptionList>
                </Card>}
            </div>

            {pending && <aside className={styles.side}>
                <Card id="request-decision" title={t('decisionTitle')} hint={t('decisionHint')}>
                    <DecisionForm requestId={request.id}/>
                </Card>
            </aside>}
        </div>
    </Page>;
}
