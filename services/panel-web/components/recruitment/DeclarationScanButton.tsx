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

import React, { useId, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import Button from "@/components/button/Button";
import Dialog from "@/components/dialog/Dialog";
import ActionForm, { useFieldIds } from "@/components/form/ActionForm";
import { CheckboxField, TextField } from "@/components/form/Fields";
import formStyles from "@/components/form/Form.module.scss";
import { Muted } from "@/components/page/Page";
import { ActionResult, ApiError } from "@/panel-api/members/types";
import { isValidPesel } from "@/util/pesel";

const DATA_PARTS = [
    "formalFirstName", "formalLastName", "preferredFirstName", "preferredLastName", "pronouns",
    "personalId", "phone", "email", "imageConsent", "specialCategoryConsent",
] as const;

function failure(status: number, code: string, fields?: string[]): ActionResult {
    const error: ApiError = {status, code, message: code, ...(fields ? {fields} : {})};
    return {ok: false, error};
}

function FileField({name, label, hint}: { name: string; label: string; hint: string }) {
    const {id, hintId, invalid} = useFieldIds(name, true);
    return <div className={formStyles.field}>
        <label className={formStyles.label} htmlFor={id}>
            {label}<span className={formStyles.required} aria-hidden="true"> *</span>
        </label>
        <input id={id}
               className={formStyles.input}
               name={name}
               type="file"
               accept="application/pdf,.pdf"
               required
               aria-invalid={invalid}
               aria-describedby={hintId}/>
        <span className={formStyles.hint} id={hintId}>{hint}</span>
    </div>;
}

/**
 * "Upload the declaration scan" for a case awaiting the declaration (paper fallback). The
 * declaration data on the paper (formal names, PESEL, consents) goes along with the file when the
 * case has none yet; when the candidate already filled the e-form only the file is uploaded and
 * data changes go through the reasoned correction. The upload is a multipart POST to the BFF
 * route (a server action would cap the body at 1 MiB); the API enforces permission and rules.
 * Rendered only for RECRUITMENT_MANAGE in the case district — the page hides it otherwise.
 */
export default function DeclarationScanButton({caseId, formalIdentifierPresent}: {
    caseId: number;
    formalIdentifierPresent: boolean;
}) {
    const t = useTranslations("recruitment.scan");
    const router = useRouter();
    const [open, setOpen] = useState(false);
    const legendId = useId();

    const upload = async (formData: FormData): Promise<ActionResult> => {
        const file = formData.get("file");
        if (!(file instanceof File) || file.size === 0) {
            return failure(422, "FILE_MISSING", ["file"]);
        }

        const body = new FormData();
        body.set("file", file, file.name);

        if (!formalIdentifierPresent) {
            if (!isValidPesel(String(formData.get("personalId") ?? ""))) {
                return failure(422, "PERSONAL_ID_INVALID", ["personalId"]);
            }
            if (formData.get("specialCategoryConsent") !== "true") {
                return failure(422, "FIELD_REQUIRED", ["specialCategoryConsent"]);
            }
            for (const part of DATA_PARTS) {
                const value = formData.get(part);
                if (typeof value === "string" && value.trim().length > 0) {
                    body.set(part, value.trim());
                }
            }
        }

        let response: Response;
        try {
            response = await fetch(`/api/recruitment/cases/${caseId}/declaration-scan`, {method: "POST", body});
        } catch {
            return failure(503, "UNAVAILABLE");
        }

        if (response.ok) {
            return {ok: true};
        }
        try {
            const json = await response.json() as { error?: ApiError };
            if (json.error?.code) {
                return {ok: false, error: json.error};
            }
        } catch {
            // fall through to the generic failure
        }
        return failure(response.status, `HTTP_${response.status}`);
    };

    return <>
        <Button variant="outline" ariaLabel={t("openAria")} onClick={() => setOpen(true)}>
            {t("open")}
        </Button>
        <Dialog open={open} title={t("title")} onClose={() => setOpen(false)}>
            <ActionForm action={upload}
                        submitLabel={t("submit")}
                        ariaLabel={t("title")}
                        onSuccess={() => {
                            setOpen(false);
                            router.refresh();
                        }}
                        extraButtons={<Button variant="outline" onClick={() => setOpen(false)}>
                            {t("cancel")}
                        </Button>}>
                <p>{formalIdentifierPresent ? t("introDataPresent") : t("intro")}</p>
                <FileField name="file" label={t("file")} hint={t("fileHint")}/>

                {!formalIdentifierPresent && <>
                    <TextField name="formalFirstName" label={t("formalFirstName")} required autoComplete="off"/>
                    <TextField name="formalLastName" label={t("formalLastName")} required/>
                    <TextField name="personalId" label={t("personalId")} required maxLength={11}
                               hint={t("personalIdHint")}/>
                    <TextField name="preferredFirstName" label={t("preferredFirstName")}/>
                    <TextField name="preferredLastName" label={t("preferredLastName")}
                               hint={t("preferredHint")}/>
                    <TextField name="pronouns" label={t("pronouns")}/>
                    <TextField name="phone" type="tel" label={t("phone")}/>
                    <TextField name="email" type="email" label={t("email")}/>
                    <fieldset className={formStyles.field} aria-labelledby={legendId}>
                        <legend id={legendId} className={formStyles.label}>{t("consentsLegend")}</legend>
                        <Muted>{t("consentsHint")}</Muted>
                        <CheckboxField name="specialCategoryConsent" label={t("consentSpecial")}/>
                        <CheckboxField name="imageConsent" label={t("consentImage")}/>
                    </fieldset>
                </>}
            </ActionForm>
        </Dialog>
    </>;
}
