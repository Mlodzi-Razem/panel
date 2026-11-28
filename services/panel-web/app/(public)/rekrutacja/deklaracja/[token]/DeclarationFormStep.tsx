"use client";

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

import React, {useId, useState, useTransition} from "react";
import {useLocale, useTranslations} from "next-intl";
import {ArrowRightIcon} from "@phosphor-icons/react/ssr";
import {submitDeclarationFormAction} from "@/app/actions/recruitment";
import Button from "@/components/button/Button";
import {Notice} from "@/components/page/Page";
import {isValidPesel, peselBirthDate} from "@/util/pesel";
import {DeclarationStatusKind} from "./DeclarationScreens";
import formStyles from "@/components/form/Form.module.scss";
import styles from "./DeclarationPage.module.scss";

type TextFieldName =
    | "formalFirstName"
    | "formalLastName"
    | "preferredFirstName"
    | "preferredLastName"
    | "personalIdNumber"
    | "pronouns"
    | "phone"
    | "email";

const EMPTY_VALUES: Record<TextFieldName, string> = {
    formalFirstName: "",
    formalLastName: "",
    preferredFirstName: "",
    preferredLastName: "",
    personalIdNumber: "",
    pronouns: "",
    phone: "",
    email: "",
};

/**
 * Step 2: the declaration itself, laid out like the paper form (sections A and B.7).
 * Client-side gate: required names, the PESEL checksum and the two required confirmations
 * must pass before the server action is called; panel-api (M4a) re-validates and owns
 * the domain rules (admission age, ban list, consent consistency).
 */
