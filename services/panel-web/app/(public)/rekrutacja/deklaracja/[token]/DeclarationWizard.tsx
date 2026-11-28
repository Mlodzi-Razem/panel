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

import React, {useState} from "react";
import {useLocale, useTranslations} from "next-intl";
import {ArrowRightIcon} from "@phosphor-icons/react/ssr";
import Button from "@/components/button/Button";
import DeclarationFormStep from "./DeclarationFormStep";
import DeclarationUploadStep from "./DeclarationUploadStep";
// Mid-flow failures carry a diagnostic kind in the steps' onFatal, but the wizard renders ONE
// flattened dead-link screen for all of them (anti-enumeration, review F1/F8/H4).
import {LinkUnavailableScreen} from "./DeclarationScreens";
import styles from "./DeclarationPage.module.scss";

export type WizardStep = "landing" | "form" | "upload";

const STEP_ORDER: readonly WizardStep[] = ["landing", "form", "upload"];

/**
 * The candidate's multi-step flow behind a magic link (RODO ladder R0): no sign-in, no account —
 * the token in the URL is the whole credential. The wizard holds the step state client-side and
 * falls back to the terminal screens the server renders when the link cannot be used at all.
 */
export default function DeclarationWizard({token, expiresAt, initialStep}: {
    token: string;
    expiresAt: string;
    initialStep: WizardStep;
}) {
    const t = useTranslations("recruitmentDeclaration");
    const locale = useLocale();
    const [step, setStep] = useState<WizardStep>(initialStep);
    const [linkDead, setLinkDead] = useState(false);

    const expiresLabel = formatDateTime(expiresAt, locale);
    const currentIndex = STEP_ORDER.indexOf(step);

    // Anti-enumeration (review F1/F8/H4): every terminal link state renders the SAME screen.
    if (linkDead) {
        return <LinkUnavailableScreen/>;
    }

    return <>
        <ol className={styles.stepper} aria-label={t("stepper.label")}>
            {STEP_ORDER.map((entry, index) => <li key={entry}
                                                 className={`${styles.step} ${index === currentIndex ? styles.stepCurrent : ""} ${index < currentIndex ? styles.stepDone : ""}`}
                                                 aria-current={index === currentIndex ? "step" : undefined}>
                <span className={styles.stepNumber} aria-hidden>{index + 1}</span>
                {t(`stepper.${entry}`)}
            </li>)}
        </ol>

        {step === "landing" && <section className={styles.card}>
            <div className={styles.statusBlock}>
                <h1 className={styles.greeting}>{t("landing.greetingTitle")}</h1>
                <p className={styles.lead}>{t("landing.lead")}</p>
                <p className={styles.cardHint}>
                    {t("landing.expiryLabel")}
                    {expiresLabel && <strong> {expiresLabel}</strong>}
                </p>
                <p className={styles.cardHint}>{t("landing.deviceHint")}</p>
                <div className={styles.actions}>
                    <Button iconRight={<ArrowRightIcon aria-hidden/>} onClick={() => setStep("form")}>
                        {t("landing.startLabel")}
                    </Button>
                </div>
            </div>
        </section>}

        {step === "form" && <DeclarationFormStep token={token} onSubmitted={() => setStep("upload")} onFatal={() => setLinkDead(true)}/>}

        {step === "upload" && <DeclarationUploadStep token={token} onFatal={() => setLinkDead(true)}/>}
    </>;
}

function formatDateTime(iso: string, locale: string): string {
    try {
        const date = new Date(iso);
        if (Number.isNaN(date.getTime())) {
            return "";
        }
        // Fixed zone: this client component is also rendered on the server (UTC on stage); formatting in the
        // runtime's own zone made the two renders differ and broke hydration (minified React error #418).
        return new Intl.DateTimeFormat(locale, {dateStyle: "medium", timeStyle: "short", timeZone: "Europe/Warsaw"}).format(date);
    } catch {
        return "";
    }
}
