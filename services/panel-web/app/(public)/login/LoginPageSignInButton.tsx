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
import Image from "next/image";
import googleLogo from '@/assets/google-logo.svg';
import Button from "@/components/button/Button";

function GoogleLogoIcon() {
    return <Image src={googleLogo}
                  unoptimized
                  style={{
                      height: '100%',
                      width: 'auto',
                  }}
                  alt="G"/>;
}

export default function LoginPageSignInButton() {
    const t = useTranslations('login');

    return <Button variant="primary"
                   style={{width: '100%'}}
                   iconLeft={<GoogleLogoIcon/>}
                   onClick={() => signIn('google')}>
        {t('loginWithGoogle')}
    </Button>;
}