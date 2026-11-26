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

/**
 * The register keeps one (English) name per function; the UI translates by the function's stable code and falls
 * back to the API's name for a code it does not know.
 */
export function useMemberTypeName() {
    const t = useTranslations('enums');
    return (code: string | null | undefined, name: string) =>
        code && t.has(`memberType.${code}`) ? t(`memberType.${code}`) : name;
}

export default function MemberTypeName({code, name}: { code: string | null | undefined; name: string }) {
    const translate = useMemberTypeName();
    return <>{translate(code, name)}</>;
}
