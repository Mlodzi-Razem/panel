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

import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { getPanelServerSession } from "@/auth/authOptions";
import PanelAppRoot from "@/app/PanelAppRoot";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { Card, Page } from "@/components/page/Page";
import { NavGroup } from "@/components/shell/nav";
import { getMe, hasPermission } from "@/panel-api/members/me";
import { listPendingChangeRequests } from "@/panel-api/members/changeRequests";
import { PERMISSIONS } from "@/panel-api/members/types";
import React from "react";

export default async function AuthorizedLayout({children}: React.PropsWithChildren<object>) {
    const session = await getPanelServerSession();

    if (!session || !session.user) {
        redirect('/login');
    }

    const me = await getMe();
    if (me.kind === 'unauthenticated') {
        redirect('/login');
    }

    const user = {
        name: session.user.name ?? session.user.email ?? '',
        email: session.user.email,
        image: session.user.image,
    };

    if (me.kind === 'linked') {
        const canDecide = hasPermission(me.me, PERMISSIONS.formalManage);
        const canReadFormal = hasPermission(me.me, PERMISSIONS.formalRead);
        const canReadRecruitment = hasPermission(me.me, PERMISSIONS.recruitmentRead);
        const pending = canDecide ? await listPendingChangeRequests() : null;

        const groups: NavGroup[] = [
            {
                labelKey: 'groupMine',
                items: [
                    {href: '/', labelKey: 'dashboard', icon: 'dashboard'},
                    {href: '/user', labelKey: 'user', icon: 'profile'},
                ],
            },
            {
                labelKey: 'groupOrganisation',
                items: [
                    ...(me.me.canSeeMembers ? [{href: '/members', labelKey: 'members', icon: 'members' as const}] : []),
                    ...(canDecide ? [{
                        href: '/members/change-requests',
                        labelKey: 'changeRequests',
                        icon: 'requests' as const,
                        badge: pending?.ok ? pending.data.length : undefined,
                    }] : []),
                    ...(canReadFormal ? [{href: '/members/ending', labelKey: 'ending', icon: 'ending' as const}] : []),
                    {href: '/bodies', labelKey: 'bodies', icon: 'bodies'},
                    ...(canReadRecruitment ? [{
                        href: '/rekrutacja', labelKey: 'recruitment', icon: 'recruitment' as const,
                    }] : []),
                ],
            },
            {
                labelKey: 'groupFinance',
                items: [{href: '/fees', labelKey: 'fees', icon: 'fees'}],
            },
        ];

        return <PanelAppRoot user={user} groups={groups}>
            {children}
        </PanelAppRoot>;
    }

    const t = await getTranslations('notLinked');
    return <PanelAppRoot user={user} groups={[]}>
        <Page title={me.kind === 'unlinked' ? t('title') : t('errorTitle')}>
            {me.kind === 'unlinked'
                ? <Card><p>{t('body')}</p></Card>
                : <ApiErrorNotice error={me.error}/>}
        </Page>
    </PanelAppRoot>;
}
