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

import type {Metadata} from "next";
import {getTranslations} from "next-intl/server";
import {getDeclarationForm} from "@/panel-api/recruitment/declarationForm";
import DeclarationShell from "./DeclarationShell";
import DeclarationWizard from "./DeclarationWizard";
import {FinalScreen, LinkUnavailableScreen} from "./DeclarationScreens";

/**
 * Public, anonymous route of the e-declaration flow (ladder R0—candidate never signs in; the
 * magic-link token in the path is the whole credential). It sits in the (public) route group,
 * so no auth gating applies anywhere in the app, and it is marked noindex because the page is
 * personal. The token is validated server-side on every visit; the token value is never logged.
 *
 * Errors are rendered as distinct screens: 404 → link unavailable, TOKEN_EXPIRED → expired,
 * 409 → case closed; a signed case shows the final screen with no personal data.
 */
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
    const t = await getTranslations("recruitmentDeclaration");
    return {
        title: t("metaTitle"),
        robots: {index: false, follow: false},
    };
}

export default async function DeclarationFormPage({params}: {params: Promise<{token: string}>}) {
    const {token} = await params;
    const result = await getDeclarationForm(token);

    if (result.kind !== "form") {
        // All dead-link states (404 / TOKEN_EXPIRED / 409 / transport) collapse to ONE screen here —
        // the candidate must not learn from the UI whether the case ever existed (review F1/F8/H4).
        return <DeclarationShell>
            <LinkUnavailableScreen/>
        </DeclarationShell>;
    }

    const {status, expiresAt} = result.form;
    if (status === "SIGNED") {
        return <DeclarationShell>
            <FinalScreen/>
        </DeclarationShell>;
    }

    return <DeclarationShell>
        <DeclarationWizard token={token}
                           expiresAt={expiresAt ?? ""}
                           initialStep={status === "FILLED" ? "upload" : "landing"}/>
    </DeclarationShell>;
}
