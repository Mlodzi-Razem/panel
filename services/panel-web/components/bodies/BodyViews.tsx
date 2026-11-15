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
import Link from "next/link";
import { useTranslations } from "next-intl";
import { FormattedDate } from "@/components/members/format";
import { Badge, LinkButton, Muted } from "@/components/page/Page";
import { NameCell } from "@/components/ui/Blocks";
import styles from "./Bodies.module.scss";
import { BodyDetail, CompositionEntry } from "@/panel-api/members/types";

export function MemberLink({entry, linkMembers}: { entry: CompositionEntry; linkMembers: boolean }) {
    return linkMembers ? <Link href={`/members/${entry.memberId}`}>{entry.memberName}</Link> : <>{entry.memberName}</>;
}

export function CompositionSummary({entries, linkMembers}: { entries: CompositionEntry[]; linkMembers: boolean }) {
    const t = useTranslations('bodies');
    if (entries.length === 0) {
        return <p className={styles.noMembers}>{t('noComposition')}</p>;
    }
    return <ul className={styles.composition}>
        {entries.map(entry => <li key={entry.membershipId}>
            <NameCell name={entry.memberName}
                      secondary={entry.memberTypeName}
                      href={linkMembers ? `/members/${entry.memberId}` : undefined}/>
            {entry.validTo && <Muted>{t('until')} <FormattedDate value={entry.validTo}/></Muted>}
        </li>)}
    </ul>;
}

export function KindBadge({kind}: { kind: string | null | undefined }) {
    const t = useTranslations('enums');
    if (!kind) {
        return null;
    }
    return <Badge tone="brand">{t.has(`bodyKind.${kind}`) ? t(`bodyKind.${kind}`) : kind}</Badge>;
}

export function BodyCard({detail, canManage, linkMembers}: {
    detail: BodyDetail;
    canManage: boolean;
    linkMembers: boolean;
}) {
    const t = useTranslations('bodies');
    const {body} = detail;
    const headingId = `body-${body.id}-title`;
    return <article className={styles.bodyCard} id={`body-${body.id}`} aria-labelledby={headingId}>
        <header className={styles.bodyHead}>
            <div className={styles.bodyTitle}>
                <h2 id={headingId}>{body.name}</h2>
                <KindBadge kind={body.kind}/>
            </div>
            {body.email && <a className={styles.bodyEmail} href={`mailto:${body.email}`}>{body.email}</a>}
        </header>
        <CompositionSummary entries={detail.composition} linkMembers={linkMembers}/>
        <footer className={styles.bodyFoot}>
            <LinkButton href={`/bodies/${body.id}`} primary={canManage}>{canManage ? t('manage') : t('details')}</LinkButton>
        </footer>
    </article>;
}
