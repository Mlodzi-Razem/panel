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
import { Badge, DescriptionList, Muted } from "@/components/page/Page";
import { CaseDetail } from "@/panel-api/members/recruitment";
import { FormattedCaseDate } from "@/components/recruitment/format";

/** The four clock states of STATES 3.2: waiting for the window, on time, approaching, overdue (soft). */
type ClockState = "WAITING" | "ON_TIME" | "SOON" | "OVERDUE";

/**
 * Statutory clocks rendered per row with the notBefore/notLater dates visible. Every deadline
 * is a soft one: the badge says "po terminie (nieblokujący)" when crossed and no action is ever
 * blocked by the UI. The clock badge is a separate element from the status badge on purpose —
 * PROBATION (warning) with a clock ON TIME (success) is a normal, frequent pairing.
 */
export default function CaseClocks({ candidateCase }: { candidateCase: CaseDetail }) {
    const t = useTranslations("recruitment");
    const today = new Date();
    const todayOn = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;

    const daysLeft = (notLater: string | null | undefined): number | null => {
        if (!notLater) {
            return null;
        }
        const deadline = Date.parse(notLater);
        if (Number.isNaN(deadline)) {
            return null;
        }
        return Math.ceil((deadline - Date.parse(todayOn)) / 86_400_000);
    };

    const stateBadge = (notBefore: string | null | undefined, notLater: string | null | undefined,
                        daysToDeadline: number | null): {state: ClockState, tone: "info" | "success" | "warning" | "danger"} => {
        // Z-1 waits for its window to open (not_before); the soft deadlines otherwise start ticking.
        if (notBefore && todayOn < notBefore) {
            return {state: "WAITING", tone: "info"};
        }
        if (daysToDeadline === null || daysToDeadline < 0) {
            return {state: "OVERDUE", tone: "danger"};
        }
        if (daysToDeadline <= 7) {
            return {state: "SOON", tone: "warning"};
        }
        return {state: "ON_TIME", tone: "success"};
    };

    const badgeLabel = (state: ClockState, daysToDeadline: number | null): string => {
        switch (state) {
            case "WAITING": return t("clocks.stateWaiting");
            case "SOON": return t("clocks.stateSoon", {count: daysToDeadline ?? 0});
            case "OVERDUE": return t("clocks.stateOverdue");
            default: return t("clocks.stateOnTime");
        }
    };

    const clocks: { key: string, label: string, notBefore: string | null | undefined, notLater: string | null | undefined }[] = [];
    if (candidateCase.declarationReceivedOn) {
        clocks.push({
            key: "assessment",
            label: t("clocks.assessmentWindow"),
            notBefore: candidateCase.assessmentNotBeforeOn,
            notLater: candidateCase.assessmentNotLaterOn,
        });
        if (candidateCase.extensionCount > 0 && candidateCase.secondAssessmentNotLaterOn) {
            clocks.push({
                key: "secondAssessment",
                label: t("clocks.secondAssessment"),
                notBefore: null,
                notLater: candidateCase.secondAssessmentNotLaterOn,
            });
        }
    }
    if (candidateCase.zoRecommendedOn) {
        clocks.push({
            key: "resolution",
            label: t("clocks.admissionResolution"),
            notBefore: null,
            notLater: candidateCase.admissionResolutionNotLaterOn,
        });
    }

    if (clocks.length === 0) {
        return null;
    }

    return <DescriptionList>
        {clocks.map(clock => {
            const days = daysLeft(clock.notLater);
            const badge = stateBadge(clock.notBefore, clock.notLater, days);
            return <div key={clock.key}>
                <dt>{clock.label}</dt>
                <dd>
                    {clock.key === "assessment" && clock.notBefore
                        ? <Muted>{t("clocks.notBefore")} <FormattedCaseDate value={clock.notBefore}/></Muted>
                        : null}
                    {clock.key === "assessment" && clock.notBefore && <Muted>{" · "}</Muted>}
                    {clock.notLater
                        ? <>{t("clocks.notLater")} <FormattedCaseDate value={clock.notLater}/>
                            {" "}
                            <Badge tone={badge.tone}>{badgeLabel(badge.state, days)}</Badge>
                        </>
                        : <Muted>—</Muted>}
                    {clock.key === "assessment" && candidateCase.extensionCount > 0 && <>
                        {" · "}
                        <Muted>{t("clocks.extensionUsed")}</Muted>
                    </>}
                </dd>
            </div>;
        })}
    </DescriptionList>;
}
