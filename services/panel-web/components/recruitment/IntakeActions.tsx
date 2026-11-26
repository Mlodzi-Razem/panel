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

"use client";

import React from "react";
import { useTranslations } from "next-intl";
import ActionForm, { CANCELLED } from "@/components/form/ActionForm";
import { SelectField, SelectOption } from "@/components/form/Fields";
import {
    acceptIntakeAction,
    assignIntakeDistrictAction,
    discardIntakeAction,
    eraseIntakeAction,
} from "@/app/actions/intakes";
import { ActionResult } from "@/panel-api/members/types";

const cancelled = <T, >(): ActionResult<T> => ({ok: false, error: {status: 0, code: CANCELLED, message: ""}});

export function AcceptIntakeForm({id}: { id: number }) {
    const t = useTranslations("intakeQueue");
    return <ActionForm action={() => acceptIntakeAction(id)} submitLabel={t("accept")}/>;
}

export function AssignDistrictForm({id, districts, current}: {
    id: number;
    districts: SelectOption[];
    current?: number;
}) {
    const t = useTranslations("intakeQueue");
    return <ActionForm action={formData => assignIntakeDistrictAction(id, formData)}
                       submitLabel={t("assign")} submitVariant="outline">
        <SelectField name="districtBodyId" label={t("assignLabel")} required hint={t("assignHint")}
                     options={districts} emptyLabel={t("chooseDistrict")}
                     defaultValue={current ? String(current) : undefined}/>
    </ActionForm>;
}

export function DiscardIntakeForm({id, reasons}: { id: number; reasons: SelectOption[] }) {
    const t = useTranslations("intakeQueue");
    return <ActionForm action={async formData => window.confirm(t("discardConfirm"))
        ? discardIntakeAction(id, formData)
        : cancelled<null>()}
                       submitLabel={t("discard")} submitVariant="danger">
        <SelectField name="reason" label={t("discardReason")} required hint={t("discardHint")}
                     options={reasons} emptyLabel={t("chooseReason")}/>
    </ActionForm>;
}

export function EraseIntakeForm({id}: { id: number }) {
    const t = useTranslations("intakeQueue");
    return <ActionForm action={async () => window.confirm(t("eraseConfirm")) ? eraseIntakeAction(id) : cancelled<null>()}
                       submitLabel={t("erase")} submitVariant="danger"/>;
}
