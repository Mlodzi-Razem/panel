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
import ActionButton from "@/components/form/ActionButton";
import ActionForm from "@/components/form/ActionForm";
import Button from "@/components/button/Button";
import { SelectField, TextAreaField, TextField } from "@/components/form/Fields";
import styles from "@/components/recruitment/recruitment.module.scss";
import { transitionCaseAction, transitionCaseFormAction } from "@/app/actions/recruitment";
import { CaseTransition } from "@/panel-api/members/recruitment";

/** A body the decision forms offer as the deciding one: the ZO of the case district or the ZK. */
export type DecidingBodyOption = {
    id: number;
    name: string;
};

/**
 * The available moves, rendered exactly as GET /recruitment-cases/{id}/transitions serves them:
 * allowed edges become buttons, blocked edges stay visible as disabled with the server's own
 * blockedReason, and the post-declaration edges that carry a collegial resolution (PROBATION_EXTENDED,
 * ZO_RECOMMENDED, REJECTED after the declaration) ask for the deciding body, the resolution date
 * and the protocol signature. No local copy of the state map.
 */
export default function CaseTransitions({
    caseId,
    transitions,
    canManage,
    canApprove,
    postDeclaration,
    districtBoard,
    nationalBoard,
}: {
    caseId: number;
    transitions: CaseTransition[];
    canManage: boolean;
    canApprove: boolean;
    postDeclaration: boolean;
    districtBoard: DecidingBodyOption;
    nationalBoard: DecidingBodyOption | null;
}) {
    const t = useTranslations("recruitment");
    const tEnums = useTranslations("enums");
    const tErrors = useTranslations("errors");

    if (transitions.length === 0) {
        return <p className={styles.blockedReason}>{t("noTransitions")}</p>;
    }

    // The post-declaration cluster shares the MANAGE-in-district or the global APPROVE gate.
    const canDecide = canManage || canApprove;

    const decisionOptions: DecidingBodyOption[] = [districtBoard];
    if (nationalBoard) {
        decisionOptions.push(nationalBoard);
    }

    return <div className={styles.transitions}>
        {transitions.map(option => {
            const label = tEnums(`recruitmentStatus.${option.target}`);

            if (!option.allowed) {
                const hint = option.blockedReason && tErrors.has(option.blockedReason)
                    ? tErrors(option.blockedReason)
                    : t("blockedHint");
                return <div key={option.target} className={styles.transitionOption}>
                    <Button disabled aria-label={label}>{label}</Button>
                    <span className={styles.blockedReason}>{hint}</span>
                </div>;
            }

            // D11/STATES 4.4: an edge the caller has no role for is not rendered at all —
            // a disabled button would reveal that the action exists. Only the server's own
            // blockedReason (allowed:false) stays visible as disabled, because the refusal
            // belongs to the case state, not to the caller.
            if (!(postDeclaration ? canDecide : canManage)) {
                return null;
            }

            // Post-declaration rejections carry a collegial resolution like every other decision.
            const decisionBearing = postDeclaration
                && (option.target === "REJECTED" || option.target === "PROBATION_EXTENDED"
                    || option.target === "ZO_RECOMMENDED");

            if (option.target === "REJECTED") {
                return <div key={option.target} className={styles.transitionOption}>
                    <ActionForm action={formData => transitionCaseFormAction(caseId, formData)}
                                submitLabel={t("reject")}
                                submitVariant="outline"
                                ariaLabel={t("rejectAria", {status: label})}>
                        <input type="hidden" name="target" value="REJECTED"/>
                        <p className={styles.terminalWarning}>{t("transitionWarning")}</p>
                        <SelectField name="rejectionReason"
                                     label={t("rejectionReason")}
                                     required
                                     emptyLabel={t("chooseReason")}
                                     options={(option.rejectionReasons ?? []).map(reason => ({
                                         value: reason,
                                         label: tEnums(`recruitmentRejectionReason.${reason}`),
                                     }))}/>
                        {decisionBearing && <SelectField name="decidingBodyId"
                                             label={t("decidingBody")}
                                             required
                                             emptyLabel={t("chooseBody")}
                                             options={decisionOptions.map(body => ({
                                                 value: String(body.id),
                                                 label: body.name,
                                             }))}/>}
                        {decisionBearing && <TextField name="decidedOn"
                                             type="date"
                                             label={t("decidedOn")}
                                             required/>}
                        {decisionBearing && <TextField name="signature"
                                             label={t("resolutionSignature")}
                                             required
                                             hint={t("resolutionSignatureHint")}/>}
                        {decisionBearing && <TextAreaField name="note"
                                             label={t("resolutionNote")}
                                             maxLength={2000}
                                             hint={t("resolutionNoteHint")}/>}
                    </ActionForm>
                </div>;
            }

            if (decisionBearing) {
                return <div key={option.target} className={styles.transitionOption}>
                    <ActionForm action={formData => transitionCaseFormAction(caseId, formData)}
                                submitLabel={label}
                                ariaLabel={label}>
                        <input type="hidden" name="target" value={option.target}/>
                        <SelectField name="decidingBodyId"
                                     label={t("decidingBody")}
                                     required
                                     emptyLabel={t("chooseBody")}
                                     options={decisionOptions
                                         // ZO_RECOMMENDED and PROBATION_EXTENDED are the ZO assessment;
                                         // the engine rejects any other deciding body (DECIDING_BODY_NOT_ZO).
                                         .filter(body => body.id === districtBoard.id)
                                         .map(body => ({value: String(body.id), label: body.name}))}/>
                        <TextField name="decidedOn"
                                   type="date"
                                   label={t("decidedOn")}
                                   required
                                   defaultValue={""}/>
                        <TextField name="signature"
                                   label={t("resolutionSignature")}
                                   required
                                   hint={t("resolutionSignatureHint")}/>
                        <TextAreaField name="note"
                                       label={t("resolutionNote")}
                                       maxLength={2000}
                                       hint={t("resolutionNoteHint")}/>
                    </ActionForm>
                </div>;
            }

            return <div key={option.target} className={styles.transitionOption}>
                <ActionButton action={() => transitionCaseAction(caseId, option.target)}
                              label={label}
                              confirmMessage={option.target === "ACCEPTED"
                                  ? t("confirmTransition", {status: label})
                                  : undefined}/>
            </div>;
        })}
    </div>;
}
