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
import { getTranslations } from "next-intl/server";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate } from "@/components/members/format";
import { Card, Page, TableWrap } from "@/components/page/Page";
import { listPendingChangeRequests } from "@/panel-api/members/changeRequests";
import { requireMe } from "@/panel-api/members/requireMe";

export default async function ChangeRequestsPage() {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('changeRequests');
    const tm = await getTranslations('members');
    const tf = await getTranslations('fields');
    const result = await listPendingChangeRequests();

    return <Page title={t('title')} subtitle={t('hint')} backHref="/members" backLabel={tm('title')}>
        <Card>
            {!result.ok && <ApiErrorNotice error={result.error}/>}
            {result.ok && result.data.length === 0 && <p>{t('empty')}</p>}
            {result.ok && result.data.length > 0 && <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('member')}</th>
                        <th scope="col">{t('requestedBy')}</th>
                        <th scope="col">{t('fields')}</th>
                        <th scope="col">{t('createdAt')}</th>
                    </tr>
                </thead>
                <tbody>
                    {result.data.map(request => <tr key={request.id}>
                        <td><Link href={`/members/change-requests/${request.id}`}>{request.member.displayName}</Link></td>
                        <td>{request.requestedBy.displayName}</td>
                        <td>{request.fields.map(f => (tf.has(f) ? tf(f) : f)).join(', ')}</td>
                        <td><FormattedDate value={request.createdAt}/></td>
                    </tr>)}
                </tbody>
            </TableWrap>}
        </Card>
    </Page>;
}
