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

import MemberTypeName from "@/components/bodies/MemberTypeName";
import React from "react";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { useTranslations } from "next-intl";
import {
    BuildingsIcon,
    CalendarXIcon,
    CheckCircleIcon,
    ClipboardTextIcon,
    IdentificationCardIcon,
    UserCircleIcon,
    UsersThreeIcon,
} from "@phosphor-icons/react/ssr";
import styles from "@/app/(authorized)/Dashboard.module.scss";
import { FormattedDate, memberName } from "@/components/members/format";
import { Badge, Card, Muted, Page } from "@/components/page/Page";
import { EmptyState, NameCell, StatCard, Stats } from "@/components/ui/Blocks";
import { listPendingChangeRequests } from "@/panel-api/members/changeRequests";
import { hasPermission } from "@/panel-api/members/me";
import { listMembers, membersEndingThisYear } from "@/panel-api/members/members";
import { requireMe } from "@/panel-api/members/requireMe";
import { BodyFunction, ChangeRequest, PERMISSIONS } from "@/panel-api/members/types";

const PENDING_PREVIEW = 5;

function today(): string {
    return new Date().toISOString().slice(0, 10);
}

function currentFunctions(functions: BodyFunction[]): BodyFunction[] {
    const now = today();
    return functions.filter(f => !f.validTo || f.validTo >= now);
}

function MyFunctions({functions}: { functions: BodyFunction[] }) {
    const t = useTranslations('dashboard');
    return <Card id="dashboard-functions" title={t('functionsTitle')}>
        {functions.length === 0
            ? <EmptyState icon={<BuildingsIcon aria-hidden/>} title={t('functionsEmpty')}/>
            : <ul className={styles.list}>
                {functions.map(f => <li key={f.membershipId}>
                    <Link href={`/bodies/${f.bodyId}`} className={styles.listLink}>
                        <span className={styles.listMain}>
                            <strong>{f.bodyName}</strong>
                            <small><MemberTypeName code={f.memberTypeCode} name={f.memberTypeName}/></small>
                        </span>
                        <Badge tone="brand"><MemberTypeName code={f.memberTypeCode} name={f.memberTypeName}/></Badge>
                    </Link>
                </li>)}
            </ul>}
    </Card>;
}

function Todo({ownPending, pending, canDecide}: {
    ownPending: boolean;
    pending: ChangeRequest[];
    canDecide: boolean;
}) {
    const t = useTranslations('dashboard');
    const tf = useTranslations('fields');
    const preview = pending.slice(0, PENDING_PREVIEW);
    const nothing = !ownPending && preview.length === 0;
    return <Card id="dashboard-todo" title={t('todoTitle')}>
        {nothing && <EmptyState icon={<CheckCircleIcon aria-hidden/>} title={t('todoEmpty')}/>}
        {ownPending && <Link href="/user" className={styles.todoOwn}>
            <ClipboardTextIcon aria-hidden/>
            <span>
                <strong>{t('ownPending')}</strong>
                <small>{t('ownPendingHint')}</small>
            </span>
        </Link>}
        {canDecide && preview.length > 0 && <>
            <h3 className={styles.subheading}>{t('toDecide')}</h3>
            <ul className={styles.list}>
                {preview.map(request => <li key={request.id} className={styles.requestRow}>
                    <NameCell name={request.member.displayName}
                              href={`/members/change-requests/${request.id}`}
                              secondary={request.fields.map(f => (tf.has(f) ? tf(f) : f)).join(', ')}/>
                    <Muted><FormattedDate value={request.createdAt}/></Muted>
                </li>)}
            </ul>
            <Link href="/members/change-requests" className={styles.more}>
                {t('viewAll', {count: pending.length})}
            </Link>
        </>}
    </Card>;
}

function QuickLinks({canSeeMembers}: { canSeeMembers: boolean }) {
    const t = useTranslations('dashboard');
    return <Card id="dashboard-links" title={t('quickLinks')}>
        <ul className={styles.quick}>
            <li><Link href="/user"><UserCircleIcon aria-hidden/>{t('linkProfile')}</Link></li>
            {canSeeMembers && <li><Link href="/members"><UsersThreeIcon aria-hidden/>{t('linkMembers')}</Link></li>}
            <li><Link href="/bodies"><BuildingsIcon aria-hidden/>{t('linkBodies')}</Link></li>
        </ul>
    </Card>;
}

export default async function Dashboard() {
    const me = await requireMe();
    if (!me) {
        return null;
    }

    const t = await getTranslations('dashboard');
    const canDecide = hasPermission(me, PERMISSIONS.formalManage);
    const canReadFormal = hasPermission(me, PERMISSIONS.formalRead);

    const [pendingResult, endingResult, membersResult] = await Promise.all([
        canDecide ? listPendingChangeRequests() : Promise.resolve(null),
        canReadFormal ? membersEndingThisYear() : Promise.resolve(null),
        me.canSeeMembers ? listMembers({page: 0, size: 1}) : Promise.resolve(null),
    ]);

    const pending = pendingResult?.ok ? pendingResult.data : [];
    const functions = currentFunctions(me.member.bodyFunctions);
    const firstName = String(me.member.fields['preferredNameFirst'] ?? '').trim() || memberName(me.member.fields);
    const showPending = pendingResult?.ok;
    const showEnding = endingResult?.ok;
    const showMembers = membersResult?.ok;

    return <Page title={t('title')}
                 subtitle={firstName ? t('greeting', {name: firstName}) : t('subtitle')}>
        <Stats>
            {showPending && <StatCard label={t('statPending')}
                                      value={pending.length}
                                      icon={<ClipboardTextIcon aria-hidden/>}
                                      href="/members/change-requests"
                                      tone="warning"/>}
            {showEnding && <StatCard label={t('statEnding')}
                                     value={endingResult.data.length}
                                     icon={<CalendarXIcon aria-hidden/>}
                                     href="/members/ending"
                                     tone="info"/>}
            {showMembers && <StatCard label={t('statMembers')}
                                      value={membersResult.data.totalItems}
                                      icon={<UsersThreeIcon aria-hidden/>}
                                      href="/members"
                                      tone="brand"/>}
            <StatCard label={t('statFunctions')}
                      value={functions.length}
                      icon={<IdentificationCardIcon aria-hidden/>}
                      href="#dashboard-functions"
                      tone="success"/>
        </Stats>
        <div className={styles.columns}>
            <MyFunctions functions={functions}/>
            <div className={styles.side}>
                <Todo ownPending={me.pendingChangeRequest} pending={pending} canDecide={canDecide}/>
                <QuickLinks canSeeMembers={me.canSeeMembers}/>
            </div>
        </div>
    </Page>;
}
