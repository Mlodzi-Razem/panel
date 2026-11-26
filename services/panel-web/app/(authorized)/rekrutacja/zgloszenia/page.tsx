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
import { getTranslations } from "next-intl/server";
import { TrayIcon } from "@phosphor-icons/react/ssr";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedCaseDate } from "@/components/recruitment/format";
import { Badge, Page, TableWrap } from "@/components/page/Page";
import { EmptyState, Pagination, Tabs } from "@/components/ui/Blocks";
import { requireMe } from "@/panel-api/members/requireMe";
import { INTAKE_STATUSES, listIntakes } from "@/panel-api/members/intakes";

const SIZE = 25;

export default async function IntakeQueuePage({searchParams}: {
    searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations("intakeQueue");
    const tRecruitment = await getTranslations("recruitment");
    const params = await searchParams;
    const status = INTAKE_STATUSES.find(s => s === params["status"]) ?? "PENDING";
    const pageNumber = Math.max(0, Number.parseInt(String(params["page"] ?? "0"), 10) || 0);
    const result = await listIntakes(status, pageNumber, SIZE);

    const hrefFor = (target: string, page = 0) =>
        `/rekrutacja/zgloszenia?status=${target}${page > 0 ? `&page=${page}` : ""}`;

    return <Page title={t("title")} subtitle={t("subtitle")}
                 backHref="/rekrutacja" backLabel={tRecruitment("title")}>
        <Tabs label={t("statusLabel")}
              items={INTAKE_STATUSES.map(s => ({
                  href: hrefFor(s), label: t(`statuses.${s}`), active: s === status,
              }))}/>
        {!result.ok
            ? <ApiErrorNotice error={result.error}/>
            : result.data.items.length === 0
                ? <EmptyState icon={<TrayIcon aria-hidden/>} title={t("empty")}>
                    <span>{t("emptyHint")}</span>
                </EmptyState>
                : <>
                    <TableWrap>
                        <thead>
                            <tr>
                                <th scope="col">{t("submission")}</th>
                                <th scope="col">{t("receivedAt")}</th>
                                <th scope="col">{t("district")}</th>
                                <th scope="col">{t("state")}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {result.data.items.map(item => <tr key={item.id}>
                                {/* no personal data in the list: it lives in the detail view only */}
                                <td><Link href={`/rekrutacja/zgloszenia/${item.id}`}>
                                    {t("submissionNumber", {id: String(item.id)})}
                                </Link></td>
                                <td><FormattedCaseDate value={item.receivedAt}/></td>
                                <td>{item.district?.name ?? <Badge tone="warning">{t("noDistrict")}</Badge>}</td>
                                <td>
                                    {t(`statuses.${item.status}`)}
                                    {item.discardReason && ` · ${t(`reasons.${item.discardReason}`)}`}
                                    {item.dataErased && ` · ${t("erased")}`}
                                </td>
                            </tr>)}
                        </tbody>
                    </TableWrap>
                    <Pagination page={result.data.page}
                                totalPages={result.data.totalPages}
                                totalItems={result.data.totalItems}
                                size={SIZE}
                                defaultSize={SIZE}
                                sizeOptions={[SIZE]}
                                hrefFor={p => hrefFor(status, p)}/>
                </>}
    </Page>;
}
