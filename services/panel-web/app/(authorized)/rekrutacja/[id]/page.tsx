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

import React from "react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { updateRecruitmentCaseAction, sendCaseReminderAction, addCaseNoteAction } from "@/app/actions/recruitment";
import ActionButton from "@/components/form/ActionButton";
import ActionForm from "@/components/form/ActionForm";
import { TextAreaField, TextField } from "@/components/form/Fields";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import CaseClocks from "@/components/recruitment/CaseClocks";
import CaseDecisions from "@/components/recruitment/CaseDecisions";
import CaseTimeline from "@/components/recruitment/CaseTimeline";
import CaseTransitions, { DecidingBodyOption } from "@/components/recruitment/CaseTransitions";
import DeclarationLinkButton from "@/components/recruitment/DeclarationLinkButton";
import { candidateName, caseStatusTone, FormattedCaseDate } from "@/components/recruitment/format";
import styles from "@/components/recruitment/recruitment.module.scss";
import {
    Badge,
    Card,
    DescriptionList,
    Muted,
    Notice,
    Page,
} from "@/components/page/Page";
import { requireMe } from "@/panel-api/members/requireMe";
import { getCaseTransitions, getRecruitmentCase } from "@/panel-api/members/recruitment";
import { listBodies } from "@/panel-api/members/bodies";
import { PERMISSIONS } from "@/panel-api/members/types";

/** The statuses whose edges belong to the M3 engine — decision input appears from here on. */
const POST_DECLARATION_STATUSES = [
    'DECLARATION_RECEIVED', 'PROBATION', 'PROBATION_EXTENDED', 'ZO_RECOMMENDED', 'ZK_VOTING', 'ACCEPTED', 'REJECTED',
];

