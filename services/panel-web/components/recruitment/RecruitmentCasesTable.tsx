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
import { UserPlusIcon } from "@phosphor-icons/react/ssr";
import { Badge, TableWrap } from "@/components/page/Page";
import { EmptyState, NameCell } from "@/components/ui/Blocks";
import styles from "@/components/recruitment/recruitment.module.scss";
import { candidateName, caseAgeDays, caseStatusTone, FormattedCaseDate } from "@/components/recruitment/format";
import { CasePage } from "@/panel-api/members/recruitment";

export default function RecruitmentCasesTable({page}: { page: CasePage }) {
    const t = useTranslations("recruitment");
    const tEnums = useTranslations("enums");

    if (page.items.length === 0) {
        return <EmptyState icon={<UserPlusIcon aria-hidden/>} title={t("empty")}>
            <span>{t("emptyHint")}</span>
        </EmptyState>;
    }

    return <TableWrap>
        <thead>
            <tr>
                <th scope="col">{t("candidate")}</th>
                <th scope="col">{t("district")}</th>
                <th scope="col">{t("status")}</th>
                <th scope="col">{t("source")}</th>
                <th scope="col">{t("caseAge")}</th>
                <th scope="col">{t("updatedAt")}</th>
            </tr>
        </thead>
        <tbody>
            {page.items.map(item => <tr key={item.id}>
                <td>
                    {/* RODO: the candidate's contact data lives in the detail view only — the list row shows the name alone. */}
                    <NameCell
                        name={candidateName(item) || `#${item.id}`}
                        href={`/rekrutacja/${item.id}`}/>
                </td>
                <td>{item.district.name}</td>
                <td>
                    <Badge tone={caseStatusTone(item.status)}>{tEnums(`recruitmentStatus.${item.status}`)}</Badge>
                    {item.rejectionReason && <div className={styles.reason}>
                        {tEnums(`recruitmentRejectionReason.${item.rejectionReason}`)}
                    </div>}
                </td>
                <td>{tEnums(`recruitmentSource.${item.source}`)}</td>
                <td>{t("days", {count: caseAgeDays(item.createdAt)})}</td>
                <td><FormattedCaseDate value={item.updatedAt ?? item.createdAt}/></td>
            </tr>)}
        </tbody>
    </TableWrap>;
}
