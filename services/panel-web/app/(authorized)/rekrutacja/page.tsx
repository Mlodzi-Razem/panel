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
import { getTranslations } from "next-intl/server";
import { TrayIcon, UserPlusIcon } from "@phosphor-icons/react/ssr";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import RecruitmentCasesTable from "@/components/recruitment/RecruitmentCasesTable";
import { LinkButton, Page } from "@/components/page/Page";
import { Pagination, ResultCount, Tabs, Toolbar } from "@/components/ui/Blocks";
import { UrlFilterSelect } from "@/components/ui/UrlControls";
import { listDistricts } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { hasPermission } from "@/panel-api/members/me";
import { CASE_STATUSES, CASE_VIEWS, CaseStatus, getRecruitmentViewCounts, listRecruitmentCases } from "@/panel-api/members/recruitment";

/**
 * The semantic phases of STATES 1 drive the status filter groups (E3): the exact status
 * stays the option value, the group carries the phase meaning.
 */
const STATUS_PHASES: { key: string, statuses: CaseStatus[] }[] = [
    { key: "contact", statuses: ["NEW", "IN_PROGRESS", "NO_CONTACT", "AWAITING_DECLARATION"] },
    { key: "probation", statuses: ["DECLARATION_RECEIVED", "PROBATION", "PROBATION_EXTENDED"] },
    { key: "decision", statuses: ["ZO_RECOMMENDED", "ZK_VOTING"] },
    { key: "terminal", statuses: ["ACCEPTED", "REJECTED"] },
];
import { PERMISSIONS } from "@/panel-api/members/types";
import { listIntakes } from "@/panel-api/members/intakes";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const DEFAULT_SIZE = 25;
const SIZES = [10, 25, 50, 100];

function single(value: string | string[] | undefined): string | undefined {
    return Array.isArray(value) ? value[0] : value;
}

function oneOf<T extends string>(value: string | undefined, allowed: readonly T[]): T | undefined {
    return allowed.find(a => a === value);
}

export default async function RecruitmentPage({searchParams}: { searchParams: SearchParams }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations("recruitment");
    const tEnums = await getTranslations("enums");
    if (!hasPermission(me, PERMISSIONS.recruitmentRead)) {
        return <Page title={t("title")}>
            <ApiErrorNotice error={{status: 403, code: "FORBIDDEN", message: ""}}/>
        </Page>;
    }

    const params = await searchParams;
    const pageNumber = Math.max(0, Number.parseInt(single(params["page"]) ?? "0", 10) || 0);
    const sizeParam = Number.parseInt(single(params["size"]) ?? "", 10);
    const size = SIZES.includes(sizeParam) ? sizeParam : DEFAULT_SIZE;
    const districtParam = Number.parseInt(single(params["district"]) ?? "", 10);
    const status = oneOf(single(params["status"]), CASE_STATUSES);
    const openParam = single(params["open"]);
    const open = openParam === "true" ? true : openParam === "false" ? false : undefined;

    const view = oneOf(single(params["view"]), CASE_VIEWS);
    const district = Number.isFinite(districtParam) ? districtParam : undefined;

    const [districtsResult, casesResult, viewCountsResult, intakeResult] = await Promise.all([
        listDistricts(),
        listRecruitmentCases({
            page: pageNumber,
            size,
            district: Number.isFinite(districtParam) ? districtParam : undefined,
            status,
            open,
            view,
        }),
        getRecruitmentViewCounts(district),
        // answers 403 to people who do not handle submissions; the link is then not shown
        listIntakes("PENDING", 0, 1),
    ]);
    const viewCounts = viewCountsResult.ok ? viewCountsResult.data : {};

    const districts = districtsResult.ok ? districtsResult.data : [];
    const canCreate = districts.some(d => d.permissions.includes(PERMISSIONS.recruitmentManage));

    const hrefWith = (changes: Record<string, string | undefined>) => {
        const next = new URLSearchParams();
        const current: Record<string, string | undefined> = {
            district: Number.isFinite(districtParam) ? String(districtParam) : undefined,
            view,
            status,
            open: open === undefined ? undefined : String(open),
            size: size !== DEFAULT_SIZE ? String(size) : undefined,
            page: pageNumber > 0 ? String(pageNumber) : undefined,
        };
        for (const [key, value] of Object.entries({...current, ...changes})) {
            if (value) {
                next.set(key, value);
            }
        }
        const qs = next.toString();
        return qs ? `/rekrutacja?${qs}` : "/rekrutacja";
    };

    return <Page title={t("title")}
                 subtitle={t("subtitle")}
                 actions={<>
                     {intakeResult.ok && <LinkButton href="/rekrutacja/zgloszenia">
                         <TrayIcon aria-hidden/>{t("intakeQueue", {count: intakeResult.data.totalItems})}
                     </LinkButton>}
                     {canCreate && <LinkButton href="/rekrutacja/nowa" primary>
                         <UserPlusIcon aria-hidden/>{t("newCase")}
                     </LinkButton>}
                 </>}>
        <Tabs label={t("viewsLabel")}
              items={[
                  {
                      href: hrefWith({view: undefined, status: undefined, open: undefined, page: undefined}),
                      label: t("views.ALL"),
                      active: !view,
                  },
                  ...CASE_VIEWS.map(v => ({
                      href: hrefWith({view: v, status: undefined, open: undefined, page: undefined}),
                      label: t(`views.${v}`),
                      active: view === v,
                      count: viewCounts[v],
                  })),
              ]}/>
        <Toolbar end={casesResult.ok && <ResultCount>
            {t("resultCount", {count: casesResult.data.totalItems})}
        </ResultCount>}>
            <UrlFilterSelect param="district"
                             label={t("district")}
                             allLabel={t("allDistricts")}
                             options={districts.map(d => ({value: String(d.id), label: d.name}))}/>
            <UrlFilterSelect param="status"
                             label={t("status")}
                             allLabel={t("anyStatus")}
                             options={[]}
                             groups={STATUS_PHASES.map(phase => ({
                                 label: t(`statusGroups.${phase.key}`),
                                 options: phase.statuses.map(s => ({
                                     value: s,
                                     label: tEnums(`recruitmentStatus.${s}`),
                                 })),
                             }))}/>
            <UrlFilterSelect param="open"
                             label={t("openFilter")}
                             allLabel={t("anyOpenness")}
                             options={[
                                 {value: "true", label: t("openCases")},
                                 {value: "false", label: t("closedCases")},
                             ]}/>
        </Toolbar>
        {casesResult.ok
            ? <>
                <RecruitmentCasesTable page={casesResult.data}/>
                <Pagination page={casesResult.data.page}
                            totalPages={casesResult.data.totalPages}
                            totalItems={casesResult.data.totalItems}
                            size={size}
                            defaultSize={DEFAULT_SIZE}
                            sizeOptions={SIZES}
                            hrefFor={p => hrefWith({page: p > 0 ? String(p) : undefined})}/>
            </>
            : <ApiErrorNotice error={casesResult.error}/>}
    </Page>;
}
