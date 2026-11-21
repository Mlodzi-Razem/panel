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

import { getTranslations } from "next-intl/server";
import AffiliationsCard from "@/components/members/AffiliationsCard";
import ApiErrorNotice from "@/components/members/ApiErrorNotice";
import { FormattedDate } from "@/components/members/format";
import { Card, Muted } from "@/components/page/Page";
import { ExternalLink, Hero, TeamsPage } from "@/components/teams/TeamsBlocks";
import styles from "@/components/teams/Teams.module.scss";
import { listPendingChangeRequests } from "@/panel-api/members/changeRequests";
import { hasPermission } from "@/panel-api/members/me";
import { requireMe } from "@/panel-api/members/requireMe";
import { PERMISSIONS } from "@/panel-api/members/types";

export default async function TeamsDistrict() {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('teams');
    const affiliations = me.member.affiliations ?? [];
    const primary = affiliations.find(a => a.kind === 'PRIMARY');
    const canDecide = hasPermission(me, PERMISSIONS.formalManage);
    const pending = canDecide ? await listPendingChangeRequests() : null;

    return <TeamsPage>
        <Hero title={t('districtTitle')} subtitle={primary?.districtName ?? t('noDistrict')}/>

        {affiliations.length > 0
            && <AffiliationsCard memberId={me.member.id} affiliations={affiliations} readOnly/>}

        {pending && <Card title={t('pendingTitle')} hint={t('pendingHint')}>
            {!pending.ok && <ApiErrorNotice error={pending.error}/>}
            {pending.ok && pending.data.length === 0 && <p>{t('pendingNone')}</p>}
            {pending.ok && pending.data.length > 0 && <ul className={styles.links}>
                {pending.data.map(request => <li key={request.id}>
                    <ExternalLink href={`/members/change-requests/${request.id}`}>
                        {request.member.displayName}
                    </ExternalLink>{' '}
                    <Muted><FormattedDate value={request.createdAt}/></Muted>
                </li>)}
            </ul>}
        </Card>}
    </TeamsPage>;
}
