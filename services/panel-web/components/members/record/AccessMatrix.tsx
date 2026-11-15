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
import { CheckCircleIcon, MinusIcon, ShieldCheckIcon } from "@phosphor-icons/react/ssr";
import styles from "@/components/members/record/record.module.scss";
import { Card, TableWrap } from "@/components/page/Page";
import { EmptyState } from "@/components/ui/Blocks";
import { LinkedMe, PERMISSIONS } from "@/panel-api/members/types";

const KNOWN: string[] = [
    PERMISSIONS.contactRead,
    PERMISSIONS.contactManage,
    PERMISSIONS.formalRead,
    PERMISSIONS.formalManage,
    PERMISSIONS.bodiesManage,
];

type Row = { key: string; label: string; permissions: string[] };

export default function AccessMatrix({permissions}: { permissions: LinkedMe['permissions'] }) {
    const t = useTranslations('user');

    const rows: Row[] = [];
    if (permissions.global.length > 0) {
        rows.push({key: 'global', label: t('accessGlobal'), permissions: permissions.global});
    }
    permissions.perDistrict
        .filter(d => d.permissions.length > 0)
        .forEach(d => rows.push({key: `d${d.districtId}`, label: d.districtName, permissions: d.permissions}));

    const used = new Set(rows.flatMap(r => r.permissions));
    const columns = [...KNOWN.filter(p => used.has(p)), ...[...used].filter(p => !KNOWN.includes(p)).sort()];
    const label = (permission: string) => t.has(`permissions.${permission}`) ? t(`permissions.${permission}`) : permission;

    return <Card id="access" title={t('accessTitle')} hint={t('accessHint')}>
        {rows.length === 0
            ? <EmptyState icon={<ShieldCheckIcon aria-hidden/>} title={t('accessNone')}/>
            : <TableWrap>
                <thead>
                    <tr>
                        <th scope="col">{t('accessDistrict')}</th>
                        {columns.map(c => <th key={c} scope="col">{label(c)}</th>)}
                    </tr>
                </thead>
                <tbody>
                    {rows.map(row => <tr key={row.key}>
                        <th scope="row">{row.label}</th>
                        {columns.map(c => row.permissions.includes(c)
                            ? <td key={c}>
                                <span className={styles.yes}>
                                    <CheckCircleIcon size={22} weight="fill" aria-hidden/>
                                    <span className={styles.srOnly}>{t('accessGranted')}</span>
                                </span>
                            </td>
                            : <td key={c}>
                                <span className={styles.no}>
                                    <MinusIcon size={18} aria-hidden/>
                                    <span className={styles.srOnly}>{t('accessNotGranted')}</span>
                                </span>
                            </td>)}
                    </tr>)}
                </tbody>
            </TableWrap>}
    </Card>;
}
