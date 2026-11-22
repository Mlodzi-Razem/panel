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

import React from "react";
import { useTranslations } from "next-intl";
import ActionForm from "@/components/form/ActionForm";
import { TextAreaField, TextField } from "@/components/form/Fields";
import styles from "@/components/recruitment/recruitment.module.scss";
import { recordCaseDecisionAction } from "@/app/actions/recruitment";
import { CaseDecision } from "@/panel-api/members/recruitment";
import { FormattedCaseDate } from "@/components/recruitment/format";

/**
 * The collegial resolutions of the admission: ZK approval (the global RECRUITMENT_APPROVE role records
 * it) and the ZO admission resolution (the district RECRUITMENT_MANAGE role records it, after the ZK
 * approval exists). Both are only writable in ZK_VOTING and never flip the status by themselves —
 * they are the conditions the ACCEPTED admission checks. The kind is pinned by which
 * form the operator is allowed to see; the deciding body is implied by the kind.
 */
export default function CaseDecisions({
    caseId,
    decisions,
    status,
    canManage,
    canApprove,
}: {
    caseId: number;
    decisions: CaseDecision[];
    status: string;
    canManage: boolean;
    canApprove: boolean;
}) {
    const t = useTranslations("recruitment");
    const tEnums = useTranslations("enums");

    const hasZkApproval = decisions.some(decision => decision.kind === "ZK_APPROVAL");
    const hasResolution = decisions.some(decision => decision.kind === "ZO_ADMISSION_RESOLUTION");
    const inVoting = status === "ZK_VOTING";

    return <div className={styles.decisions}>
        {decisions.length === 0 && <p className={styles.blockedReason}>{t("decisions.noneYet")}</p>}

        {decisions.length > 0 && <dl>
            {decisions.map(decision => <div key={decision.id}>
                <dt>{tEnums(`recruitmentDecisionKind.${decision.kind}`)}</dt>
                <dd>
                    {decision.decidingBodyName} · <FormattedCaseDate value={decision.decidedOn}/> · {" "}
                    {decision.decisionSignature}
                </dd>
            </div>)}
        </dl>}

        {inVoting && canApprove && !hasZkApproval && <ActionForm
            action={formData => recordCaseDecisionAction(caseId, formData)}
            submitLabel={t("decisions.recordZkApproval")}
            successMessage={t("decisions.recorded")}
        >
            <input type="hidden" name="kind" value="ZK_APPROVAL"/>
            <TextField name="decidedOn"
                       type="date"
                       label={t("decidedOn")}
                       required
                       hint={t("decisions.zkApprovalHint")}/>
            <TextField name="signature"
                       label={t("resolutionSignature")}
                       required
                       hint={t("resolutionSignatureHint")}/>
            <TextAreaField name="note"
                           label={t("resolutionNote")}
                           maxLength={2000}
                           hint={t("resolutionNoteHint")}/>
        </ActionForm>}

        {inVoting && canManage && !hasResolution && <ActionForm
            action={formData => recordCaseDecisionAction(caseId, formData)}
            submitLabel={t("decisions.recordZoResolution")}
            successMessage={t("decisions.recorded")}
        >
            <input type="hidden" name="kind" value="ZO_ADMISSION_RESOLUTION"/>
            <TextField name="decidedOn"
                       type="date"
                       label={t("decidedOn")}
                       required
                       hint={hasZkApproval
                           ? t("resolutionSignatureHint")
                           : t("decisions.resolutionNeedsZkFirst")}/>
            <TextField name="signature"
                       label={t("resolutionSignature")}
                       required
                       hint={t("resolutionSignatureHint")}/>
            <TextAreaField name="note"
                           label={t("resolutionNote")}
                           maxLength={2000}
                           hint={t("resolutionNoteHint")}/>
        </ActionForm>}

        {inVoting && !canApprove && !hasZkApproval && canManage && !hasResolution
            && <p className={styles.blockedReason}>{t("decisions.zkApprovalPending")}</p>}
    </div>;
}
