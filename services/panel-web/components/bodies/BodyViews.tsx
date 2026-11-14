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
import { Badge, Card, LinkButton } from "@/components/page/Page";
import { BodyDetail, CompositionEntry } from "@/panel-api/members/types";

export function MemberLink({entry, linkMembers}: { entry: CompositionEntry; linkMembers: boolean }) {
    return linkMembers ? <Link href={`/members/${entry.memberId}`}>{entry.memberName}</Link> : <>{entry.memberName}</>;
}

export function CompositionSummary({entries, linkMembers}: { entries: CompositionEntry[]; linkMembers: boolean }) {
    const t = useTranslations('bodies');
    if (entries.length === 0) {
        return <p>{t('noComposition')}</p>;
    }
    const byRole = new Map<string, CompositionEntry[]>();
    for (const entry of entries) {
        byRole.set(entry.memberTypeName, [...(byRole.get(entry.memberTypeName) ?? []), entry]);
    }
    return <ul>
        {[...byRole.entries()].map(([role, list]) => <li key={role}>
            <strong>{role}:</strong>{' '}
            {list.map((entry, index) => <React.Fragment key={entry.membershipId}>
                {index > 0 && ', '}
                <MemberLink entry={entry} linkMembers={linkMembers}/>
                {entry.validTo && <> (<FormattedDate value={entry.validTo}/>)</>}
            </React.Fragment>)}
        </li>)}
    </ul>;
}

export function KindBadge({kind}: { kind: string | null | undefined }) {
    const t = useTranslations('enums');
    if (!kind) {
        return null;
    }
    return <Badge>{t.has(`bodyKind.${kind}`) ? t(`bodyKind.${kind}`) : kind}</Badge>;
}

export function BodyCard({detail, canManage, linkMembers}: {
    detail: BodyDetail;
    canManage: boolean;
    linkMembers: boolean;
}) {
    const t = useTranslations('bodies');
    const {body} = detail;
    return <Card id={`body-${body.id}`}
                 title={body.name}
                 actions={<>
                     <KindBadge kind={body.kind}/>
                     <LinkButton href={`/bodies/${body.id}`}>{canManage ? t('manage') : t('details')}</LinkButton>
                 </>}>
        {body.email && <p>{body.email}</p>}
        <CompositionSummary entries={detail.composition} linkMembers={linkMembers}/>
    </Card>;
}
