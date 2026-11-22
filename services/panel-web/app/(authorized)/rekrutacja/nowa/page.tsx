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
import { createRecruitmentCaseAction } from "@/app/actions/recruitment";
import ActionForm from "@/components/form/ActionForm";
import { SelectField, TextField } from "@/components/form/Fields";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { Card, Page } from "@/components/page/Page";
import { listDistricts } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { CASE_SOURCES } from "@/panel-api/members/recruitment";
import { PERMISSIONS } from "@/panel-api/members/types";

export default async function NewRecruitmentCasePage() {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations("recruitment");
    const tEnums = await getTranslations("enums");

    const districts = await listDistricts();
    if (!districts.ok) {
        return <Page title={t("newCase")} backHref="/rekrutacja" backLabel={t("title")}>
            <ApiErrorNotice error={districts.error}/>
        </Page>;
    }

    const targets = districts.data.filter(d => d.permissions.includes(PERMISSIONS.recruitmentManage));
    if (targets.length === 0) {
        return <Page title={t("newCase")} backHref="/rekrutacja" backLabel={t("title")}>
            <ApiErrorNotice error={{status: 403, code: "FORBIDDEN", message: ""}}/>
        </Page>;
    }

    return <Page title={t("newCase")} subtitle={t("newCaseHint")} backHref="/rekrutacja" backLabel={t("title")}>
        <ActionForm action={createRecruitmentCaseAction}
                    submitLabel={t("createCase")}
                    ariaLabel={t("newCase")}>
            <Card title={t("sectionDistrictAndSource")}>
                <SelectField name="districtBodyId"
                             label={t("district")}
                             required
                             emptyLabel={targets.length === 1 ? undefined : t("chooseDistrict")}
                             options={targets.map(d => ({value: String(d.id), label: d.name}))}/>
                <SelectField name="source"
                             label={t("source")}
                             required
                             emptyLabel={t("chooseSource")}
                             options={CASE_SOURCES.map(s => ({
                                 value: s,
                                 label: tEnums(`recruitmentSource.${s}`),
                             }))}/>
            </Card>
            <Card title={t("sectionCandidate")}>
                <TextField name="candidateFirstName" label={t("fields.firstName")} required/>
                <TextField name="candidateLastName" label={t("fields.lastName")} required/>
                <TextField name="candidateEmail" type="email" label={t("fields.email")}/>
                <TextField name="candidatePhone" type="tel" label={t("fields.phone")}/>
                <TextField name="candidateSocialProfileUrl" type="url" label={t("fields.profile")}/>
                <TextField name="candidateBirthDate" type="date" label={t("fields.birthDate")}
                           hint={t("fields.birthDateHint")}/>
            </Card>
        </ActionForm>
    </Page>;
}
