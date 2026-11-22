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
import {useTranslations} from "next-intl";
import {CheckCircleIcon, WarningCircleIcon} from "@phosphor-icons/react/ssr";
import styles from "./DeclarationPage.module.scss";

/**
 * Diagnostic failure kinds of the contract (404 / TOKEN_EXPIRED / 409 / transport). They stay a
 * server-side and BFF concept only — the presentation NEVER distinguishes them (anti-enumeration,
 * review F1/F8/H4): every dead link renders the same single screen. Kept as a type for prop
 * signatures of the step components that receive the mapped kind.
 */
export type DeclarationStatusKind = "unavailable" | "expired" | "closed" | "error";

/** THE terminal screen for every dead link — identical DOM and copy for any reason. */
export function LinkUnavailableScreen() {
    const t = useTranslations("recruitmentDeclaration.status");
    return <section className={styles.card}>
        <div className={styles.statusBlock}>
            <span className={`${styles.statusIcon} ${styles.statusIconDanger}`} aria-hidden>
                <WarningCircleIcon weight="fill"/>
            </span>
            <h1 className={styles.statusTitle}>{t("linkTitle")}</h1>
            <p className={styles.statusBody}>{t("linkBody")}</p>
            <p className={styles.statusBody}>{t("contactHint")}</p>
        </div>
    </section>;
}

/** SIGNED status: declaration complete. Deliberately generic — admission-clock details stay internal (ZO/ZK). */
export function FinalScreen() {
    const t = useTranslations("recruitmentDeclaration.final");
    const tContact = useTranslations("recruitmentDeclaration.status");
    return <section className={styles.card}>
        <div className={styles.statusBlock}>
            <span className={`${styles.statusIcon} ${styles.statusIconSuccess}`} aria-hidden>
                <CheckCircleIcon weight="fill"/>
            </span>
            <h1 className={styles.statusTitle}>{t("title")}</h1>
            <p className={styles.statusBody}>{t("lead")}</p>
            <h2 className={styles.cardTitle}>{t("probationTitle")}</h2>
            <p className={styles.statusBody}>{t("probationBody")}</p>
            <p className={styles.statusBody}>{tContact("contactHint")}</p>
        </div>
    </section>;
}
