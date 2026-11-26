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

import React, { useState, useTransition } from "react";
import { useTranslations } from "next-intl";
import Button from "@/components/button/Button";
import formStyles from "@/components/form/Form.module.scss";
import pageStyles from "@/app/(public)/rekrutacja/deklaracja/[token]/DeclarationPage.module.scss";
import { renewIntakeFormTokenAction, submitIntakeAction } from "@/app/actions/intake";
import styles from "./JoinForm.module.scss";

const UNKNOWN_DISTRICT = "unknown";
/** A renewed stamp is only accepted after a few seconds; sending sooner would be mistaken for a robot. */
const RENEWAL_PAUSE_MS = 5000;
const REQUIRED = ["firstName", "lastName", "email", "birthDate", "district"] as const;

type TextName = "firstName" | "lastName" | "email" | "phone" | "birthDate" | "district" | "locality" | "website";

const EMPTY: Record<TextName, string> = {
    firstName: "", lastName: "", email: "", phone: "", birthDate: "", district: "", locality: "", website: "",
};

export default function JoinForm({districts, initialToken}: {
    districts: { id: number; name: string }[];
    initialToken: string;
}) {
    const t = useTranslations("intakeForm");
    const [values, setValues] = useState(EMPTY);
    const [consent, setConsent] = useState(false);
    const [token, setToken] = useState(initialToken);
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [notice, setNotice] = useState<string | null>(null);
    const [sent, setSent] = useState(false);
    const [paused, setPaused] = useState(false);
    const [pending, startTransition] = useTransition();

    if (sent) {
        return <section className={pageStyles.card} role="status">
            <h1 className={pageStyles.cardTitle}>{t("sentTitle")}</h1>
            <p className={pageStyles.lead}>{t("sentText")}</p>
        </section>;
    }

    const unknownDistrict = values.district === UNKNOWN_DISTRICT;

    const update = (name: TextName) =>
        (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
            const value = event.target.value;
            setValues(previous => ({...previous, [name]: value}));
            setFieldErrors(previous => ({...previous, [name]: ""}));
        };

    const validate = () => {
        const errors: Record<string, string> = {};
        for (const name of REQUIRED) {
            if (!values[name].trim()) {
                errors[name] = t("errors.required");
            }
        }
        if (unknownDistrict && !values.locality.trim()) {
            errors.locality = t("errors.required");
        }
        if (values.email.trim() && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(values.email.trim())) {
            errors.email = t("errors.email");
        }
        if (!consent) {
            errors.consent = t("errors.consent");
        }
        return errors;
    };

    const submit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const errors = validate();
        setFieldErrors(errors);
        if (Object.keys(errors).length > 0) {
            setNotice(t("errors.checkFields"));
            return;
        }
        setNotice(null);
        startTransition(async () => {
            const result = await submitIntakeAction({
                firstName: values.firstName,
                lastName: values.lastName,
                email: values.email,
                phone: values.phone,
                birthDate: values.birthDate,
                districtBodyId: unknownDistrict ? null : Number(values.district),
                locality: unknownDistrict ? values.locality : "",
                consent,
                formToken: token,
                website: values.website,
            });

            if (result.ok) {
                setSent(true);
                return;
            }
            if (result.code === "FORM_EXPIRED") {
                const renewed = await renewIntakeFormTokenAction();
                if (!renewed) {
                    setNotice(t("errors.reload"));
                    return;
                }
                setToken(renewed);
                setPaused(true);
                window.setTimeout(() => setPaused(false), RENEWAL_PAUSE_MS);
                setNotice(t("errors.resend"));
                return;
            }
            if (result.code === "ADMISSION_AGE") {
                setFieldErrors({birthDate: t("errors.age")});
                setNotice(t("errors.age"));
                return;
            }
            if (result.code === "INTAKE_BUSY") {
                setNotice(t("errors.busy"));
                return;
            }
            if (result.fields.length > 0) {
                const mapped: Record<string, string> = {};
                for (const field of result.fields) {
                    mapped[field === "districtBodyId" ? "district" : field] = t("errors.invalid");
                }
                setFieldErrors(mapped);
                setNotice(t("errors.checkFields"));
                return;
            }
            setNotice(t("errors.server"));
        });
    };

    const describedBy = (name: string, hint: boolean) =>
        [hint ? `join-${name}-hint` : "", fieldErrors[name] ? `join-${name}-error` : ""]
            .filter(Boolean).join(" ") || undefined;

    const error = (name: string) => fieldErrors[name]
        ? <span className={styles.fieldError} id={`join-${name}-error`}>{fieldErrors[name]}</span>
        : null;

    const input = (name: TextName, type: string, autoComplete: string, required: boolean, hint?: string) =>
        <div className={formStyles.field}>
            <label className={formStyles.label} htmlFor={`join-${name}`}>
                {t(`fields.${name}`)}{required && <span className={formStyles.required} aria-hidden="true"> *</span>}
            </label>
            <input id={`join-${name}`}
                   className={formStyles.input}
                   type={type}
                   autoComplete={autoComplete}
                   maxLength={name === "email" ? 254 : 100}
                   value={values[name]}
                   onChange={update(name)}
                   required={required}
                   aria-invalid={!!fieldErrors[name]}
                   aria-describedby={describedBy(name, !!hint)}/>
            {hint && <span className={formStyles.hint} id={`join-${name}-hint`}>{hint}</span>}
            {error(name)}
        </div>;

    return <form noValidate
                 className={`${pageStyles.card} ${formStyles.form} ${styles.fields}`}
                 aria-label={t("title")}
                 aria-busy={pending}
                 onSubmit={submit}>
        <h1 className={pageStyles.cardTitle}>{t("title")}</h1>
        <p className={pageStyles.lead}>{t("intro")}</p>
        <p className={pageStyles.cardHint}>{t("requiredHint")}</p>

        <div className={formStyles.fieldGrid}>
            {input("firstName", "text", "given-name", true)}
            {input("lastName", "text", "family-name", true)}
        </div>
        {input("email", "email", "email", true)}
        {input("phone", "tel", "tel", false)}
        {input("birthDate", "date", "bday", true, t("hints.birthDate"))}

        <div className={formStyles.field}>
            <label className={formStyles.label} htmlFor="join-district">
                {t("fields.district")}<span className={formStyles.required} aria-hidden="true"> *</span>
            </label>
            <select id="join-district"
                    className={formStyles.input}
                    value={values.district}
                    onChange={update("district")}
                    required
                    aria-invalid={!!fieldErrors.district}
                    aria-describedby={describedBy("district", true)}>
                <option value="">{t("chooseDistrict")}</option>
                {districts.map(d => <option key={d.id} value={String(d.id)}>{d.name}</option>)}
                <option value={UNKNOWN_DISTRICT}>{t("unknownDistrict")}</option>
            </select>
            <span className={formStyles.hint} id="join-district-hint">{t("hints.district")}</span>
            {error("district")}
        </div>
        {unknownDistrict && input("locality", "text", "address-level2", true)}

        <div className={styles.trap} aria-hidden="true">
            <label htmlFor="join-website">Website</label>
            <input id="join-website" type="text" tabIndex={-1} autoComplete="off"
                   value={values.website} onChange={update("website")}/>
        </div>

        <p className={styles.draft}>{t("notice.draft")}</p>
        <details className={styles.notice}>
            <summary>{t("notice.summary")}</summary>
            <p>{t("notice.administrator")}</p>
            <p>{t("notice.purpose")}</p>
            <p>{t("notice.access")}</p>
            <p>{t("notice.retention")}</p>
            <p>{t("notice.rights")}</p>
        </details>

        <div className={formStyles.field}>
            <label className={styles.consent}>
                <input type="checkbox"
                       checked={consent}
                       onChange={event => {
                           setConsent(event.target.checked);
                           setFieldErrors(previous => ({...previous, consent: ""}));
                       }}
                       required
                       aria-invalid={!!fieldErrors.consent}
                       aria-describedby={describedBy("consent", false)}/>
                <span>{t("consent")}<span className={formStyles.required} aria-hidden="true"> *</span></span>
            </label>
            {error("consent")}
        </div>

        <div aria-live="polite">
            {notice && <div className={formStyles.status} data-tone="error" role="alert">{notice}</div>}
        </div>
        <div className={formStyles.buttons}>
            <Button type="submit" variant="primary" disabled={pending || paused}>
                {pending ? t("sending") : t("send")}
            </Button>
        </div>
    </form>;
}
