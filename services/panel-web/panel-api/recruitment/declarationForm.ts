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
 * Client for the PUBLIC recruitment declaration-form contract (M4a). Called by the anonymous
 * candidate route /rekrutacja/deklaracja/[token] and its BFF route handlers — on purpose this
 * module never mints a service token (the contract endpoints are public) and never logs the
 * token: the token is a bearer secret, so it is URL-path-scoped, kept out of console output
 * and out of OpenTelemetry spans (the fetches below run under context.suppressTracing).
 *
 * When PANEL_RECRUITMENT_DECLARATION_MOCK=1 the same function surface is answered by the
 * in-panel mock (mockDeclarationFormApi) — see the README next to the page.
 */
import {context} from "@opentelemetry/api";
import {suppressTracing} from "@opentelemetry/core";
import * as z from "zod";
import env from "@/config/env";
import {recruitmentDeclarationMockEnabled} from "@/config/env";
import {ApiError, ApiResult} from "@/panel-api/members/types";
import {
    mockCaseFor,
    mockDeclarationPdf,
    mockGetForm,
    mockSubmitForm,
    mockUploadDeclaration,
} from "@/panel-api/recruitment/mockDeclarationFormApi";

export const DECLARATION_FORM_PDF_PATH = "/public/declaration-form" as const;

export const declarationFormStatusSchema = z.enum(["CREATED", "FILLED", "SIGNED"]);
export type DeclarationFormStatus = z.infer<typeof declarationFormStatusSchema>;

const declarationFormSchema = z.object({
    status: declarationFormStatusSchema,
    // The contract record marks these nullable (a SIGNED session carries nulls) — the
    // CREATED/FILLED landings always fill them, the page treats SIGNED separately.
    expiresAt: z.string().nullish(),
    greeting: z.string().nullish(),
});
export type DeclarationForm = z.infer<typeof declarationFormSchema>;

export type GetDeclarationFormResult =
    | {kind: "form"; form: DeclarationForm}
    | {kind: "unavailable"}
    | {kind: "expired"}
    | {kind: "closed"}
    | {kind: "error"; message: string};

/**
 * The submit wire mirrors the panel-api `DeclarationFormRequest` record field-for-field:
 * canonical keys `personalId`, `imageConsent`, `specialCategoryConsent`, `infoDutyAcknowledged`, optional `email`.
 * The birth-date confirmation is a DeclarationFormStep-local UX gate (FE asks only, never sends).
 */
export const submissionValuesSchema = z.object({
    formalFirstName: z.string().trim().min(1),
    formalLastName: z.string().trim().min(1),
    preferredFirstName: z.string().trim().optional(),
    preferredLastName: z.string().trim().optional(),
    personalId: z.string().trim().regex(/^\d{11}$/, "PESEL must be 11 digits"),
    pronouns: z.string().trim().optional(),
    phone: z.string().trim().optional(),
    email: z.string().trim().optional(),
    imageConsent: z.boolean(),
    specialCategoryConsent: z.boolean(),
    infoDutyAcknowledged: z.boolean(),
});
export type SubmissionValues = z.infer<typeof submissionValuesSchema>;

/**
 * The M4a success envelope is the contract's `DeclarationForm` record
 * (`{status, expiresAt?, greeting?, pdfUrl?}`). `pdfUrl` is informational only —
 * the browser always views the PDF through our own BFF proxy route.
 */
export const signedDeclarationResponseSchema = z.object({
    status: z.literal("SIGNED"),
    expiresAt: z.string().nullish(),
    greeting: z.string().nullish(),
    pdfUrl: z.string().nullish(),
});

export type UploadDeclarationResult =
    | {kind: "signed"}
    | {kind: "invalidSignature"}
    | {kind: "unavailable"}
    | {kind: "expired"}
    | {kind: "closed"}
    | {kind: "error"; message: string};

const errorBodySchema = z.object({
    code: z.string(),
    field: z.string().nullish(),
    message: z.string().nullish(),
});

function path(token: string, suffix?: string): string {
    const encoded = encodeURIComponent(token);
    return suffix
        ? `${DECLARATION_FORM_PDF_PATH}/${encoded}/${suffix}`
        : `${DECLARATION_FORM_PDF_PATH}/${encoded}`;
}

function url(endpointUrl: string): string {
    const baseUrl = env.PANEL_API_URL;
    if (!baseUrl.endsWith("/") && !endpointUrl.startsWith("/")) {
        return baseUrl + "/" + endpointUrl;
    }
    return baseUrl + endpointUrl;
}

/**
 * Fetch wrapper for the public endpoints: no service token (they are public), no caching, and
 * the whole call runs with tracing suppressed — the magic-link token sits in the URL path, so
 * spans and their http.url attributes must never carry it (the global fetch instrumentation is
 * on — instrumentation.ts). @opentelemetry/core@2.2 is already in the tree via @vercel/otel; it
 * is declared explicitly in package.json because we import suppressTracing from it.
 */
async function publicFetch(endpoint: string, init?: RequestInit): Promise<Response> {
    return context.with(suppressTracing(context.active()), () => fetch(url(endpoint), {
        ...init,
        cache: "no-store",
    }));
}

async function readErrorBody(response: Response): Promise<z.infer<typeof errorBodySchema> | null> {
    try {
        return errorBodySchema.nullish().parse(await response.json()) ?? null;
    } catch {
        return null;
    }
}

/** Maps a non-OK response to the wizard's case states: 404 → unavailable, TOKEN_EXPIRED → expired, 409 → closed. */
async function classifyCaseFailure(response: Response): Promise<"unavailable" | "expired" | "closed" | "error"> {
    if (response.status === 404) {
        return "unavailable";
    }
    if (response.status === 409) {
        return "closed";
    }
    const body = await readErrorBody(response);
    if (body?.code === "TOKEN_EXPIRED") {
        return "expired";
    }
    if (body?.code === "CASE_CLOSED") {
        return "closed";
    }
    return "error";
}