export default function DeclarationFormStep({token, onSubmitted, onFatal}: {
    token: string;
    onSubmitted: () => void;
    onFatal: (kind: DeclarationStatusKind) => void;
}) {
    const t = useTranslations("recruitmentDeclaration");
    const tErrors = useTranslations("recruitmentDeclaration.errors");
    const locale = useLocale();
    const [values, setValues] = useState<Record<TextFieldName, string>>(EMPTY_VALUES);
    const [consentImage, setConsentImage] = useState(false);
    const [consentSpecialCategory, setConsentSpecialCategory] = useState(false);
    const [infoDutyAck, setInfoDutyAck] = useState(false);
    const [birthDateConfirmed, setBirthDateConfirmed] = useState(false);
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [notice, setNotice] = useState<string | null>(null);
    const [pending, startTransition] = useTransition();

    const birthDate = isValidPesel(values.personalIdNumber)
        ? peselBirthDate(values.personalIdNumber)
        : null;

    const update = (name: TextFieldName) => (event: React.ChangeEvent<HTMLInputElement>) => {
        setValues(previous => ({...previous, [name]: event.target.value}));
        setFieldErrors(previous => {
            const next = {...previous};
            delete next[name];
            return next;
        });
    };

    function validate(): Record<string, string> {
        const errors: Record<string, string> = {};
        if (values.formalFirstName.trim().length === 0) {
            errors.formalFirstName = tErrors("required");
        }
        if (values.formalLastName.trim().length === 0) {
            errors.formalLastName = tErrors("required");
        }
        if (!/^\d{11}$/.test(values.personalIdNumber.trim())) {
            errors.personalIdNumber = tErrors("peselFormat");
        } else if (!isValidPesel(values.personalIdNumber.trim())) {
            errors.personalIdNumber = tErrors("peselChecksum");
        }
        const email = values.email.trim();
        if (email.length > 0 && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
            errors.email = tErrors("emailFormat");
        }
        if (!consentSpecialCategory) {
            errors.consentSpecialCategory = tErrors("consentSpecial");
        }
        if (!infoDutyAck) {
            errors.infoDutyAck = tErrors("infoDutyAck");
        }
        if (!birthDateConfirmed) {
            errors.birthDateConfirmation = tErrors("birthDateConfirmation");
        }
        return errors;
    }

    const submit = () => {
        const clientSideErrors = validate();
        if (Object.keys(clientSideErrors).length > 0) {
            setFieldErrors(clientSideErrors);
            setNotice(tErrors("checkFields"));
            return;
        }
        startTransition(async () => {
            const optional = (value: string) => {
                const trimmed = value.trim();
                return trimmed.length > 0 ? trimmed : undefined;
            };
            // Wire keys are the contract's canonical names (panel-api DeclarationFormRequest);
            // the birth-date confirmation stays a local UX gate and is never sent.
            const result = await submitDeclarationFormAction(token, {
                formalFirstName: values.formalFirstName.trim(),
                formalLastName: values.formalLastName.trim(),
                preferredFirstName: optional(values.preferredFirstName),
                preferredLastName: optional(values.preferredLastName),
                personalId: values.personalIdNumber.trim(),
                pronouns: optional(values.pronouns),
                phone: optional(values.phone),
                email: optional(values.email),
                imageConsent: consentImage,
                specialCategoryConsent: consentSpecialCategory,
                infoDutyAcknowledged: infoDutyAck,
            });

            if (result.ok) {
                onSubmitted();
                return;
            }
            const error = result.error;
            if (error.code === "TOKEN_EXPIRED") {
                onFatal("expired");
                return;
            }
            if (error.code === "CASE_CLOSED" || error.status === 409) {
                onFatal("closed");
                return;
            }
            if (error.status === 404 || error.code === "LINK_UNAVAILABLE") {
                onFatal("unavailable");
                return;
            }
            if (error.code === "ADMISSION_AGE") {
                setNotice(tErrors("admissionAge"));
                return;
            }
            if (error.fields && error.fields.length > 0) {
                const mapped: Record<string, string> = {};
                for (const field of error.fields) {
                    const local = localFieldOf(field);
                    mapped[local] = fieldMessage(error.code, field);
                }
                setFieldErrors(previous => ({...mapped, ...previous }));
                setNotice(tErrors("checkFields"));
                return;
            }
            setNotice(tErrors.has(error.code) ? tErrors(error.code) : tErrors("server"));
        });
    };

    /** Server fields are canonical wire names; the form's local ids differ for two of them. */
    function localFieldOf(serverField: string): string {
        if (serverField === "personalId") {
            return "personalIdNumber";
        }
        if (serverField === "specialCategoryConsent") {
            return "consentSpecialCategory";
        }
        if (serverField === "infoDutyAcknowledged") {
            return "infoDutyAck";
        }
        return serverField;
    }

    function fieldMessage(code: string, field: string): string {
        if (code === "PERSONAL_ID_INVALID" && (field === "personalId" || field === "personalIdNumber")) {
            return tErrors("peselChecksum");
        }
        if (code === "FIELD_REQUIRED" && field === "infoDutyAcknowledged") {
            return tErrors("infoDutyAck");
        }
        return code === "FIELD_REQUIRED" ? tErrors("required") : tErrors("server");
    }

    return <form noValidate
                 className={`${styles.card} ${styles.formCard} ${formStyles.form}`}
                 aria-label={t("form.formAriaLabel")}
                 aria-busy={pending}
                 onSubmit={event => event.preventDefault()}>
        <h1 className={styles.cardTitle}>{t("form.title")}</h1>

        <h2 className={styles.sectionTitle}>{t("form.officialTitle")}</h2>
        <div className={styles.formGrid}>
            <Field name="formalFirstName"
                   required
                   label={t("form.formalFirstName")}
                   value={values.formalFirstName}
                   onChange={update("formalFirstName")}
                   error={fieldErrors.formalFirstName}
                   hint={t("form.officialHint")}
                   autoComplete="given-name"/>
            <Field name="formalLastName"
                   required
                   label={t("form.formalLastName")}
                   value={values.formalLastName}
                   onChange={update("formalLastName")}
                   error={fieldErrors.formalLastName}
                   autoComplete="family-name"/>
        </div>

        <h2 className={styles.sectionTitle}>{t("form.preferredTitle")}</h2>
        <p className={styles.cardHint}>{t("form.preferredHint")}</p>
        <div className={styles.formGrid}>
            <Field name="preferredFirstName"
                   label={t("form.preferredFirstName")}
                   value={values.preferredFirstName}
                   onChange={update("preferredFirstName")}
                   error={fieldErrors.preferredFirstName}/>
            <Field name="preferredLastName"
                   label={t("form.preferredLastName")}
                   value={values.preferredLastName}
                   onChange={update("preferredLastName")}
                   error={fieldErrors.preferredLastName}/>
        </div>

        <h2 className={styles.sectionTitle}>{t("form.peselTitle")}</h2>
        <div className={styles.formGrid}>
            <Field name="personalIdNumber"
                   required
                   mono
                   label={t("form.personalIdNumberLabel")}
                   value={values.personalIdNumber}
                   onChange={update("personalIdNumber")}
                   error={fieldErrors.personalIdNumber}
                   hint={t("form.peselHint")}
                   inputMode="numeric"
                   maxLength={11}/>
        </div>
        {birthDate && <p className={styles.cardHint}>
            {t("form.birthDateLabel", {date: new Intl.DateTimeFormat(locale, {dateStyle: "long", timeZone: "UTC"}).format(birthDate)})}
        </p>}
        <fieldset className={styles.checkboxRow}>
            <input type="checkbox"
                   id="declaration-birth-date-confirmation"
                   className={styles.checkbox}
                   required
                   aria-required="true"
                   aria-invalid={fieldErrors.birthDateConfirmation ? true : undefined}
                   aria-describedby={fieldErrors.birthDateConfirmation
                       ? "declaration-birth-date-confirmation-hint declaration-birth-date-confirmation-error"
                       : "declaration-birth-date-confirmation-hint"}
                   checked={birthDateConfirmed}
                   onChange={event => setBirthDateConfirmed(event.target.checked)}/>
            <label className={styles.checkboxLabel} htmlFor="declaration-birth-date-confirmation">
                {t("form.birthDateConfirmationLabel")}
                <span id="declaration-birth-date-confirmation-hint" className={styles.checkboxHint}>
                    {t("form.birthDateConfirmationHint")}
                </span>
            </label>
        </fieldset>
        {fieldErrors.birthDateConfirmation && <p id="declaration-birth-date-confirmation-error"
                                                 className={styles.checkboxError}>
            {fieldErrors.birthDateConfirmation}
        </p>}

        <h2 className={styles.sectionTitle}>{t("form.pronounsTitle")}</h2>
        <div className={styles.formGrid}>
            <Field name="pronouns"
                   label={t("form.pronounsLabel")}
                   value={values.pronouns}
                   onChange={update("pronouns")}
                   hint={t("form.pronounsHint")}/>
        </div>

        <h2 className={styles.sectionTitle}>{t("form.phoneTitle")}</h2>
        <div className={styles.formGrid}>
            <Field name="phone"
                   label={t("form.phoneLabel")}
                   value={values.phone}
                   onChange={update("phone")}
                   type="tel"
                   autoComplete="tel"
                   hint={t("form.phoneHint")}/>
            <Field name="email"
                   label={t("form.emailLabel")}
                   value={values.email}
                   onChange={update("email")}
                   error={fieldErrors.email}
                   type="email"
                   autoComplete="email"
                   hint={t("form.emailHint")}/>
        </div>

        <h2 className={styles.sectionTitle}>{t("form.oathTitle")}</h2>
        <div className={styles.oath}>
            <p className={styles.cardHint}>{t("form.oathIntro")}</p>
            <ul className={styles.oathList}>
                <li>{t("form.oathItem1")}</li>
                <li>{t("form.oathItem2")}</li>
                <li>{t("form.oathItem3")}</li>
                <li>{t("form.oathItem4")}</li>
            </ul>
        </div>

        <h2 className={styles.sectionTitle}>{t("form.consentsTitle")}</h2>
        <p className={styles.cardHint}>{t("form.consentsIntro")}</p>

        <fieldset className={styles.checkboxRow}>
            <input type="checkbox"
                   id="declaration-consent-special"
                   className={styles.checkbox}
                   required
                   aria-required="true"
                   aria-invalid={fieldErrors.consentSpecialCategory ? true : undefined}
                   aria-describedby={fieldErrors.consentSpecialCategory
                       ? "declaration-consent-special-hint declaration-consent-special-error"
                       : "declaration-consent-special-hint"}
                   checked={consentSpecialCategory}
                   onChange={event => setConsentSpecialCategory(event.target.checked)}/>
            <label className={styles.checkboxLabel} htmlFor="declaration-consent-special">
                {t("form.consentSpecialLabel")}
                <span id="declaration-consent-special-hint" className={styles.checkboxHint}>
                    {t("form.consentSpecialHint")}
                </span>
            </label>
        </fieldset>
        {fieldErrors.consentSpecialCategory && <p id="declaration-consent-special-error"
                                                  className={styles.checkboxError}>
            {fieldErrors.consentSpecialCategory}
        </p>}

        <fieldset className={styles.checkboxRow}>
            <input type="checkbox"
                   id="declaration-consent-image"
                   className={styles.checkbox}
                   checked={consentImage}
                   onChange={event => setConsentImage(event.target.checked)}
                   aria-describedby="declaration-consent-image-hint"/>
            <label className={styles.checkboxLabel} htmlFor="declaration-consent-image">
                {t("form.consentImageLabel")}
                <span id="declaration-consent-image-hint" className={styles.checkboxHint}>
                    {t("form.consentImageHint")}
                </span>
            </label>
        </fieldset>

        <p className={styles.cardHint}>{t("form.obligationNote")}</p>

        <details className={styles.infoDuty}>
            <summary>{t("form.infoDutySummary")}</summary>
            <p className={styles.cardHint}>{t("form.infoDutyAdministrator")}</p>
            <ul className={styles.infoDutyList}>
                <li>{t("form.infoDutyLegalBases")}</li>
                <li>{t("form.infoDutyPurposeMembership")}</li>
                <li>{t("form.infoDutyPurposeFees")}</li>
                <li>{t("form.infoDutyPurposeClaims")}</li>
                <li>{t("form.infoDutyPurposeImage")}</li>
                <li>{t("form.infoDutyRecipients")}</li>
                <li>{t("form.infoDutyRights")}</li>
                <li>{t("form.infoDutyNoAutomated")}</li>
            </ul>
        </details>

        <fieldset className={styles.checkboxRow}>
            <input type="checkbox"
                   id="declaration-info-duty-ack"
                   className={styles.checkbox}
                   required
                   aria-required="true"
                   aria-invalid={fieldErrors.infoDutyAck ? true : undefined}
                   aria-describedby={fieldErrors.infoDutyAck
                       ? "declaration-info-duty-ack-hint declaration-info-duty-ack-error"
                       : "declaration-info-duty-ack-hint"}
                   checked={infoDutyAck}
                   onChange={event => setInfoDutyAck(event.target.checked)}/>
            <label className={styles.checkboxLabel} htmlFor="declaration-info-duty-ack">
                {t("form.infoDutyAckLabel")}
                <span id="declaration-info-duty-ack-hint" className={styles.checkboxHint}>
                    {t("form.infoDutyAckHint")}
                </span>
            </label>
        </fieldset>
        {fieldErrors.infoDutyAck && <p id="declaration-info-duty-ack-error"
                                      className={styles.checkboxError}>
            {fieldErrors.infoDutyAck}
        </p>}

        {notice && <Notice tone="danger" role="alert"><p>{notice}</p></Notice>}

        <div className={styles.actions}>
            <Button type="submit"
                    iconRight={<ArrowRightIcon aria-hidden/>}
                    disabled={pending}
                    onClick={submit}>
                {t("form.submitLabel")}
            </Button>
            {pending && <span className={styles.progressStatus}>{t("form.pendingLabel")}</span>}
        </div>
        <p className={styles.cardHint}>{t("form.httpsNote")}</p>
    </form>;
}

