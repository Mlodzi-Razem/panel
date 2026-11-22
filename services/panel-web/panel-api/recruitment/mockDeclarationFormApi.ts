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

/**
 * In-panel mock of the public declaration-form contract (M4a backend, see the page README).
 * Enabled with PANEL_RECRUITMENT_DECLARATION_MOCK=1 — never in production.
 *
 * The mock is a deterministic state machine keyed by the whole token: it keeps a process-lifetime
 * Map<String, Draft>, never logs token values, and mirrors the contract's error semantics:
 *
 *   GET  CREATED → FILLED (after a valid submit) → SIGNED (after an accepted upload)
 *   token containing "expired"   → TOKEN_EXPIRED (422 contract shape)
 *   token containing "closed"    → case closed (409, no body required)
 *   token containing "invalid"   → link unavailable (404)
 *   token containing "filled"    → a FILLED draft, so step 3 (PDF + upload) opens directly
 *   any other token              → a fresh CREATED draft with the seeded greeting "Anna K."
 *
 * A signed-PDF upload whose file name contains "invalid" yields SIGNATURE_INVALID, so both
 * upload outcomes can be exercised in the UI. All drafts are syntetic blank forms; no PII.
 */
import {isValidPesel, peselBirthDate} from "@/util/pesel";
import type {SubmissionValues} from "@/panel-api/recruitment/declarationForm";

const MOCK_GREETING = "Anna K.";
const MOCK_LINK_TTL_MINUTES = 48 * 60;

type MockDraft = {
    status: "CREATED" | "FILLED" | "SIGNED";
    greeting: string;
    expiresAt: string;
};

const drafts = new Map<string, MockDraft>();

function draft(token: string): MockDraft | undefined {
    if (token.includes("invalid")) {
        return undefined;
    }
    let current = drafts.get(token);
    if (!current) {
        current = {
            status: token.includes("filled") ? "FILLED" : "CREATED",
            greeting: MOCK_GREETING,
            expiresAt: new Date(Date.now() + MOCK_LINK_TTL_MINUTES * 60_000).toISOString(),
        };
        drafts.set(token, current);
    }
    return current;
}

export type MockCaseOutcome =
    | {kind: "ok"}
    | {kind: "unavailable"}
    | {kind: "expired"}
    | {kind: "closed"};

/** Pre-flight token check shared by every endpoint: unavailable (404), expired (422) or closed (409). */
export function mockCaseFor(token: string): MockCaseOutcome {
    if (token.includes("invalid")) {
        return {kind: "unavailable"};
    }
    if (token.includes("expired")) {
        return {kind: "expired"};
    }
    if (token.includes("closed")) {
        return {kind: "closed"};
    }
    return {kind: "ok"};
}

export function mockGetForm(token: string): MockDraft | undefined {
    return draft(token);
}

export function mockSubmitForm(token: string, values: SubmissionValues): MockDraft | {"error": string} {
    const current = draft(token);
    if (!current) {
        return {"error": "unavailable"};
    }
    if (current.status !== "CREATED") {
        return {"error": "already-filled"};
    }
    // The same FE checks the real backend will own (M4a): required fields, PESEL checksum,
    // required confirmations. Age-derived checks (ADMISSION_AGE) belong to the backend.
    if (!values.formalFirstName.trim() || !values.formalLastName.trim()) {
        return {"error": "FIELD_REQUIRED:formalFirstName"};
    }
    if (!isValidPesel(values.personalId) || !peselBirthDate(values.personalId)) {
        return {"error": "PERSONAL_ID_INVALID:personalId"};
    }
    if (!values.specialCategoryConsent) {
        return {"error": "CONSENT_REQUIRED:specialCategoryConsent"};
    }
    current.status = "FILLED";
    return current;
}

export function mockUploadDeclaration(token: string, fileName: string): MockDraft | {"error": string} {
    const current = draft(token);
    if (!current) {
        return {"error": "unavailable"};
    }
    if (current.status === "SIGNED") {
        return current;
    }
    if (current.status !== "FILLED") {
        return {"error": "not-filled"};
    }
    if (fileName.toLowerCase().includes("invalid")) {
        return {"error": "SIGNATURE_INVALID"};
    }
    current.status = "SIGNED";
    return current;
}

export function mockDeclarationPdf(token: string): Uint8Array | null {
    const current = draft(token);
    if (!current) {
        return null;
    }
    return minimalPdf([
        "DEKLARACJA CZLONKOWSKA — MOCK",
        "Podglad generowany lokalnie przez panel-web.",
        "Prawdziwy plik zwroci backend panel-api (M4a).",
    ]);
}

/** A minimal but structurally valid single-page PDF, assembled with correct xref offsets. */
function minimalPdf(lines: readonly string[]): Uint8Array {
    const escape = (text: string) => text.replace(/([()\\])/g, "\\$1");
    const contentLines = lines.map((text, index) => `BT /F1 ${index === 0 ? 16 : 12} Tf 72 ${740 - index * 24} Td (${escape(text)}) Tj ET`);
    const streamText = contentLines.join("\n");

    const objects = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        `<< /Length ${streamText.length} >>\nstream\n${streamText}\nendstream`,
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ];

    let pdf = "%PDF-1.4\n";
    const offsets: number[] = [];
    objects.forEach((body, index) => {
        offsets.push(pdf.length);
        pdf += `${index + 1} 0 obj\n${body}\nendobj\n`;
    });
    const xrefStart = pdf.length;
    pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
    offsets.forEach(offset => {
        pdf += `${String(offset).padStart(10, "0")} 00000 n \n`;
    });
    pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF`;

    return new TextEncoder().encode(pdf);
}
