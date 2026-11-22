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
 * a one-off form URL with an expiry and e-mails it to the candidate when the case has an address
 * and mail is on (M5b). The dialog keeps the link visible and copyable and adds one status line
 * saying what happened to the e-mail; the address itself is never shown.
 */
// Rendered only for MANAGE in the case district — the page hides the button entirely without
// the permission (STATES 4.4), so the component itself stays permission-free.
const DISPATCH_KEYS = ["SCHEDULED", "DISABLED", "NO_EMAIL", "REFUSED_LIMIT"] as const;

/** REFUSED_SENDER, INVALID_RECIPIENT and anything unknown share the generic failure line. */
function dispatchKey(emailDispatch: string): (typeof DISPATCH_KEYS)[number] | "FAILED" {
    return (DISPATCH_KEYS as readonly string[]).includes(emailDispatch)
        ? emailDispatch as (typeof DISPATCH_KEYS)[number]
        : "FAILED";
}

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
                <p role="status" data-testid="declaration-link-dispatch">
                    {t(`declarationLink.emailDispatch.${dispatchKey(link.emailDispatch)}`)}
                </p>
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