export default async function RecruitmentCasePage({params}: { params: Promise<{ id: string }> }) {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const {id} = await params;
    const caseId = Number.parseInt(id, 10);
    const t = await getTranslations("recruitment");
    const tEnums = await getTranslations("enums");

    const detail = await getRecruitmentCase(caseId);
    if (!detail.ok) {
        return <Page title={t("caseTitle", {id})} backHref="/rekrutacja" backLabel={t("title")}>
            <ApiErrorNotice error={detail.error}/>
        </Page>;
    }

    const candidateCase = detail.data;
    // the API omits null fields (NON_NULL), so an open case has no closedAt at all
    const open = candidateCase.closedAt == null;
    const postDeclaration = POST_DECLARATION_STATUSES.includes(candidateCase.status);
    const transitionsResult = await getCaseTransitions(caseId);
    const bodiesResult = await listBodies();

    const canManage = me.permissions.perDistrict
        .some(d => d.districtId === candidateCase.district.id
            && d.permissions.includes(PERMISSIONS.recruitmentManage));
    const canApprove = open && me.permissions.global.includes(PERMISSIONS.recruitmentApprove);

    // The deciding bodies the decision forms offer: the ZO of the case district (assessment,
    // resolution) and the ZK (national approvals); any other body would fail the engine checks.
    let districtBoard: DecidingBodyOption | null = null;
    let nationalBoard: DecidingBodyOption | null = null;
    if (bodiesResult.ok) {
        districtBoard = bodiesResult.data
            .find(body => body.kind === "DISTRICT_BOARD" && body.parentBodyId === candidateCase.district.id)
            ?? null;
        nationalBoard = bodiesResult.data.find(body => body.kind === "NATIONAL_BOARD") ?? null;
    }

    return <Page title={candidateName(candidateCase) || t("caseTitle", {id})}
                 backHref="/rekrutacja"
                 backLabel={t("title")}>
        <Card id="przeglad">
            <div className={styles.actions}>
                <Badge tone={caseStatusTone(candidateCase.status)}>
                    {tEnums(`recruitmentStatus.${candidateCase.status}`)}
                </Badge>
                <Badge tone="neutral">{tEnums(`recruitmentSource.${candidateCase.source}`)}</Badge>
                {candidateCase.rejectionReason && <Badge tone="danger">
                    {tEnums(`recruitmentRejectionReason.${candidateCase.rejectionReason}`)}
                </Badge>}
                {!open && <Muted>{t("closedOn")} <FormattedCaseDate value={candidateCase.closedAt}/></Muted>}
            </div>
            <DescriptionList>
                <div><dt>{t("fields.district")}</dt><dd>{candidateCase.district.name}</dd></div>
                <div><dt>{t("fields.email")}</dt><dd>{candidateCase.candidateEmail ?? "—"}</dd></div>
                <div><dt>{t("fields.phone")}</dt><dd>{candidateCase.candidatePhone ?? "—"}</dd></div>
                <div><dt>{t("fields.profile")}</dt><dd>{candidateCase.candidateSocialProfileUrl ?? "—"}</dd></div>
                <div><dt>{t("fields.birthDate")}</dt><dd>{candidateCase.candidateBirthDate ?? "—"}</dd></div>
                {candidateCase.acceptedMemberId != null && <div>
                    <dt>{t("admittedMember")}</dt>
                    <dd>
                        <Link href={`/members/${candidateCase.acceptedMemberId}`}
                              aria-label={t("openMemberRecord")}>
                            {candidateCase.acceptedMemberNumber ?? candidateCase.acceptedMemberId}
                        </Link>
                    </dd>
                </div>}
                {candidateCase.acceptedOn && <div>
                    <dt>{t("admittedOn")}</dt>
                    <dd><FormattedCaseDate value={candidateCase.acceptedOn}/></dd>
                </div>}
                <div><dt>{t("openedAt")}</dt><dd><FormattedCaseDate value={candidateCase.createdAt}/></dd></div>
                <div><dt>{t("updatedAt")}</dt><dd><FormattedCaseDate value={candidateCase.updatedAt}/></dd></div>
            </DescriptionList>
        </Card>

        {postDeclaration && candidateCase.declarationReceivedOn && <Card title={t("clocksCard")}
                                                                          hint={t("clocksCardHint")}>
            <CaseClocks candidateCase={candidateCase}/>
        </Card>}

        <Card title={t("transitionsCard")} hint={t("transitionsHint")}>
            {transitionsResult.ok && bodiesResult.ok && districtBoard
                ? <CaseTransitions caseId={caseId} transitions={transitionsResult.data.transitions}
                                   canManage={canManage && open}
                                   canApprove={canApprove}
                                   postDeclaration={postDeclaration}
                                   districtBoard={districtBoard}
                                   nationalBoard={nationalBoard}/>
                : <ApiErrorNotice error={transitionsResult.ok
                    ? (bodiesResult.ok
                        ? {status: 500, code: "INVALID_RESPONSE", message: ""}
                        : bodiesResult.error)
                    : transitionsResult.error}/>}            {canManage && open && <div className={styles.actions}>
                <ActionButton action={sendCaseReminderAction.bind(null, caseId)}
                              label={t("sendReminder")}
                              variant="outline"
                              ariaLabel={t("sendReminderAria")}/>
                {candidateCase.status === "AWAITING_DECLARATION" && canManage
                    && <DeclarationLinkButton caseId={caseId}/>}
            </div>}
        </Card>

        {postDeclaration && candidateCase.status !== "REJECTED" && candidateCase.status !== "ACCEPTED"
            && <Card title={t("decisionsCard")} hint={t("decisionsCardHint")}>
                <CaseDecisions caseId={caseId}
                               decisions={candidateCase.decisions}
                               status={candidateCase.status}
                               canManage={canManage && open}
                               canApprove={canApprove}/>
            </Card>}

        {canManage && open && <Card title={t("editCard")} hint={t("editHint")}>
            <ActionForm action={updateRecruitmentCaseAction.bind(null, caseId)}
                        submitLabel={t("save")}
                        successMessage={t("saved")}>
                <TextField name="candidateFirstName"
                           label={t("fields.firstName")}
                           required
                           defaultValue={candidateCase.candidateFirstName}/>
                <TextField name="candidateLastName"
                           label={t("fields.lastName")}
                           required
                           defaultValue={candidateCase.candidateLastName ?? ""}/>
                <TextField name="candidateEmail"
                           type="email"
                           label={t("fields.email")}
                           defaultValue={candidateCase.candidateEmail ?? ""}/>
                <TextField name="candidatePhone"
                           type="tel"
                           label={t("fields.phone")}
                           defaultValue={candidateCase.candidatePhone ?? ""}/>
                <TextField name="candidateSocialProfileUrl"
                           type="url"
                           label={t("fields.profile")}
                           defaultValue={candidateCase.candidateSocialProfileUrl ?? ""}/>
                <TextField name="candidateBirthDate"
                           type="date"
                           label={t("fields.birthDate")}
                           hint={t("fields.birthDateHint")}
                           defaultValue={candidateCase.candidateBirthDate ?? ""}/>
            </ActionForm>
        </Card>}

        {canManage && <Card title={t("noteCard")} hint={t("noteHint")}>
            <ActionForm action={addCaseNoteAction.bind(null, caseId)}
                        submitLabel={t("addNote")}
                        successMessage={t("noteAdded")}>
                <TextAreaField name="note" label={t("noteLabel")} required maxLength={2000}/>
            </ActionForm>
        </Card>}

        <Card title={t("timelineCard")} hint={t("timelineHint")}>
            <CaseTimeline events={candidateCase.events}/>
            <Notice tone="info">
                <p>{t("clocksHint")}</p>
            </Notice>
        </Card>
    </Page>;
}