function Field({name, label, required, mono, value, onChange, error, hint, type, inputMode, maxLength, autoComplete}: {
    name: TextFieldName;
    label: string;
    required?: boolean;
    mono?: boolean;
    value: string;
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
    error?: string;
    hint?: string;
    type?: string;
    inputMode?: "numeric";
    maxLength?: number;
    autoComplete?: string;
}) {
    const [baseId] = useId().split("-");
    const id = `declaration-${name}-${baseId}`;
    const describedBy = [
        hint ? `${id}-hint` : null,
        error ? `${id}-error` : null,
    ].filter(Boolean).join(" ") || undefined;

    return <div className={formStyles.field}>
        <label className={formStyles.label} htmlFor={id}>
            {label}
            {required && <span className={formStyles.required} aria-hidden="true"> *</span>}
        </label>
        <input id={id}
               name={name}
               className={mono ? `${formStyles.input} ${styles.monoInput}` : formStyles.input}
               type={type ?? "text"}
               inputMode={inputMode}
               maxLength={maxLength}
               autoComplete={autoComplete ?? "off"}
               value={value}
               onChange={onChange}
               required={required}
               aria-required={required ? "true" : undefined}
               aria-invalid={error ? true : undefined}
               aria-describedby={describedBy}/>
        {error && <p className={styles.fieldError} id={`${id}-error`}>{error}</p>}
        {hint && <p className={formStyles.hint} id={`${id}-hint`}>{hint}</p>}
    </div>;
}
