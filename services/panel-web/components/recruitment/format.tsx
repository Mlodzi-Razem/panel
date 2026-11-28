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
import { useFormatter } from "next-intl";
import { Tone } from "@/components/page/Page";

/** Name fields of a case summary or detail; the display fields are absent on responses without them. */
export type CandidateNameFields = {
    candidateFirstName?: string | null;
    candidateLastName?: string | null;
    candidateDisplayFirstName?: string | null;
    candidateDisplayLastName?: string | null;
    candidateNameSource?: string | null;
};

const nonEmpty = (v: string | null | undefined): v is string => typeof v === "string" && v.trim() !== "";

const normalizeName = (v: string) => v.normalize("NFC").trim().replace(/\s+/g, " ").toLowerCase();

/**
 * The name the Panel uses (declared preferred, else the case name, else formal). Falls back to the intake
 * fields when the response carries no display source. M2 requires first and last together.
 */
export function candidateName(item: CandidateNameFields): string {
    const parts = item.candidateNameSource == null
        ? [item.candidateFirstName, item.candidateLastName]
        : [item.candidateDisplayFirstName, item.candidateDisplayLastName];

    return parts.filter(nonEmpty).join(" ");
}

/**
 * The intake name, shown as a muted second line in the list when the displayed name is a declared preferred
 * name that differs from it (PN-3). Null in every other case.
 */
export function intakeNameIfDiffers(item: CandidateNameFields): string | null {
    if (item.candidateNameSource !== "DECLARATION_PREFERRED") {
        return null;
    }

    const intake = [item.candidateFirstName, item.candidateLastName].filter(nonEmpty).join(" ");

    if (intake === "") {
        return null;
    }

    return normalizeName(intake) === normalizeName(candidateName(item)) ? null : intake;
}

export function FormattedCaseDate({value}: { value: string | null | undefined }) {
    const format = useFormatter();
    if (!value) {
        return null;
    }
    const date = new Date(value.length === 10 ? `${value}T00:00:00Z` : value);
    if (Number.isNaN(date.getTime())) {
        return <>{value}</>;
    }
    return <time dateTime={value}>
        {format.dateTime(date, value.length === 10
            ? {dateStyle: "medium", timeZone: "UTC"}
            : {dateStyle: "medium", timeStyle: "short"})}
    </time>;
}

/**
 * Colour = phase (STATES 1), the exact status stays in the badge text. Four semantic groups
 * instead of eleven shades: I Kontakt=info, II Okres wstępny=warning, III Decyzyjne=brand,
 * IV Terminalne: ACCEPTED=success, REJECTED=danger.
 */
export function caseStatusTone(status: string): Tone {
    switch (status) {
        case "NEW":
        case "IN_PROGRESS":
        case "NO_CONTACT":
        case "AWAITING_DECLARATION":
            return "info";
        case "DECLARATION_RECEIVED":
        case "PROBATION":
        case "PROBATION_EXTENDED":
            return "warning";
        case "ZO_RECOMMENDED":
        case "ZK_VOTING":
            return "brand";
        case "ACCEPTED":
            return "success";
        case "REJECTED":
            return "danger";
        default:
            return "neutral";
    }
}

/** Whole days since the case was opened — a proxy for the statutory clocks the API does not expose yet. */
export function caseAgeDays(createdAt: string): number {
    const opened = new Date(createdAt).getTime();
    if (Number.isNaN(opened)) {
        return 0;
    }
    return Math.max(0, Math.floor((Date.now() - opened) / 86_400_000));
}
