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

import Image from "next/image";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { isAuthenticated, isEntraSignInEnabled } from "@/auth/authOptions";
import LoginPageSignInButton from "@/app/(public)/login/LoginPageSignInButton";
import mrLogo from "@/assets/mr-logo.svg";

import styles from './LoginPage.module.scss';

export default async function Login() {
    if (await isAuthenticated()) {
        redirect('/');
    }

    const t = await getTranslations('login');
    const microsoft = isEntraSignInEnabled();

    return <main className={styles.page}>
        <section className={styles.brand}>
            <Image src={mrLogo} alt="Młodzi Razem" unoptimized priority className={styles.logo}/>
            <p className={styles.pitch}>{t('pitch')}</p>
        </section>
        <section className={styles.formSide}>
            <div className={styles.card}>
                <div className={styles.cardText}>
                    <h1>{t('title')}</h1>
                    <p>{t('subtitle')}</p>
                </div>
                {microsoft
                    ? <LoginPageSignInButton/>
                    : <p className={styles.note} role="status">{t('notConfigured')}</p>}
                <p className={styles.note}>{t('organisationOnly')}</p>
            </div>
        </section>
    </main>;
}
