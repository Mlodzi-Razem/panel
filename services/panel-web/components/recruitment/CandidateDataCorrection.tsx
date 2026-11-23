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

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { correctCandidateDataAction } from "@/app/actions/recruitment";
import Button from "@/components/button/Button";
import Dialog from "@/components/dialog/Dialog";
import ActionForm from "@/components/form/ActionForm";
import { TextAreaField, TextField } from "@/components/form/Fields";

export type CandidateFormalData = {
    formalFirstName: string;
    formalLastName: string;
    preferredFirstName: string;
    preferredLastName: string;
    pronouns: string;
};

/**
 * "Correct the data": the reasoned correction of the candidate's formal data. History is never
 * overwritten silently — the API records the changed field names and the reason in the case
 * timeline. Rendered only for RECRUITMENT_MANAGE on an open case that already has formal data.
 * The PESEL is never served, so its field starts empty and is sent only when filled in.
 */
export default function CandidateDataCorrection({caseId, data}: { caseId: number; data: CandidateFormalData }) {
    const t = useTranslations("recruitment.correction");
    const router = useRouter();
    const [open, setOpen] = useState(false);

    return <>
        <Button variant="outline" ariaLabel={t("openAria")} onClick={() => setOpen(true)}>
            {t("open")}
        </Button>
        <Dialog open={open} title={t("title")} onClose={() => setOpen(false)}>
            <ActionForm action={formData => correctCandidateDataAction(caseId, formData)}
                        submitLabel={t("submit")}
                        ariaLabel={t("title")}
                        onSuccess={() => {
                            setOpen(false);
                            router.refresh();
                        }}
                        extraButtons={<Button variant="outline" onClick={() => setOpen(false)}>
                            {t("cancel")}
                        </Button>}>
                <p>{t("intro")}</p>
                <TextField name="formalFirstName" label={t("formalFirstName")} required
                           defaultValue={data.formalFirstName}/>
                <TextField name="formalLastName" label={t("formalLastName")} required
                           defaultValue={data.formalLastName}/>
                <TextField name="preferredFirstName" label={t("preferredFirstName")}
                           defaultValue={data.preferredFirstName}/>
                <TextField name="preferredLastName" label={t("preferredLastName")}
                           hint={t("preferredHint")}
                           defaultValue={data.preferredLastName}/>
                <TextField name="pronouns" label={t("pronouns")} defaultValue={data.pronouns}/>
                <TextField name="personalId" label={t("personalId")} maxLength={11}
                           hint={t("personalIdHint")}/>
                <TextAreaField name="reason" label={t("reason")} required maxLength={500}
                               hint={t("reasonHint")}/>
            </ActionForm>
        </Dialog>
    </>;
}
