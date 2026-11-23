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
import { useTranslations } from "next-intl";
import { Badge } from "@/components/page/Page";
import styles from "@/components/recruitment/recruitment.module.scss";
import { FormattedCaseDate } from "@/components/recruitment/format";
import { CaseEvent } from "@/panel-api/members/recruitment";

/**
 * The timeline as the API serves it, oldest first. Event payloads carry field names, not values
 * (the module's GDPR discipline); the note text is the one value the intake records on purpose.
 */
function payloadSummary(event: CaseEvent): string | null {
    if (!event.payload || typeof event.payload !== "object") {
        return null;
    }
    const payload = event.payload as Record<string, unknown>;
    if (typeof payload["note"] === "string" && payload["note"].length > 0) {
        return payload["note"];
    }
    if (Array.isArray(payload["fields"])) {
        return payload["fields"].map(f => String(f)).join(", ");
    }
    return null;
}

/** A data correction: which fields changed (names, never values) and the reason the operator gave. */
function CorrectionSummary({payload}: { payload: unknown }) {
    const t = useTranslations("recruitment");
    const data = (payload && typeof payload === "object" ? payload : {}) as Record<string, unknown>;
    const fields = Array.isArray(data["fields"]) ? data["fields"].map(f => String(f)) : [];
    const reason = typeof data["reason"] === "string" ? data["reason"] : null;

    return <>
        {fields.length > 0 && <p className={styles.timelinePayload}>
            {t("timelineCorrectionFields")}: {fields.map(f => t.has(`dataFields.${f}`) ? t(`dataFields.${f}`) : f).join(", ")}
        </p>}
        {reason && <p className={styles.timelinePayload}>{t("timelineCorrectionReason")}: {reason}</p>}
    </>;
}

export default function CaseTimeline({events}: { events: CaseEvent[] }) {
    const t = useTranslations("recruitment");
    const tEnums = useTranslations("enums");

    if (events.length === 0) {
        return <p className={styles.mutedText}>{t("timelineEmpty")}</p>;
    }

    return <ol className={styles.timeline}>
        {events.map(event => <li key={event.id} className={styles.timelineItem}>
            <div className={styles.timelineHead}>
                {/* Collegial acts carry the brand colour; closing is neutral unless it was a
                    rejection (the case detail badge shows that); danger stays reserved for refusals. */}
                <Badge tone={event.eventType === "DECISION_RECORDED"
                    ? "brand"
                    : event.eventType === "CASE_CREATED" ? "brand" : "neutral"}>
                    {tEnums(`recruitmentEvent.${event.eventType}`)}
                </Badge>
                <time className={styles.mutedText}>
                    <FormattedCaseDate value={event.createdAt}/>
                </time>
                {event.actor && <span className={styles.mutedText}>{event.actor.displayName}</span>}
            </div>
            {event.eventType === "CANDIDATE_DATA_CORRECTED"
                ? <CorrectionSummary payload={event.payload}/>
                : payloadSummary(event) && <p className={styles.timelinePayload}>{payloadSummary(event)}</p>}
        </li>)}
    </ol>;
}
