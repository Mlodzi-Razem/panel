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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

"use client";

import React, { useState } from "react";
import { useTranslations } from "next-intl";
import ActionButton from "@/components/form/ActionButton";
import Button from "@/components/button/Button";
import Dialog from "@/components/dialog/Dialog";
import { Muted } from "@/components/page/Page";
import styles from "@/components/recruitment/recruitment.module.scss";
import { generateDeclarationLinkAction } from "@/app/actions/recruitment";
import { DeclarationLink } from "@/panel-api/members/recruitment";
import { FormattedCaseDate } from "@/components/recruitment/format";

/**
 * "Generate the declaration link" for a case awaiting the declaration. The M4a endpoint mints
 * a one-off form URL with an expiry; the dialog only shows it with a copy affordance — the back
 * office sends it to the candidate over their own channel, the panel never sends anything itself.
 * Until the M4a milestone lands the API answers this POST with an error; it is surfaced as-is.
 */
// Rendered only for MANAGE in the case district — the page hides the button entirely without
// the permission (STATES 4.4), so the component itself stays permission-free.
export default function DeclarationLinkButton({ caseId }: { caseId: number }) {
    const t = useTranslations("recruitment");
    const [link, setLink] = useState<DeclarationLink | null>(null);
    const [copied, setCopied] = useState(false);

    const copy = async () => {
        if (!link) {
            return;
        }
        await navigator.clipboard.writeText(link.formUrl);
        setCopied(true);
    };

    return <>
        <ActionButton<DeclarationLink>
            action={() => generateDeclarationLinkAction(caseId)}
            label={t("declarationLink.generate")}
            variant="outline"
            ariaLabel={t("declarationLink.generateAria")}
            onSuccess={outcome => {
                if (outcome.data) {
                    setCopied(false);
                    setLink(outcome.data);
                }
            }}
        />
        <Dialog open={link !== null} title={t("declarationLink.title")}
                onClose={() => setLink(null)}>
            {link && <div className={styles.declarationLink}>
                <p>{t("declarationLink.intro")}</p>
                <p className={styles.formUrl}>{link.formUrl}</p>
                <p>
                    <Muted>{t("declarationLink.expiresAt")} <FormattedCaseDate value={link.expiresAt}/></Muted>
                </p>
                <div className={styles.dialogActions}>
                    <Button onClick={copy}>{t("declarationLink.copy")}</Button>
                    <Button variant="outline" onClick={() => setLink(null)}>{t("declarationLink.close")}</Button>
                    {copied && <Muted>{t("declarationLink.copied")}</Muted>}
                </div>
            </div>}
        </Dialog>
    </>;
}
