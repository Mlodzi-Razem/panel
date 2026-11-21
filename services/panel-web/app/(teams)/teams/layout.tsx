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

import React, { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import SuspenseFallback from "@/app/SuspenseFallback";
import { getPanelServerSession } from "@/auth/authOptions";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { Card } from "@/components/page/Page";
import TeamsHost from "@/components/teams/TeamsHost";
import TeamsSignIn from "@/components/teams/TeamsSignIn";
import { TeamsPage } from "@/components/teams/TeamsBlocks";
import { getMe } from "@/panel-api/members/me";

/** The personal tabs of the Panel MR app in Teams, Outlook and the Microsoft 365 app. */
export default async function TeamsLayout({children}: React.PropsWithChildren<object>) {
    const session = await getPanelServerSession();
    const me = session?.user ? await getMe() : null;

    let content: React.ReactNode;
    if (!me || me.kind === 'unauthenticated') {
        content = <TeamsSignIn/>;
    } else if (me.kind === 'unlinked') {
        const t = await getTranslations('notLinked');
        const tt = await getTranslations('teams');
        content = <TeamsPage>
            <Card title={t('title')}>
                <p>{session?.user?.idp === 'entra' ? tt('notLinkedBody') : t('body')}</p>
            </Card>
        </TeamsPage>;
    } else if (me.kind === 'error') {
        content = <TeamsPage><ApiErrorNotice error={me.error}/></TeamsPage>;
    } else {
        content = <Suspense fallback={<SuspenseFallback/>}>{children}</Suspense>;
    }

    return <>
        <TeamsHost/>
        {content}
    </>;
}
