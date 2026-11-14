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

import React from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { Empty, FieldValue, FormattedDate, memberName } from "@/components/members/format";
import { TableWrap } from "@/components/page/Page";
import { MemberSummary } from "@/panel-api/members/types";

export default function MembersTable({items, showEnding = false}: { items: MemberSummary[]; showEnding?: boolean }) {
    const t = useTranslations('members');
    const hasStatus = items.some(i => 'status' in i.fields);
    const hasMemberId = items.some(i => 'memberId' in i.fields);

    if (items.length === 0) {
        return <p>{t('noResults')}</p>;
    }

    return <TableWrap>
        <thead>
            <tr>
                <th scope="col">{t('name')}</th>
                {hasMemberId && <th scope="col">{t('memberId')}</th>}
                {hasStatus && <th scope="col">{t('status')}</th>}
                {hasStatus && <th scope="col">{t('membershipType')}</th>}
                <th scope="col">{t('primaryDistrict')}</th>
                <th scope="col">{t('supportingDistricts')}</th>
                {showEnding && <th scope="col">{t('membershipEnds')}</th>}
            </tr>
        </thead>
        <tbody>
            {items.map(item => <tr key={item.id}>
                <td><Link href={`/members/${item.id}`}>{memberName(item.fields) || `#${item.id}`}</Link></td>
                {hasMemberId && <td><FieldValue def={{name: 'memberId', kind: 'text'}} value={item.fields['memberId']}/></td>}
                {hasStatus && <td><FieldValue def={{name: 'status', kind: 'status'}} value={item.fields['status']}/></td>}
                {hasStatus && <td>
                    <FieldValue def={{name: 'membershipType', kind: 'membershipType'}}
                                value={item.fields['membershipType']}/>
                </td>}
                <td>{item.primaryDistrict?.name ?? <Empty/>}</td>
                <td>{item.supportingDistricts.length > 0
                    ? item.supportingDistricts.map(d => d.name).join(', ')
                    : <Empty/>}</td>
                {showEnding && <td><FormattedDate value={item.membershipEndsOn}/></td>}
            </tr>)}
        </tbody>
    </TableWrap>;
}
