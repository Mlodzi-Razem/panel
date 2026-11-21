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

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { signIn } from "next-auth/react";
import { useTranslations } from "next-intl";
import { authentication } from "@microsoft/teams-js";
import { TEAMS_SSO_PROVIDER } from "@/auth/providers";
import { initializeTeams } from "@/components/teams/teamsJs";
import styles from "@/components/teams/Teams.module.scss";

type State = 'working' | 'outside' | 'failed';

/**
 * Signs the user in with the Teams SSO token, then reloads the server components.
 * Outside Teams, or when Teams cannot provide a token, it offers the browser version instead.
 */
export default function TeamsSignIn() {
    const t = useTranslations('teams');
    const router = useRouter();
    const [state, setState] = useState<State>('working');

    useEffect(() => {
        let active = true;

        (async () => {
            if (!(await initializeTeams())) {
                if (active) {
                    setState('outside');
                }
                return;
            }
            try {
                const token = await authentication.getAuthToken();
                const result = await signIn(TEAMS_SSO_PROVIDER, {token, redirect: false});
                if (!active) {
                    return;
                }
                if (result?.ok && !result.error) {
                    router.refresh();
                } else {
                    setState('failed');
                }
            } catch {
                if (active) {
                    setState('failed');
                }
            }
        })();

        return () => {
            active = false;
        };
    }, [router]);

    if (state === 'working') {
        return <section className={styles.signIn} aria-busy="true">
            <p>{t('signingIn')}</p>
        </section>;
    }

    return <section className={styles.signIn}>
        <h1>{state === 'outside' ? t('outsideTitle') : t('failedTitle')}</h1>
        <p>{state === 'outside' ? t('outsideBody') : t('failedBody')}</p>
        <a href="/" target="_blank" rel="noopener noreferrer" className={styles.button}>{t('openInBrowser')}</a>
    </section>;
}
