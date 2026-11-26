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

import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import DeclarationShell from "@/app/(public)/rekrutacja/deklaracja/[token]/DeclarationShell";
import styles from "@/app/(public)/rekrutacja/deklaracja/[token]/DeclarationPage.module.scss";
import { getIntakeForm } from "@/panel-api/recruitment/intakeForm";
import JoinForm from "./JoinForm";

/**
 * The public "I want to join" page. Anonymous; not indexed while Panel runs on the validation environment. The
 * association website links here: the page cannot be framed by other sites (CSP frame-ancestors).
 */
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
    const t = await getTranslations("intakeForm");
    return {title: t("metaTitle"), robots: {index: false, follow: false}};
}

export default async function JoinPage() {
    const t = await getTranslations("intakeForm");
    const form = await getIntakeForm();

    return <DeclarationShell>
        {form
            ? <JoinForm districts={form.districts} initialToken={form.formToken}/>
            : <section className={styles.card}>
                <h1 className={styles.cardTitle}>{t("unavailableTitle")}</h1>
                <p className={styles.lead}>{t("unavailableText")}</p>
            </section>}
    </DeclarationShell>;
}
