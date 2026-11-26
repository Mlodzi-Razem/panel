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
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedCaseDate } from "@/components/recruitment/format";
import {
    AcceptIntakeForm,
    AssignDistrictForm,
    DiscardIntakeForm,
    EraseIntakeForm,
} from "@/components/recruitment/IntakeActions";
import { Card, DescriptionList, Notice, Page } from "@/components/page/Page";
import { requireMe } from "@/panel-api/members/requireMe";
import { listDistricts } from "@/panel-api/members/members";
import { getIntake, INTAKE_DISCARD_REASONS } from "@/panel-api/members/intakes";

export default async function IntakePage({params}: { params: Promise<{ id: string }> }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const {id} = await params;
    const t = await getTranslations("intakeQueue");
    const title = t("submissionNumber", {id});
    const result = await getIntake(Number.parseInt(id, 10) || 0);

    if (!result.ok) {
        return <Page title={title} backHref="/rekrutacja/zgloszenia" backLabel={t("title")}>
            <ApiErrorNotice error={result.error}/>
        </Page>;
    }

    const intake = result.data;
    const {summary, actions} = intake;
    const districtsResult = actions.assignDistrict ? await listDistricts() : null;
    const districts = districtsResult?.ok
        ? districtsResult.data.map(d => ({value: String(d.id), label: d.name}))
        : [];

    return <Page title={title} backHref="/rekrutacja/zgloszenia" backLabel={t("title")}>
        <Card id="dane" title={t("dataTitle")}>
            {intake.openCaseWithSameEmail && <Notice tone="warning">{t("openCaseHint")}</Notice>}
            {summary.dataErased && <Notice>{t("erasedHint")}</Notice>}
            {summary.status === "PENDING" && <Notice>{t("unverifiedHint")}</Notice>}
            <DescriptionList>
                <div><dt>{t("state")}</dt><dd>
                    {t(`statuses.${summary.status}`)}
                    {summary.discardReason && ` · ${t(`reasons.${summary.discardReason}`)}`}
                </dd></div>
                <div><dt>{t("receivedAt")}</dt><dd><FormattedCaseDate value={summary.receivedAt}/></dd></div>
                <div><dt>{t("district")}</dt><dd>{summary.district?.name ?? t("noDistrict")}</dd></div>
                {!summary.dataErased && <>
                    <div><dt>{t("fields.name")}</dt><dd>{intake.firstName} {intake.lastName}</dd></div>
                    <div><dt>{t("fields.email")}</dt><dd>{intake.email}</dd></div>
                    <div><dt>{t("fields.phone")}</dt><dd>{intake.phone ?? "—"}</dd></div>
                    <div><dt>{t("fields.birthDate")}</dt><dd>{intake.birthDate}</dd></div>
                    {intake.locality && <div><dt>{t("fields.locality")}</dt><dd>{intake.locality}</dd></div>}
                </>}
                <div><dt>{t("fields.consent")}</dt><dd>
                    {intake.consentVersion}, <FormattedCaseDate value={intake.consentedAt}/>
                </dd></div>
                {summary.caseId != null && <div><dt>{t("case")}</dt><dd>
                    <Link href={`/rekrutacja/${summary.caseId}`}>{t("openCase")}</Link>
                </dd></div>}
            </DescriptionList>
        </Card>
        {actions.accept && <Card id="przyjecie" title={t("acceptTitle")} hint={t("acceptHint")}>
            <AcceptIntakeForm id={summary.id}/>
        </Card>}
        {summary.status === "PENDING" && !actions.accept && <Notice>{t("cannotAccept")}</Notice>}
        {actions.assignDistrict && <Card id="okreg" title={t("assignTitle")}>
            <AssignDistrictForm id={summary.id} districts={districts} current={summary.district?.id}/>
        </Card>}
        {actions.discard && <Card id="odrzucenie" title={t("discardTitle")}>
            <DiscardIntakeForm id={summary.id}
                               reasons={INTAKE_DISCARD_REASONS.map(r => ({value: r, label: t(`reasons.${r}`)}))}/>
        </Card>}
        {actions.erase && <Card id="usuniecie" title={t("eraseTitle")} hint={t("eraseHint")}>
            <EraseIntakeForm id={summary.id}/>
        </Card>}
    </Page>;
}
