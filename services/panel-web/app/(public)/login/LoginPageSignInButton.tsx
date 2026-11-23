/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
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

"use client";

import { signIn } from "next-auth/react";
import { useTranslations } from "next-intl";
import Button from "@/components/button/Button";
import { ENTRA_PROVIDER } from "@/auth/providers";

function MicrosoftLogoIcon() {
    return <svg viewBox="0 0 21 21" style={{height: '100%', width: 'auto'}} aria-hidden="true">
        <rect x="1" y="1" width="9" height="9" fill="#f25022"/>
        <rect x="11" y="1" width="9" height="9" fill="#7fba00"/>
        <rect x="1" y="11" width="9" height="9" fill="#00a4ef"/>
        <rect x="11" y="11" width="9" height="9" fill="#ffb900"/>
    </svg>;
}

export default function LoginPageSignInButton() {
    const t = useTranslations('login');

    return <Button variant="primary"
                   style={{width: '100%'}}
                   iconLeft={<MicrosoftLogoIcon/>}
                   onClick={() => signIn(ENTRA_PROVIDER)}>
        {t('loginWithMicrosoft')}
    </Button>;
}
