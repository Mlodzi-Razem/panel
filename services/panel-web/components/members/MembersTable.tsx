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
import { useTranslations } from "next-intl";
import { EyeIcon, UsersThreeIcon } from "@phosphor-icons/react/ssr";
import { FieldValue, FormattedDate, memberName } from "@/components/members/format";
import { TableWrap } from "@/components/page/Page";
import { Chips, EmptyState, IconLink, NameCell, RowActions, SortHeader, SortState } from "@/components/ui/Blocks";
import { MemberSummary } from "@/panel-api/members/types";

export type MembersTableProps = {
    items: MemberSummary[];
    showEnding?: boolean;
    sort?: SortState;
    sortHref?: (sortParam: string) => string;
};

export default function MembersTable({items, showEnding = false, sort, sortHref}: MembersTableProps) {
    const t = useTranslations('members');
    const tCommon = useTranslations('common');
    const hasStatus = items.some(i => 'status' in i.fields);
    const hasMemberId = items.some(i => 'memberId' in i.fields);

    if (items.length === 0) {
        return <EmptyState icon={<UsersThreeIcon aria-hidden/>} title={t('noResults')}>
            <span>{t('noResultsHint')}</span>
        </EmptyState>;
    }

    const header = (label: string, field: string) => sort && sortHref
        ? <SortHeader label={label} field={field} sort={sort} hrefFor={sortHref}/>
        : <th scope="col">{label}</th>;

    return <TableWrap>
        <thead>
            <tr>
                {header(t('name'), 'name')}
                {hasMemberId && header(t('memberId'), 'memberId')}
                {hasStatus && header(t('status'), 'status')}
                {hasStatus && header(t('membershipType'), 'membershipType')}
                {header(t('primaryDistrict'), 'district')}
                {showEnding && <th scope="col">{t('membershipEnds')}</th>}
                <th scope="col"><span style={{display: 'block', textAlign: 'right'}}>{tCommon('actions')}</span></th>
            </tr>
        </thead>
        <tbody>
            {items.map(item => {
                const name = memberName(item.fields) || `#${item.id}`;
                const pseudonym = typeof item.fields['pseudonym'] === 'string' ? item.fields['pseudonym'] as string : null;
                const domainEmail = typeof item.fields['domainEmail'] === 'string' ? item.fields['domainEmail'] as string : null;
                return <tr key={item.id}>
                    <td><NameCell name={name} secondary={domainEmail ?? pseudonym} href={`/members/${item.id}`}/></td>
                    {hasMemberId && <td>{typeof item.fields['memberId'] === 'string' && item.fields['memberId'] ? item.fields['memberId'] as string : '—'}</td>}
                    {hasStatus && <td><FieldValue def={{name: 'status', kind: 'status'}} value={item.fields['status']}/></td>}
                    {hasStatus && <td>
                        <FieldValue def={{name: 'membershipType', kind: 'membershipType'}} value={item.fields['membershipType']}/>
                    </td>}
                    <td>
                        <div>{item.primaryDistrict?.name ?? '—'}</div>
                        {item.supportingDistricts.length > 0 &&
                            <Chips items={item.supportingDistricts.map(d => `+ ${d.name}`)}/>}
                    </td>
                    {showEnding && <td><FormattedDate value={item.membershipEndsOn}/></td>}
                    <td>
                        <RowActions>
                            <IconLink href={`/members/${item.id}`} label={t('open', {name})}><EyeIcon aria-hidden/></IconLink>
                        </RowActions>
                    </td>
                </tr>;
            })}
        </tbody>
    </TableWrap>;
}