function toApiError(response: Response, body: z.infer<typeof errorBodySchema> | null): ApiError {
    return {
        status: response.status,
        code: body?.code ?? `HTTP_${response.status}`,
        message: body?.message ?? (response.statusText || "Request failed"),
        ...(body?.field ? {fields: [body.field]} : {}),
    };
}

export async function getDeclarationForm(token: string): Promise<GetDeclarationFormResult> {
    if (recruitmentDeclarationMockEnabled()) {
        const outcome = mockCaseFor(token);
        if (outcome.kind !== "ok") {
            return outcome.kind === "unavailable" ? {kind: "unavailable"}
                : outcome.kind === "expired" ? {kind: "expired"} : {kind: "closed"};
        }
        const draft = mockGetForm(token);
        return draft ? {kind: "form", form: draft} : {kind: "unavailable"};
    }

    let response: Response;
    try {
        response = await publicFetch(path(token), {headers: {Accept: "application/json"}});
    } catch (error) {
        return {kind: "error", message: error instanceof Error ? error.message : "Panel API unreachable"};
    }
    if (!response.ok) {
        const failure = await classifyCaseFailure(response);
        return failure === "error"
            ? {kind: "error", message: `Panel API responded ${response.status}`}
            : failure === "unavailable" ? {kind: "unavailable"}
                : failure === "expired" ? {kind: "expired"} : {kind: "closed"};
    }
    const parsed = declarationFormSchema.safeParse(await response.json());
    return parsed.success
        ? {kind: "form", form: parsed.data}
        : {kind: "error", message: "Panel API returned a malformed declaration form"};
}

export async function submitDeclarationForm(token: string, values: SubmissionValues): Promise<ApiResult<"FILLED">> {
    if (recruitmentDeclarationMockEnabled()) {
        const outcome = mockSubmitForm(token, values);
        if ("error" in outcome) {
            const [code, field] = outcome.error.split(":");
            const caseFailure = code === "unavailable" ? "unavailable" : null;
            if (caseFailure) {
                return {ok: false, error: {status: 404, code: "NOT_FOUND", message: "Link unavailable"}};
            }
            return {ok: false, error: {status: 422, code, message: code, ...(field ? {fields: [field]} : {})}};
        }
        return {ok: true, data: "FILLED"};
    }

    let response: Response;
    try {
        response = await publicFetch(path(token), {
            method: "POST",
            headers: {Accept: "application/json", "Content-Type": "application/json"},
            body: JSON.stringify(values),
        });
    } catch (error) {
        return {ok: false, error: {status: 0, code: "API_UNREACHABLE", message: error instanceof Error ? error.message : "Panel API unreachable"}};
    }
    if (response.status === 200) {
        // The contract returns the canonical `DeclarationForm` record: status FILLED + pdfUrl
        // (the browser always views the PDF through our own BFF proxy, so pdfUrl is optional here).
        const parsed = z.object({status: z.literal("FILLED"), pdfUrl: z.string().nullish()})
            .safeParse(await response.json());
        return parsed.success
            ? {ok: true, data: "FILLED"}
            : {ok: false, error: {status: 200, code: "API_MALFORMED", message: "Panel API returned a malformed response"}};
    }
    return {ok: false, error: toApiError(response, await readErrorBody(response))};
}

export async function streamDeclarationFormPdf(token: string): Promise<Response> {
    if (recruitmentDeclarationMockEnabled()) {
        const outcome = mockCaseFor(token);
        if (outcome.kind === "ok") {
            const pdf = mockDeclarationPdf(token);
            if (pdf) {
                return new Response(pdf as unknown as BodyInit, {status: 200});
            }
        }
        return new Response(null, {status: 404});
    }
    return publicFetch(path(token, "pdf"), {headers: {Accept: "application/pdf"}});
}

export async function uploadSignedDeclaration(token: string, file: File): Promise<UploadDeclarationResult> {
    if (recruitmentDeclarationMockEnabled()) {
        const outcome = mockUploadDeclaration(token, file.name);
        if ("error" in outcome) {
            if (outcome.error === "SIGNATURE_INVALID") {
                return {kind: "invalidSignature"};
            }
            return {kind: "unavailable"};
        }
        return {kind: "signed"};
    }

    const formData = new FormData();
    formData.append("file", file);

    let response: Response;
    try {
        response = await publicFetch(path(token, "declaration"), {method: "POST", body: formData});
    } catch (error) {
        return {kind: "error", message: error instanceof Error ? error.message : "Panel API unreachable"};
    }
    if (response.ok) {
        const parsed = signedDeclarationResponseSchema.safeParse(await response.json());
        return parsed.success
            ? {kind: "signed"}
            : {kind: "error", message: "Panel API returned a malformed response"};
    }
    // The response body streams once: read it a single time, then classify inline — the
    // 422 SIGNATURE_INVALID verdict of the PAdES validator is a retryable upload problem,
    // not a transport error (review D), so it must keep its own UI path.
    const body = await readErrorBody(response);
    if (body?.code === "SIGNATURE_INVALID") {
        return {kind: "invalidSignature"};
    }
    if (response.status === 404) {
        return {kind: "unavailable"};
    }
    if (response.status === 409 || body?.code === "CASE_CLOSED") {
        return {kind: "closed"};
    }
    if (body?.code === "TOKEN_EXPIRED") {
        return {kind: "expired"};
    }
    return {kind: "error", message: `Panel API responded ${response.status}`};
}
